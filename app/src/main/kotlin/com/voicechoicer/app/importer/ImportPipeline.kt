package com.voicechoicer.app.importer

import android.net.Uri
import com.voicechoicer.app.data.ProjectRepository
import com.voicechoicer.app.data.files.AppFileStore
import com.voicechoicer.app.media.AudioDecoder
import com.voicechoicer.app.media.SherpaDiarizer
import com.voicechoicer.app.media.VideoProbe
import com.voicechoicer.app.media.WhisperTranscriber
import com.voicechoicer.core.audio.PitchEstimator
import com.voicechoicer.core.audio.SilenceSegmenter
import com.voicechoicer.core.audio.SpeakerAligner
import com.voicechoicer.core.audio.SpeakerClusterer
import com.voicechoicer.core.audio.TimeRange
import com.voicechoicer.core.model.DetectedCharacter
import com.voicechoicer.core.model.Fragment
import com.voicechoicer.core.subtitle.SpeakerDetector
import com.voicechoicer.core.subtitle.SubtitleParser
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

sealed interface ImportProgress {
    data object CopyingVideo : ImportProgress
    data object ReadingSubtitles : ImportProgress
    data object AnalyzingAudio : ImportProgress
    data class TranscribingSpeech(val doneCount: Int, val totalCount: Int) : ImportProgress
    data object DetectingSpeakers : ImportProgress
    data object Saving : ImportProgress
    data class Done(val projectId: Long, val warning: String? = null) : ImportProgress
    data class Failed(val message: String) : ImportProgress
}

/**
 * Orchestrates turning a picked video (+ optional subtitle file) into a
 * fully-formed project: copy the clip locally, split it into fragments
 * (from subtitles when available, otherwise via silence detection plus
 * offline speech-to-text and real ML-based speaker diarization), detect
 * characters, and persist everything.
 */
class ImportPipeline @Inject constructor(
    private val fileStore: AppFileStore,
    private val videoProbe: VideoProbe,
    private val audioDecoder: AudioDecoder,
    private val whisperTranscriber: WhisperTranscriber,
    private val sherpaDiarizer: SherpaDiarizer,
    private val repository: ProjectRepository,
) {

    suspend fun import(
        title: String,
        videoUri: Uri,
        subtitleUri: Uri?,
        onProgress: (ImportProgress) -> Unit,
    ): Long = withContext(Dispatchers.IO) {
        onProgress(ImportProgress.CopyingVideo)
        val videoFile = fileStore.importVideo(videoUri)
        try {
            require(videoProbe.hasVideoTrack(videoFile.absolutePath)) {
                "Wybrany plik nie zawiera ścieżki wideo. Wybierz plik wideo (np. .mp4)."
            }
            val durationMs = videoProbe.durationMs(videoFile.absolutePath).coerceAtLeast(1)

            var warning: String? = null
            val (characters, fragments) = if (subtitleUri != null) {
                onProgress(ImportProgress.ReadingSubtitles)
                buildFromSubtitles(subtitleUri, durationMs)
            } else {
                val result = buildFromAutoDetection(videoFile.absolutePath, durationMs, onProgress)
                warning = result.warning
                result.characters to result.fragments
            }

            onProgress(ImportProgress.Saving)
            val projectId = repository.createProject(
                title = title,
                videoFile = videoFile,
                durationMs = durationMs,
                detectedCharacters = characters,
                fragments = fragments,
            )
            onProgress(ImportProgress.Done(projectId, warning))
            projectId
        } catch (t: Throwable) {
            videoFile.delete()
            throw t
        }
    }

    private fun buildFromSubtitles(subtitleUri: Uri, durationMs: Long): Pair<List<DetectedCharacter>, List<Fragment>> {
        val text = fileStore.readText(subtitleUri)
        val cues = SubtitleParser.parse(text).filter { it.startMs < durationMs }
        val fragments = SpeakerDetector.detectFragments(cues).map { it.copy(endMs = it.endMs.coerceAtMost(durationMs)) }
        val characters = SpeakerDetector.charactersOf(fragments)
        require(fragments.isNotEmpty()) { "Plik napisów nie zawiera żadnych poprawnych linii" }
        return characters to fragments
    }

    private data class SilenceDetectionResult(
        val characters: List<DetectedCharacter>,
        val fragments: List<Fragment>,
        val warning: String?,
    )

    private data class SegmentInfo(val startMs: Long, val endMs: Long, val text: String, val pitchHz: Double)

    /**
     * No subtitles: transcribe the whole track in one pass and let Whisper choose its own segment
     * boundaries (see [WhisperTranscriber.transcribeWithSegments] for why - short version: feeding
     * it isolated, silence-cut clips one at a time hurts both the transcription and the cut
     * points). Falls back to plain silence-based segmentation with blank text - for manual
     * entry - if the model isn't available or found nothing.
     *
     * Each resulting segment is then assigned a speaker: preferably via [SherpaDiarizer]'s real
     * ML-based diarization (pyannote segmentation + speaker embeddings), aligned to the
     * transcript segments with [SpeakerAligner] since the two are independent passes over the
     * same audio. If the diarizer is unavailable or finds nothing, falls back to
     * [SpeakerClusterer]'s voice-pitch heuristic instead.
     */
    private suspend fun buildFromAutoDetection(
        videoPath: String,
        durationMs: Long,
        onProgress: (ImportProgress) -> Unit,
    ): SilenceDetectionResult {
        onProgress(ImportProgress.AnalyzingAudio)
        val decoded = audioDecoder.decodeAudioTrack(videoPath)

        val transcriptionAvailable = whisperTranscriber.ensureLoaded()
        var infos: List<SegmentInfo> = emptyList()
        var warning: String? = null

        if (transcriptionAvailable) {
            onProgress(ImportProgress.TranscribingSpeech(0, 1))
            val whisperSegments = whisperTranscriber.transcribeWithSegments(decoded.pcm, decoded.sampleRate)
            onProgress(ImportProgress.TranscribingSpeech(1, 1))
            infos = whisperSegments.map { seg -> toSegmentInfo(decoded, seg.startMs, seg.endMs, seg.text, durationMs) }
            if (infos.isEmpty()) {
                warning = "Rozpoznawanie mowy nie wykryło żadnego tekstu w tym nagraniu — kwestie trzeba będzie wpisać ręcznie."
            }
        } else {
            warning = "Rozpoznawanie mowy niedostępne (${whisperTranscriber.lastError ?: "nieznany błąd"}) — wpisz tekst kwestii ręcznie."
        }

        if (infos.isEmpty()) {
            val silenceSegments = SilenceSegmenter.segment(decoded.pcm, decoded.sampleRate)
            infos = silenceSegments.map { seg -> toSegmentInfo(decoded, seg.startMs, seg.endMs, "", durationMs) }
        }

        require(infos.isNotEmpty()) {
            "Nie udało się wykryć żadnych fragmentów mowy. Spróbuj dołączyć plik napisów (.srt/.vtt)."
        }

        // Segments too quiet/short for a reliable pitch reading fall back to the batch's median
        // voiced pitch, so a handful of unclear frames don't get spuriously split into their own
        // "character" purely for lack of signal.
        val voicedPitches = infos.map { it.pitchHz }.filter { it > 0.0 }.sorted()
        val fallbackPitch = if (voicedPitches.isNotEmpty()) voicedPitches[voicedPitches.size / 2] else 150.0
        val pitchesForClustering = infos.map { if (it.pitchHz > 0.0) it.pitchHz else fallbackPitch }
        val pitchClusterLabels = SpeakerClusterer.cluster(pitchesForClustering)

        onProgress(ImportProgress.DetectingSpeakers)
        val diarizationSegments = if (sherpaDiarizer.ensureLoaded()) {
            sherpaDiarizer.diarize(decoded.pcm, decoded.sampleRate)
        } else {
            emptyList()
        }
        val speakerLabels = if (diarizationSegments.isNotEmpty()) {
            val ranges = infos.map { TimeRange(it.startMs, it.endMs) }
            SpeakerAligner.assignSpeakers(ranges, diarizationSegments)
                .mapIndexed { index, speakerId -> speakerId ?: pitchClusterLabels[index] }
        } else {
            pitchClusterLabels
        }

        val fragments = infos.mapIndexed { index, info ->
            Fragment(
                orderIndex = index,
                startMs = info.startMs,
                endMs = info.endMs,
                text = info.text,
                characterKey = "speaker_${speakerLabels[index]}",
            )
        }
        val characters = speakerLabels.toSet().sorted().map { clusterIndex ->
            DetectedCharacter("speaker_$clusterIndex", "Postać ${clusterIndex + 1}")
        }
        return SilenceDetectionResult(characters, fragments, warning)
    }

    private fun toSegmentInfo(
        decoded: AudioDecoder.DecodedAudio,
        startMs: Long,
        endMs: Long,
        text: String,
        durationMs: Long,
    ): SegmentInfo {
        val startSample = ((startMs * decoded.sampleRate) / 1000).toInt().coerceIn(0, decoded.pcm.size)
        val endSample = ((endMs * decoded.sampleRate) / 1000).toInt().coerceIn(startSample, decoded.pcm.size)
        val pitch = PitchEstimator.estimateAveragePitchHz(decoded.pcm.copyOfRange(startSample, endSample), decoded.sampleRate)
        return SegmentInfo(startMs, endMs.coerceAtMost(durationMs), text, pitch)
    }
}
