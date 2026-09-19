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
 * Offline speech-to-text for the no-subtitle import path, using a bundled
 * multilingual Whisper "base" model (see the `downloadWhisperModel` Gradle
 * task) via the whisper-android wrapper around whisper.cpp. Notably more
 * accurate than small Kaldi-based models (e.g. Vosk's "small" checkpoints)
 * at a comparable size, at the cost of a larger APK and slower
 * transcription. Also does its own segmentation (see [transcribeWithSegments]) -
 * it is fed the whole track at once rather than pre-cut isolated clips.
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

    /** One utterance as Whisper itself split it: real word/sentence-aware boundaries, not a silence guess. */
    data class Segment(val startMs: Long, val endMs: Long, val text: String)

    /**
     * Transcribes the *whole* track at its native [sampleRate] (the library resamples internally)
     * in a single call, and returns Whisper's own segmentation of it.
     *
     * This is deliberately not "cut into isolated clips with our own VAD, then transcribe each
     * clip alone": Whisper's accuracy depends heavily on surrounding context, and feeding it tiny,
     * silence-bounded fragments one at a time (which can slice a word in half at the boundary)
     * measurably hurts quality. Letting Whisper see the continuous audio and choose its own
     * segment boundaries - it uses its language model, not just an energy threshold - gives both
     * better transcriptions and better-placed cut points.
     *
     * Returns an empty list if unavailable or on failure (logged, not propagated); the caller
     * falls back to silence-based segmentation with manual text entry in that case.
     */
    suspend fun transcribeWithSegments(pcm: ShortArray, sampleRate: Int): List<Segment> {
        val currentModel = model ?: return emptyList()
        if (pcm.isEmpty()) return emptyList()

        val tempFile = File(context.cacheDir, "whisper_track_${System.nanoTime()}.wav")
        return try {
            tempFile.writeBytes(Wav.encode(pcm, sampleRate))
            val result = Whisper.transcribe(currentModel, tempFile.absolutePath, WhisperConfig(language = "pl"))
            Log.i(TAG, "Whisper produced ${result.segments.size} segment(s)")
            result.segments
                .map { Segment(it.startMs, it.endMs, it.text.trim()) }
                .filter { it.text.isNotEmpty() && it.endMs > it.startMs }
        } catch (e: Exception) {
            Log.e(TAG, "Whole-track transcription failed, falling back to manual entry", e)
            emptyList()
        } finally {
            tempFile.delete()
        }
    }

    /**
     * Drops the loaded model so its native memory can be reclaimed before another
     * memory-heavy stage of the same import (speaker diarization, see [SherpaDiarizer])
     * runs - two large native models resident at once risks the OS killing the app on
     * lower-RAM phones. Transparently reloaded via [ensureLoaded] next time it's needed.
     */
    fun releaseForNow() {
        model = null
    }

    companion object {
        private const val MODEL_ASSET_DIR = "whisper-model"
        private const val MODEL_FILE_NAME = "ggml-base.bin"
        private const val TAG = "WhisperTranscriber"
    }
}
