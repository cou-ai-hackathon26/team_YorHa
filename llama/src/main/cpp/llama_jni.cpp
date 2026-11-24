// JNI bridge for llama.cpp running the fine-tuned Gemma 3 1B GGUF.
//
// Memory policy (CLAUDE.md §4.1): complete() loads the model, runs greedy (temp 0.0) decoding and
// frees model + context before returning, so Gemma is never resident at the same time as Whisper.

#include <jni.h>

#include <algorithm>
#include <atomic>
#include <string>
#include <vector>

#include "llama.h"

// llama_batch_get_one / llama_decode are marked deprecated in favour of the newer batch_ext API
// but are still fully supported at the pinned commit. Silence the noise.
#pragma clang diagnostic ignored "-Wdeprecated-declarations"
#pragma GCC diagnostic ignored "-Wdeprecated-declarations"

#ifdef __ANDROID__
#include <android/log.h>
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, "MediDiaryLlama", __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, "MediDiaryLlama", __VA_ARGS__)
#else
#include <cstdio>
#define LOGI(...) (fprintf(stderr, __VA_ARGS__), fputc('\n', stderr))
#define LOGE(...) (fprintf(stderr, __VA_ARGS__), fputc('\n', stderr))
#endif

static std::atomic<bool> g_abort{false};
static std::atomic<bool> g_backend_ready{false};

static void throw_java(JNIEnv * env, const char * msg) {
    jclass cls = env->FindClass("java/lang/IllegalStateException");
    if (cls != nullptr) env->ThrowNew(cls, msg);
}

static std::string jstring_to_std(JNIEnv * env, jstring s) {
    if (s == nullptr) return {};
    // GetStringUTFChars returns modified UTF-8; Bangla is BMP-only so this is byte-identical to UTF-8.
    const char * chars = env->GetStringUTFChars(s, nullptr);
    std::string out(chars ? chars : "");
    if (chars) env->ReleaseStringUTFChars(s, chars);
    return out;
}

static jstring utf8_to_jstring(JNIEnv * env, const std::string & s) {
    jbyteArray bytes = env->NewByteArray(static_cast<jsize>(s.size()));
    env->SetByteArrayRegion(bytes, 0, static_cast<jsize>(s.size()), reinterpret_cast<const jbyte *>(s.data()));
    jclass str_cls = env->FindClass("java/lang/String");
    jmethodID ctor = env->GetMethodID(str_cls, "<init>", "([BLjava/lang/String;)V");
    jstring charset = env->NewStringUTF("UTF-8");
    auto result = static_cast<jstring>(env->NewObject(str_cls, ctor, bytes, charset));
    env->DeleteLocalRef(bytes);
    env->DeleteLocalRef(charset);
    env->DeleteLocalRef(str_cls);
    return result;
}

static std::string build_prompt(const llama_model * model, const std::string & system_prompt,
                                const std::string & user_text, bool use_chat_template) {
    if (!use_chat_template) {
        // Raw mode: caller passes the full prompt exactly as used during fine-tuning.
        return system_prompt.empty() ? user_text : system_prompt + "\n\n" + user_text;
    }
    std::vector<llama_chat_message> msgs;
    if (!system_prompt.empty()) msgs.push_back({"system", system_prompt.c_str()});
    msgs.push_back({"user", user_text.c_str()});

    // nullptr template name = the template embedded in the GGUF (Gemma 3: <start_of_turn>...).
    const char * tmpl = llama_model_chat_template(model, nullptr);
    std::vector<char> buf(2 * (system_prompt.size() + user_text.size()) + 512);
    int32_t n = llama_chat_apply_template(tmpl, msgs.data(), msgs.size(), true, buf.data(), (int32_t) buf.size());
    if (n > (int32_t) buf.size()) {
        buf.resize(n + 1);
        n = llama_chat_apply_template(tmpl, msgs.data(), msgs.size(), true, buf.data(), (int32_t) buf.size());
    }
    if (n < 0) {
        // Unknown template: fall back to Gemma's documented turn format (system merged into user turn).
        LOGE("chat template not recognised, using Gemma fallback format");
        std::string p = "<start_of_turn>user\n";
        if (!system_prompt.empty()) p += system_prompt + "\n\n";
        p += user_text + "<end_of_turn>\n<start_of_turn>model\n";
        return p;
    }
    return std::string(buf.data(), n);
}

// Tracks JSON nesting so generation can stop as soon as the top-level object closes.
struct JsonEndDetector {
    int depth = 0;
    bool started = false;
    bool in_string = false;
    bool escaped = false;

    // Returns true when the first top-level JSON object/array has been fully emitted.
    bool feed(const std::string & piece) {
        for (char c : piece) {
            if (in_string) {
                if (escaped) escaped = false;
                else if (c == '\\') escaped = true;
                else if (c == '"') in_string = false;
                continue;
            }
            if (c == '"' && started) in_string = true;
            else if (c == '{' || c == '[') { depth++; started = true; }
            else if ((c == '}' || c == ']') && started) {
                depth--;
                if (depth <= 0) return true;
            }
        }
        return false;
    }
};

extern "C" {

JNIEXPORT jstring JNICALL
Java_com_medidiary_llama_LlamaNative_complete(
        JNIEnv * env, jobject /*thiz*/,
        jstring j_model_path, jstring j_system_prompt, jstring j_user_text,
        jint n_ctx, jint n_threads, jint max_tokens, jboolean use_chat_template,
        jboolean stop_at_json_end) {
    g_abort.store(false);
    if (!g_backend_ready.exchange(true)) llama_backend_init();

    const std::string model_path = jstring_to_std(env, j_model_path);
    const std::string system_prompt = jstring_to_std(env, j_system_prompt);
    const std::string user_text = jstring_to_std(env, j_user_text);

    llama_model_params mparams = llama_model_default_params();
    mparams.n_gpu_layers = 0;
    mparams.load_mode = LLAMA_LOAD_MODE_MMAP;  // weights paged from flash, not copied: lower peak RSS

    llama_model * model = llama_model_load_from_file(model_path.c_str(), mparams);
    if (model == nullptr) {
        throw_java(env, ("Failed to load GGUF model: " + model_path).c_str());
        return nullptr;
    }

    llama_context_params cparams = llama_context_default_params();
    cparams.n_ctx = n_ctx > 0 ? (uint32_t) n_ctx : 1024;
    cparams.n_batch = cparams.n_ctx;
    cparams.n_ubatch = 256;
    cparams.n_threads = n_threads > 0 ? n_threads : 4;
    cparams.n_threads_batch = cparams.n_threads;
    cparams.no_perf = true;

    llama_context * ctx = llama_init_from_model(model, cparams);
    if (ctx == nullptr) {
        llama_model_free(model);
        throw_java(env, "Failed to create llama context");
        return nullptr;
    }

    const llama_vocab * vocab = llama_model_get_vocab(model);
    const std::string prompt = build_prompt(model, system_prompt, user_text, use_chat_template == JNI_TRUE);

    // Tokenize (first call measures).
    int32_t n_prompt = -llama_tokenize(vocab, prompt.c_str(), (int32_t) prompt.size(), nullptr, 0, true, true);
    std::vector<llama_token> tokens(n_prompt > 0 ? n_prompt : 0);
    if (n_prompt <= 0 ||
        llama_tokenize(vocab, prompt.c_str(), (int32_t) prompt.size(), tokens.data(), (int32_t) tokens.size(), true, true) < 0) {
        llama_free(ctx);
        llama_model_free(model);
        throw_java(env, "Tokenization failed");
        return nullptr;
    }

    const int32_t ctx_size = (int32_t) llama_n_ctx(ctx);
    if (n_prompt + 8 >= ctx_size) {
        llama_free(ctx);
        llama_model_free(model);
        throw_java(env, ("Transcript too long for context window (" + std::to_string(n_prompt) +
                         " tokens, n_ctx=" + std::to_string(ctx_size) + ")").c_str());
        return nullptr;
    }
    const int32_t budget = std::min<int32_t>(max_tokens > 0 ? max_tokens : 384, ctx_size - n_prompt - 1);

    llama_sampler * smpl = llama_sampler_chain_init(llama_sampler_chain_default_params());
    llama_sampler_chain_add(smpl, llama_sampler_init_greedy());  // temp 0.0 => deterministic

    std::string output;
    std::string error;
    JsonEndDetector json_end;

    // Prompt evaluation in n_batch-sized chunks.
    const int32_t n_batch = (int32_t) llama_n_batch(ctx);
    for (int32_t i = 0; i < n_prompt && error.empty(); i += n_batch) {
        const int32_t n = std::min(n_batch, n_prompt - i);
        if (llama_decode(ctx, llama_batch_get_one(tokens.data() + i, n)) != 0) error = "Prompt decode failed";
        if (g_abort.load()) error = "Extraction cancelled";
    }

    char piece[256];
    for (int32_t generated = 0; error.empty() && generated < budget; ++generated) {
        if (g_abort.load()) { error = "Extraction cancelled"; break; }

        llama_token tok = llama_sampler_sample(smpl, ctx, -1);
        if (llama_vocab_is_eog(vocab, tok)) break;

        const int32_t len = llama_token_to_piece(vocab, tok, piece, sizeof(piece), 0, false);
        if (len > 0) {
            std::string p(piece, len);
            output += p;
            if (stop_at_json_end == JNI_TRUE && json_end.feed(p)) break;
        }

        if (llama_decode(ctx, llama_batch_get_one(&tok, 1)) != 0) { error = "Token decode failed"; break; }
    }

    llama_sampler_free(smpl);
    llama_free(ctx);          // free immediately: sequential memory policy
    llama_model_free(model);

    if (!error.empty()) {
        throw_java(env, error.c_str());
        return nullptr;
    }
    LOGI("prompt=%d tokens, output=%zu bytes", n_prompt, output.size());
    return utf8_to_jstring(env, output);
}

JNIEXPORT void JNICALL
Java_com_medidiary_llama_LlamaNative_cancel(JNIEnv * /*env*/, jobject /*thiz*/) {
    g_abort.store(true);
}

JNIEXPORT jstring JNICALL
Java_com_medidiary_llama_LlamaNative_systemInfo(JNIEnv * env, jobject /*thiz*/) {
    return env->NewStringUTF(llama_print_system_info());
}

}  // extern "C"
