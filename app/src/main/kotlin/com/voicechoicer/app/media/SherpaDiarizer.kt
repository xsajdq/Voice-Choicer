package com.voicechoicer.app.media

import android.content.Context
import android.util.Log
import com.k2fsa.sherpa.onnx.FastClusteringConfig
import com.k2fsa.sherpa.onnx.OfflineSpeakerDiarization
import com.k2fsa.sherpa.onnx.OfflineSpeakerDiarizationConfig
import com.k2fsa.sherpa.onnx.OfflineSpeakerSegmentationModelConfig
import com.k2fsa.sherpa.onnx.OfflineSpeakerSegmentationPyannoteModelConfig
import com.k2fsa.sherpa.onnx.SpeakerEmbeddingExtractorConfig
import com.voicechoicer.core.audio.DiarizedSegment
import com.voicechoicer.core.audio.Resampler
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max

/**
 * Real ML-based speaker diarization (pyannote segmentation + a speaker-embedding model,
 * clustered by sherpa-onnx) - a genuine "who spoke when" pass over the whole track,
 * unlike [com.voicechoicer.core.audio.SpeakerClusterer]'s pitch-heuristic grouping of
 * already-cut segments. See the `downloadDiarizationModels`/`downloadSherpaNativeLibs`
 * Gradle tasks for where the bundled models and native library come from.
 *
 * Like [WhisperTranscriber], loading is expensive and native, so it's cached for the
 * process lifetime; failures are swallowed and callers fall back to the pitch heuristic.
 */
@Singleton
class SherpaDiarizer @Inject constructor(@ApplicationContext private val context: Context) {

    private var diarizer: OfflineSpeakerDiarization? = null
    private var loadFailed = false

    var lastError: String? = null
        private set

    fun ensureLoaded(): Boolean {
        if (diarizer != null) return true
        if (loadFailed) return false
        return try {
            val config = OfflineSpeakerDiarizationConfig(
                segmentation = OfflineSpeakerSegmentationModelConfig(
                    pyannote = OfflineSpeakerSegmentationPyannoteModelConfig(
                        model = "$MODEL_ASSET_DIR/segmentation.onnx",
                    ),
                    numThreads = 1,
                ),
                embedding = SpeakerEmbeddingExtractorConfig(
                    model = "$MODEL_ASSET_DIR/embedding.onnx",
                    numThreads = 1,
                ),
                clustering = FastClusteringConfig(numClusters = -1, threshold = 0.5f),
            )
            diarizer = OfflineSpeakerDiarization(context.assets, config)
            Log.i(TAG, "sherpa-onnx diarizer loaded")
            true
        } catch (e: Throwable) {
            loadFailed = true
            lastError = "Nie udało się wczytać modelu rozpoznawania rozmówców: ${e.message}"
            Log.e(TAG, "Failed to load sherpa-onnx diarizer", e)
            false
        }
    }

    /**
     * Runs diarization over the whole track and returns each detected speech turn with a
     * speaker id (stable within this call, starting at 0), sorted by start time. Returns an
     * empty list if the diarizer isn't loaded, on failure, or if no speech turns were found.
     */
    fun diarize(pcm: ShortArray, sampleRate: Int): List<DiarizedSegment> {
        val currentDiarizer = diarizer ?: return emptyList()
        if (pcm.isEmpty()) return emptyList()

        return try {
            val targetRate = currentDiarizer.sampleRate()
            val resampled = Resampler.resample(pcm, sampleRate, targetRate)
            val samples = FloatArray(resampled.size) { i -> (resampled[i] / 32768.0f).coerceIn(-1f, 1f) }
            val segments = currentDiarizer.process(samples)
            segments
                .map { seg ->
                    DiarizedSegment(
                        startMs = (seg.start * 1000).toLong(),
                        endMs = max((seg.end * 1000).toLong(), (seg.start * 1000).toLong() + 1),
                        speakerId = seg.speaker,
                    )
                }
                .sortedBy { it.startMs }
        } catch (e: Throwable) {
            Log.e(TAG, "Diarization failed", e)
            emptyList()
        }
    }

    companion object {
        private const val MODEL_ASSET_DIR = "diarization-model"
        private const val TAG = "SherpaDiarizer"
    }
}
