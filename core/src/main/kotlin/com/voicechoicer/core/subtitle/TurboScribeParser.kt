package com.voicechoicer.core.subtitle

import com.voicechoicer.core.model.Cue

/**
 * Parses TurboScribe's plain-text transcript export: no cue blocks like
 * .srt/.vtt, just running text with inline `(M:SS)` (or `(H:MM:SS)`) markers
 * before each sentence/phrase, e.g. `(0:04) Wystarczy. (0:19) Ciach, ciach!`.
 * Line breaks inside the pasted text carry no meaning - a marker can appear
 * mid-line and text can wrap across several lines before the next marker.
 *
 * There is no end time per phrase, so each cue's end is the next marker's
 * start; the very last cue (with nothing after it to bound it) gets a small
 * fixed duration and is expected to be clamped to the video's real length by
 * the caller, same as the last cue of a hand-edited .srt/.vtt file might be.
 */
object TurboScribeParser {

    private val TIMESTAMP = Regex("""\((\d{1,3}(?::\d{1,2}){1,2})\)""")
    private const val MIN_TIMESTAMP_COUNT = 2
    private const val LAST_CUE_FALLBACK_DURATION_MS = 4000L

    /** Heuristic: TurboScribe's export has no "-->" ranges but has several `(M:SS)` markers. */
    fun looksLikeTurboScribe(content: String): Boolean =
        !content.contains("-->") && TIMESTAMP.findAll(content).count() >= MIN_TIMESTAMP_COUNT

    fun parse(content: String): List<Cue> {
        val matches = TIMESTAMP.findAll(content).toList()
        val cues = mutableListOf<Cue>()
        var index = 1
        for (i in matches.indices) {
            val match = matches[i]
            val startMs = parseTimestampMs(match.groupValues[1])
            val textStart = match.range.last + 1
            val textEnd = if (i + 1 < matches.size) matches[i + 1].range.first else content.length
            val text = content.substring(textStart, textEnd).replace(Regex("""\s+"""), " ").trim()
            if (text.isEmpty()) continue

            val endMs = if (i + 1 < matches.size) {
                parseTimestampMs(matches[i + 1].groupValues[1]).coerceAtLeast(startMs + 1)
            } else {
                startMs + LAST_CUE_FALLBACK_DURATION_MS
            }
            cues += Cue(index = index++, startMs = startMs, endMs = endMs, text = text)
        }
        return cues
    }

    private fun parseTimestampMs(text: String): Long {
        val segments = text.split(':').map { it.toLong() }
        val (hours, minutes, seconds) = when (segments.size) {
            3 -> Triple(segments[0], segments[1], segments[2])
            else -> Triple(0L, segments[0], segments[1])
        }
        return ((hours * 60 + minutes) * 60 + seconds) * 1000
    }
}
