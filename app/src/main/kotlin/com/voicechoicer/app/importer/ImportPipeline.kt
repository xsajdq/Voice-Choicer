package com.voicechoicer.app.importer

import android.net.Uri
import com.voicechoicer.app.data.ProjectRepository
import com.voicechoicer.app.data.files.AppFileStore
import com.voicechoicer.app.media.AudioDecoder
import com.voicechoicer.app.media.VideoProbe
import com.voicechoicer.core.audio.SilenceSegmenter
import com.voicechoicer.core.model.DetectedCharacter
import com.voicechoicer.core.model.Fragment
import com.voicechoicer.core.subtitle.SpeakerDetector
import com.voicechoicer.core.subtitle.SubtitleParser
import javax.inject.Inject

sealed interface ImportProgress {
    data object CopyingVideo : ImportProgress
    data object ReadingSubtitles : ImportProgress
    data object AnalyzingAudio : ImportProgress
    data object Saving : ImportProgress
    data class Done(val projectId: Long) : ImportProgress
    data class Failed(val message: String) : ImportProgress
}

/**
 * Orchestrates turning a picked video (+ optional subtitle file) into a
 * fully-formed project: copy the clip locally, split it into fragments
 * (from subtitles when available, otherwise via silence detection), detect
 * characters, and persist everything.
 */
class ImportPipeline @Inject constructor(
    private val fileStore: AppFileStore,
    private val videoProbe: VideoProbe,
    private val audioDecoder: AudioDecoder,
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
                onProgress(ImportProgress.AnalyzingAudio)
                buildFromSilenceDetection(videoFile.absolutePath, durationMs)
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

    private fun buildFromSilenceDetection(videoPath: String, durationMs: Long): Pair<List<DetectedCharacter>, List<Fragment>> {
        val decoded = audioDecoder.decodeAudioTrack(videoPath)
        val segments = SilenceSegmenter.segment(decoded.pcm, decoded.sampleRate)
        require(segments.isNotEmpty()) {
            "Nie udało się wykryć żadnych fragmentów mowy. Spróbuj dołączyć plik napisów (.srt/.vtt)."
        }
        val unknownKey = "unknown"
        val fragments = segments.mapIndexed { index, segment ->
            Fragment(
                orderIndex = index,
                startMs = segment.startMs,
                endMs = segment.endMs.coerceAtMost(durationMs),
                text = "",
                characterKey = unknownKey,
            )
        }
        val characters = listOf(DetectedCharacter(unknownKey, SpeakerDetector.UNKNOWN_DISPLAY_NAME))
        return characters to fragments
    }
}
