package com.voicechoicer.app.media

import android.content.Context
import android.util.Log
import com.voicechoicer.core.audio.Wav
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.ffmpegkit.whisper.Whisper
import dev.ffmpegkit.whisper.WhisperConfig
import dev.ffmpegkit.whisper.WhisperModel
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Offline speech-to-text for the silence-detection import path (no
 * subtitle file supplied), using a bundled multilingual Whisper "base"
 * model (see the `downloadWhisperModel` Gradle task) via the
 * whisper-android wrapper around whisper.cpp. Notably more accurate than
 * small Kaldi-based models (e.g. Vosk's "small" checkpoints) at a
 * comparable size, at the cost of a larger APK and slower per-segment
 * transcription.
 *
 * Loading the model is expensive (tens of MB to read + native init), so
 * it happens once and is cached for the process lifetime; if it ever
 * fails, transcription is unavailable and callers fall back to empty text
 * for manual entry - check [lastError] (or logcat tag "WhisperTranscriber").
 */
@Singleton
class WhisperTranscriber @Inject constructor(@ApplicationContext private val context: Context) {

    private var model: WhisperModel? = null
    private var loadFailed = false

    var lastError: String? = null
        private set

    val isAvailable: Boolean get() = model != null

    suspend fun ensureLoaded(): Boolean {
        if (model != null) return true
        if (loadFailed) return false
        return try {
            val modelFile = ensureModelFileCopied()
            model = Whisper.loadModel(context, modelFile.absolutePath)
            Log.i(TAG, "Whisper model loaded from ${modelFile.absolutePath}")
            true
        } catch (e: Throwable) {
            loadFailed = true
            lastError = "Nie udało się wczytać modelu rozpoznawania mowy: ${e.message}"
            Log.e(TAG, "Failed to load Whisper model", e)
            false
        }
    }

    /** The model ships as a compressed asset; whisper.cpp needs a real file to mmap, so copy it once. */
    private fun ensureModelFileCopied(): File {
        val destFile = File(File(context.filesDir, "whisper-model"), MODEL_FILE_NAME)
        if (!destFile.exists() || destFile.length() == 0L) {
            destFile.parentFile?.mkdirs()
            context.assets.open("$MODEL_ASSET_DIR/$MODEL_FILE_NAME").use { input ->
                destFile.outputStream().use { output -> input.copyTo(output) }
            }
        }
        return destFile
    }

    /**
     * Transcribes mono PCM16 audio at its native [sampleRate] (the library resamples internally).
     * Returns "" if unavailable, too short to reliably decode, or if transcription throws (logged,
     * not propagated - a bad segment shouldn't fail the whole import).
     */
    suspend fun transcribe(pcm: ShortArray, sampleRate: Int): String {
        val currentModel = model ?: return ""
        if (pcm.size < sampleRate / 4) return "" // <250ms: too little audio for a reliable decode

        val tempFile = File(context.cacheDir, "whisper_segment_${System.nanoTime()}.wav")
        return try {
            tempFile.writeBytes(Wav.encode(pcm, sampleRate))
            val result = Whisper.transcribe(currentModel, tempFile.absolutePath, WhisperConfig(language = "pl"))
            Log.i(TAG, "Whisper result: ${result.text}")
            result.text.trim()
        } catch (e: Exception) {
            Log.e(TAG, "Transcription failed for a segment, leaving its text empty", e)
            ""
        } finally {
            tempFile.delete()
        }
    }

    companion object {
        private const val MODEL_ASSET_DIR = "whisper-model"
        private const val MODEL_FILE_NAME = "ggml-base.bin"
        private const val TAG = "WhisperTranscriber"
    }
}
