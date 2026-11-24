package com.medidiary.app.ai

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File

enum class ModelKind(val fileName: String, val label: String, val minBytes: Long) {
    WHISPER("medidiary-whisper.bin", "Whisper (ggml .bin)", 10L * 1024 * 1024),
    GEMMA("medidiary-gemma.gguf", "Gemma 3 1B (GGUF)", 50L * 1024 * 1024),
}

data class ModelStatus(val whisper: File?, val gemma: File?) {
    val ready get() = whisper != null && gemma != null
}

/**
 * Model files are NOT in git or the APK by default. They are found in, in order:
 *  1. internal storage: filesDir/models/<fileName>  (after "Import model" in Settings)
 *  2. app-specific external storage: /sdcard/Android/data/com.medidiary.app/files/models/<fileName>
 *     (fastest for the team: `adb push <file> /sdcard/Android/data/com.medidiary.app/files/models/`)
 *  3. APK assets: assets/models/<fileName> (optional, copied to filesDir once — makes the APK ~1 GB)
 * No network access is ever used.
 */
class ModelManager(private val context: Context) {
    private val internalDir = File(context.filesDir, "models").apply { mkdirs() }
    private val externalDir: File? = context.getExternalFilesDir("models")?.apply { mkdirs() }

    private val _status = MutableStateFlow(scan())
    val status: StateFlow<ModelStatus> = _status.asStateFlow()

    fun refresh(): ModelStatus = scan().also { _status.value = it }

    fun externalDirPath(): String = externalDir?.absolutePath
        ?: "/sdcard/Android/data/${context.packageName}/files/models"

    private fun scan() = ModelStatus(locate(ModelKind.WHISPER), locate(ModelKind.GEMMA))

    private fun locate(kind: ModelKind): File? = listOfNotNull(
        File(internalDir, kind.fileName),
        externalDir?.let { File(it, kind.fileName) },
    ).firstOrNull { it.isFile && it.length() >= kind.minBytes }

    /** Copy a model chosen with the system file picker (works offline, e.g. from Downloads/USB). */
    suspend fun importFromUri(kind: ModelKind, uri: Uri, onProgress: (Long) -> Unit = {}): File =
        withContext(Dispatchers.IO) {
            val target = File(internalDir, kind.fileName)
            val tmp = File(internalDir, kind.fileName + ".part")
            context.contentResolver.openInputStream(uri).use { input ->
                requireNotNull(input) { "Cannot open selected file" }
                tmp.outputStream().use { out ->
                    val buf = ByteArray(1 shl 20)
                    var total = 0L
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        total += n
                        onProgress(total)
                    }
                }
            }
            validateHeader(kind, tmp)
            if (target.exists()) target.delete()
            check(tmp.renameTo(target)) { "Could not move model into place" }
            refresh()
            target
        }

    /** Copies assets/models/<fileName> to internal storage if a teammate bundled it in the APK. */
    suspend fun extractBundledAssets() = withContext(Dispatchers.IO) {
        for (kind in ModelKind.entries) {
            val target = File(internalDir, kind.fileName)
            if (target.exists()) continue
            val assetPath = "models/${kind.fileName}"
            val exists = runCatching { context.assets.open(assetPath).close() }.isSuccess
            if (!exists) continue
            val tmp = File(internalDir, kind.fileName + ".part")
            context.assets.open(assetPath).use { i -> tmp.outputStream().use { o -> i.copyTo(o, 1 shl 20) } }
            tmp.renameTo(target)
        }
        refresh()
    }

    fun delete(kind: ModelKind) {
        File(internalDir, kind.fileName).delete()
        externalDir?.let { File(it, kind.fileName).delete() }
        refresh()
    }

    /** Cheap sanity check so a wrong file is rejected before the native loader crashes on it. */
    private fun validateHeader(kind: ModelKind, file: File) {
        val magic = ByteArray(4)
        file.inputStream().use { it.read(magic) }
        val ok = when (kind) {
            ModelKind.GEMMA -> String(magic, Charsets.US_ASCII) == "GGUF"
            // whisper.cpp ggml files start with 0x67676d6c ("ggml" little-endian => "lmgg").
            ModelKind.WHISPER -> String(magic, Charsets.US_ASCII) in setOf("lmgg", "ggml", "GGUF")
        }
        if (!ok) {
            file.delete()
            error(
                when (kind) {
                    ModelKind.GEMMA -> "Not a GGUF file. Convert/quantise with llama.cpp (convert_hf_to_gguf.py + llama-quantize Q4_K_M)."
                    ModelKind.WHISPER -> "Not a whisper.cpp ggml model. Convert your fine-tune with whisper.cpp/models/convert-h5-to-ggml.py."
                },
            )
        }
    }
}
