package com.voicechoicer.app.media

import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine
import org.vosk.Model
import org.vosk.Recognizer
import org.vosk.android.StorageService

/**
 * Offline speech-to-text for the silence-detection import path (no
 * subtitle file supplied), using a small Polish Vosk model bundled as an
 * app asset (see the `downloadVoskModel` Gradle task). Loading the model
 * is somewhat expensive, so it happens once and is cached for the process
 * lifetime; if it ever fails (e.g. the asset wasn't bundled in this
 * build), transcription is unavailable and callers fall back to empty
 * text for manual entry - check [lastError] (or logcat tag "VoskTranscriber")
 * to see why.
 *
 * Known limitation: Vosk's native decoder can abort the whole process
 * (SIGABRT, not a catchable JVM exception) when asked for a final result
 * on a clip with no decodable speech at all - see
 * https://github.com/alphacep/vosk-android-demo/issues/160. We reduce the
 * odds of hitting this by skipping transcription for very short segments,
 * but cannot eliminate it entirely; it's an unresolved upstream bug.
 */
@Singleton
class VoskTranscriber @Inject constructor(@ApplicationContext private val context: Context) {

    private var model: Model? = null
    private var loadFailed = false

    var lastError: String? = null
        private set

    val isAvailable: Boolean get() = model != null

    suspend fun ensureLoaded(): Boolean {
        if (model != null) return true
        if (loadFailed) return false
        return try {
            model = unpackModel()
            Log.i(TAG, "Vosk model loaded successfully from assets/$MODEL_ASSET_DIR")
            true
        } catch (e: Throwable) {
            loadFailed = true
            lastError = "Nie udało się wczytać modelu rozpoznawania mowy: ${e.message}"
            Log.e(TAG, "Failed to load Vosk model from assets/$MODEL_ASSET_DIR", e)
            false
        }
    }

    private suspend fun unpackModel(): Model = suspendCancellableCoroutine { continuation ->
        StorageService.unpack(
            context,
            MODEL_ASSET_DIR,
            "vosk-model-pl",
            { unpackedModel -> continuation.resume(unpackedModel) },
            { exception: IOException -> continuation.resumeWithException(exception) },
        )
    }

    /**
     * Transcribes mono PCM16 audio already resampled to [SAMPLE_RATE] Hz.
     * Returns "" if unavailable, blank, too short to reliably decode, or
     * if the recognizer itself throws (logged, not propagated - a bad
     * segment shouldn't fail the whole import).
     */
    fun transcribe(pcm16kHzMono: ShortArray): String {
        val currentModel = model ?: return ""
        if (pcm16kHzMono.size < MIN_SAMPLES_TO_ATTEMPT) return ""

        return try {
            val recognizer = Recognizer(currentModel, SAMPLE_RATE.toFloat())
            try {
                val bytes = ByteArray(pcm16kHzMono.size * 2)
                ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().put(pcm16kHzMono)
                recognizer.acceptWaveForm(bytes, bytes.size)
                val json = recognizer.finalResult
                Log.i(TAG, "Vosk raw result: $json")
                extractText(json)
            } finally {
                recognizer.close()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Transcription failed for a segment, leaving its text empty", e)
            ""
        }
    }

    private fun extractText(resultJson: String): String {
        val match = TEXT_FIELD_REGEX.find(resultJson) ?: return ""
        return match.groupValues[1].trim()
    }

    companion object {
        const val SAMPLE_RATE = 16000

        /** Segments shorter than this are skipped: too little audio for a reliable decode, and the
         * likeliest trigger for the native "no valid final token" crash linked in the class kdoc. */
        private const val MIN_SAMPLES_TO_ATTEMPT = SAMPLE_RATE / 4 // 250ms

        private const val MODEL_ASSET_DIR = "model-pl-small"
        private const val TAG = "VoskTranscriber"
        private val TEXT_FIELD_REGEX = Regex(""""text"\s*:\s*"([^"]*)"""")
    }
}
