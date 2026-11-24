// JNI bridge for whisper.cpp.
//
// Memory policy (CLAUDE.md §4.1): every transcribe() call loads the model, runs inference and frees
// the context before returning, so Whisper is never resident at the same time as Gemma.

#include <jni.h>

#include <atomic>
#include <string>
#include <vector>

#include "whisper.h"

#ifdef __ANDROID__
#include <android/log.h>
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, "MediDiaryWhisper", __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, "MediDiaryWhisper", __VA_ARGS__)
#else
#include <cstdio>
#define LOGI(...) (fprintf(stderr, __VA_ARGS__), fputc('\n', stderr))
#define LOGE(...) (fprintf(stderr, __VA_ARGS__), fputc('\n', stderr))
#endif

static std::atomic<bool> g_abort{false};

static bool abort_cb(void * /*user_data*/) {
    return g_abort.load();
}

static void throw_java(JNIEnv * env, const char * msg) {
    jclass cls = env->FindClass("java/lang/IllegalStateException");
    if (cls != nullptr) env->ThrowNew(cls, msg);
}

static std::string jstring_to_std(JNIEnv * env, jstring s) {
    if (s == nullptr) return {};
    const char * chars = env->GetStringUTFChars(s, nullptr);
    std::string out(chars ? chars : "");
    if (chars) env->ReleaseStringUTFChars(s, chars);
    return out;
}

// Java's NewStringUTF expects *modified* UTF-8 and aborts on 4-byte sequences; whisper can emit
// those (emoji, rare scripts) and can also split a multi-byte character across segments.
// Build the string from a byte[] via `new String(bytes, UTF_8)` which is lenient.
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

extern "C" {

JNIEXPORT jstring JNICALL
Java_com_medidiary_whisper_WhisperNative_transcribe(
        JNIEnv * env, jobject /*thiz*/,
        jstring j_model_path, jfloatArray j_samples, jstring j_language,
        jint n_threads, jstring j_initial_prompt) {
    g_abort.store(false);

    const std::string model_path = jstring_to_std(env, j_model_path);
    const std::string language = jstring_to_std(env, j_language);
    const std::string initial_prompt = jstring_to_std(env, j_initial_prompt);

    whisper_context_params cparams = whisper_context_default_params();
    cparams.use_gpu = false;

    whisper_context * ctx = whisper_init_from_file_with_params(model_path.c_str(), cparams);
    if (ctx == nullptr) {
        throw_java(env, ("Failed to load Whisper model: " + model_path).c_str());
        return nullptr;
    }

    const jsize n_samples = env->GetArrayLength(j_samples);
    std::vector<float> samples(static_cast<size_t>(n_samples));
    env->GetFloatArrayRegion(j_samples, 0, n_samples, samples.data());

    whisper_full_params params = whisper_full_default_params(WHISPER_SAMPLING_GREEDY);
    params.n_threads = n_threads > 0 ? n_threads : 4;
    params.translate = false;
    params.no_context = true;
    params.no_timestamps = true;
    params.single_segment = false;
    params.print_special = false;
    params.print_progress = false;
    params.print_realtime = false;
    params.print_timestamps = false;
    params.suppress_blank = true;
    params.temperature = 0.0f;
    params.detect_language = false;
    // "auto" lets Whisper pick per recording (useful for Bangla/English code-switching).
    params.language = language.empty() ? "auto" : language.c_str();
    params.initial_prompt = initial_prompt.empty() ? nullptr : initial_prompt.c_str();
    params.abort_callback = abort_cb;
    params.abort_callback_user_data = nullptr;

    LOGI("whisper_full: %d samples, lang=%s, threads=%d", (int) n_samples, params.language, params.n_threads);
    const int rc = whisper_full(ctx, params, samples.data(), static_cast<int>(samples.size()));

    std::string text;
    if (rc == 0) {
        const int n_segments = whisper_full_n_segments(ctx);
        for (int i = 0; i < n_segments; ++i) {
            const char * seg = whisper_full_get_segment_text(ctx, i);
            if (seg != nullptr) text += seg;
        }
    }

    whisper_free(ctx);  // free immediately: sequential memory policy

    if (rc != 0) {
        throw_java(env, g_abort.load() ? "Transcription cancelled" : "Whisper inference failed");
        return nullptr;
    }
    return utf8_to_jstring(env, text);
}

JNIEXPORT void JNICALL
Java_com_medidiary_whisper_WhisperNative_cancel(JNIEnv * /*env*/, jobject /*thiz*/) {
    g_abort.store(true);
}

JNIEXPORT jstring JNICALL
Java_com_medidiary_whisper_WhisperNative_systemInfo(JNIEnv * env, jobject /*thiz*/) {
    return env->NewStringUTF(whisper_print_system_info());
}

}  // extern "C"
