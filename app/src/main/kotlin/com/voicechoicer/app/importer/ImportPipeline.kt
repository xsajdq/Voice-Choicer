package com.voicechoicer.app.importer

import android.net.Uri
import com.voicechoicer.app.data.ProjectRepository
import com.voicechoicer.app.data.files.AppFileStore
import com.voicechoicer.app.media.AudioDecoder
import com.voicechoicer.app.media.VideoProbe
import com.voicechoicer.app.media.VoskTranscriber
import com.voicechoicer.core.audio.PitchEstimator
import com.voicechoicer.core.audio.Resampler
import com.voicechoicer.core.audio.SilenceSegmenter
import com.voicechoicer.core.audio.SpeakerClusterer
import com.voicechoicer.core.model.DetectedCharacter
import com.voicechoicer.core.model.Fragment
import com.voicechoicer.core.subtitle.SpeakerDetector
import com.voicechoicer.core.subtitle.SubtitleParser
import javax.inject.Inject

sealed interface ImportProgress {
    data object CopyingVideo : ImportProgress
    data object ReadingSubtitles : ImportProgress
    data object AnalyzingAudio : ImportProgress
    data class TranscribingSpeech(val doneCount: Int, val totalCount: Int) : ImportProgress
    data object Saving : ImportProgress
    data class Done(val projectId: Long) : ImportProgress
    data class Failed(val message: String) : ImportProgress
}

/**
 * Orchestrates turning a picked video (+ optional subtitle file) into a
 * fully-formed project: copy the clip locally, split it into fragments
 * (from subtitles when available, otherwise via silence detection plus
 * offline speech-to-text and voice-based speaker clustering), detect
 * characters, and persist everything.
 */
class ImportPipeline @Inject constructor(
    private val fileStore: AppFileStore,
    private val videoProbe: VideoProbe,
    private val audioDecoder: AudioDecoder,
    private val voskTranscriber: VoskTranscriber,
    private val repository: ProjectRepository,
) {

    suspend fun import(
        title: String,
        videoUri: Uri,
        subtitleUri: Uri?,
        onProgress: (ImportProgress) -> Unit,
    ): Long {
        onProgress(ImportProgress.CopyingVideo)
        val videoFile = fileStore.importVideo(videoUri)
        try {
            require(videoProbe.hasVideoTrack(videoFile.absolutePath)) {
                "Wybrany plik nie zawiera ścieżki wideo. Wybierz plik wideo (np. .mp4)."
            }
            val durationMs = videoProbe.durationMs(videoFile.absolutePath).coerceAtLeast(1)

            val (characters, fragments) = if (subtitleUri != null) {
                onProgress(ImportProgress.ReadingSubtitles)
                buildFromSubtitles(subtitleUri, durationMs)
            } else {
                buildFromSilenceDetection(videoFile.absolutePath, durationMs, onProgress)
            }

            onProgress(ImportProgress.Saving)
            val projectId = repository.createProject(
                title = title,
                videoFile = videoFile,
                durationMs = durationMs,
                detectedCharacters = characters,
                fragments = fragments,
            )
            onProgress(ImportProgress.Done(projectId))
            return projectId
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

    /**
     * No subtitles: find speech segments by silence detection, transcribe each one offline
     * (Vosk, Polish model - falls back to empty text if the model failed to load), estimate a
     * voice-pitch feature per segment, and cluster those pitches into automatically-detected
     * "characters". This is a heuristic, not real diarization - see SpeakerClusterer's docs.
     */
    private suspend fun buildFromSilenceDetection(
        videoPath: String,
        durationMs: Long,
        onProgress: (ImportProgress) -> Unit,
    ): Pair<List<DetectedCharacter>, List<Fragment>> {
        onProgress(ImportProgress.AnalyzingAudio)
        val decoded = audioDecoder.decodeAudioTrack(videoPath)
        val segments = SilenceSegmenter.segment(decoded.pcm, decoded.sampleRate)
        require(segments.isNotEmpty()) {
            "Nie udało się wykryć żadnych fragmentów mowy. Spróbuj dołączyć plik napisów (.srt/.vtt)."
        }

        val transcriptionAvailable = voskTranscriber.ensureLoaded()

        data class SegmentInfo(val startMs: Long, val endMs: Long, val text: String, val pitchHz: Double)

        val infos = segments.mapIndexed { index, segment ->
            onProgress(ImportProgress.TranscribingSpeech(index, segments.size))

            val startSample = ((segment.startMs * decoded.sampleRate) / 1000).toInt().coerceIn(0, decoded.pcm.size)
            val endSample = ((segment.endMs * decoded.sampleRate) / 1000).toInt().coerceIn(startSample, decoded.pcm.size)
            val segmentPcm = decoded.pcm.copyOfRange(startSample, endSample)

            val pitch = PitchEstimator.estimateAveragePitchHz(segmentPcm, decoded.sampleRate)
            val text = if (transcriptionAvailable) {
                val resampled = Resampler.resample(segmentPcm, decoded.sampleRate, VoskTranscriber.SAMPLE_RATE)
                voskTranscriber.transcribe(resampled)
            } else {
                ""
            }

            SegmentInfo(segment.startMs, segment.endMs.coerceAtMost(durationMs), text, pitch)
        }
        onProgress(ImportProgress.TranscribingSpeech(segments.size, segments.size))

        // Segments too quiet/short for a reliable pitch reading fall back to the batch's median
        // voiced pitch, so a handful of unclear frames don't get spuriously split into their own
        // "character" purely for lack of signal.
        val voicedPitches = infos.map { it.pitchHz }.filter { it > 0.0 }.sorted()
        val fallbackPitch = if (voicedPitches.isNotEmpty()) voicedPitches[voicedPitches.size / 2] else 150.0
        val pitchesForClustering = infos.map { if (it.pitchHz > 0.0) it.pitchHz else fallbackPitch }

        val clusterLabels = SpeakerClusterer.cluster(pitchesForClustering)

        val fragments = infos.mapIndexed { index, info ->
            Fragment(
                orderIndex = index,
                startMs = info.startMs,
                endMs = info.endMs,
                text = info.text,
                characterKey = "speaker_${clusterLabels[index]}",
            )
        }
        val characters = clusterLabels.toSet().sorted().map { clusterIndex ->
            DetectedCharacter("speaker_$clusterIndex", "Postać ${clusterIndex + 1}")
        }
        return characters to fragments
    }
}
