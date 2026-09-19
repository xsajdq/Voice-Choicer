package com.voicechoicer.core.subtitle

import com.voicechoicer.core.model.Cue

/**
 * Parses .srt and .vtt subtitle files into a flat list of [Cue]s.
 *
 * Both formats share the same block shape once you ignore the header/cue
 * settings: an optional id line, a "start --> end" timestamp line, then one
 * or more text lines, separated by a blank line. We detect the format only
 * to know which decimal separator the timestamps use ("," for SRT, "." for
 * VTT) - everything else is handled generically.
 */
object SubtitleParser {

    private val TIMECODE_LINE = Regex(
        """(\d{1,2}:)?\d{2}:\d{2}[.,]\d{3}\s*-->\s*(\d{1,2}:)?\d{2}:\d{2}[.,]\d{3}"""
    )
    private val TAG_REGEX = Regex("""<[^>]+>""")
    private val KARAOKE_TAG_REGEX = Regex("""\{[^}]*}""")

    fun parse(content: String): List<Cue> {
        val normalized = content.replace("\r\n", "\n").replace('\r', '\n')
        val lines = normalized.split("\n")

        val cues = mutableListOf<Cue>()
        var i = 0
        var autoIndex = 1
        while (i < lines.size) {
            val line = lines[i].trim()
            if (line.isEmpty() || line.equals("WEBVTT", ignoreCase = true) || line.startsWith("NOTE")) {
                i++
                continue
            }
            val timecodeLineIndex = when {
                TIMECODE_LINE.containsMatchIn(line) -> i
                i + 1 < lines.size && TIMECODE_LINE.containsMatchIn(lines[i + 1]) -> i + 1
                else -> -1
            }
            if (timecodeLineIndex == -1) {
                // Stray line (e.g. a cue identifier we don't need); skip it.
                i++
                continue
            }
            val timecodeLine = lines[timecodeLineIndex]
            val match = TIMECODE_LINE.find(timecodeLine)!!
            val (startMs, endMs) = parseRange(match.value)

            var textStart = timecodeLineIndex + 1
            val textLines = mutableListOf<String>()
            while (textStart < lines.size && lines[textStart].trim().isNotEmpty()) {
                textLines.add(cleanText(lines[textStart]))
                textStart++
            }

            if (textLines.isNotEmpty() && endMs > startMs) {
                cues.add(
                    Cue(
                        index = autoIndex++,
                        startMs = startMs,
                        endMs = endMs,
                        text = textLines.joinToString("\n").trim(),
                    )
                )
            }
            i = textStart + 1
        }
        return cues
    }

    private fun cleanText(raw: String): String {
        return raw
            .replace(TAG_REGEX, "")
            .replace(KARAOKE_TAG_REGEX, "")
            .trim()
    }

    private fun parseRange(rangeText: String): Pair<Long, Long> {
        val parts = rangeText.split("-->").map { it.trim() }
        val start = parseTimecode(parts[0])
        // The end side may carry trailing VTT cue settings ("align:start position:0%").
        val end = parseTimecode(parts[1].substringBefore(' '))
        return start to end
    }

    private fun parseTimecode(text: String): Long {
        val normalized = text.replace(',', '.')
        val mainAndMillis = normalized.split('.')
        val millis = mainAndMillis.getOrElse(1) { "0" }.padEnd(3, '0').take(3).toLong()
        val segments = mainAndMillis[0].split(':').map { it.toLong() }
        val (hours, minutes, seconds) = when (segments.size) {
            3 -> Triple(segments[0], segments[1], segments[2])
            2 -> Triple(0L, segments[0], segments[1])
            else -> Triple(0L, 0L, segments[0])
        }
        return ((hours * 60 + minutes) * 60 + seconds) * 1000 + millis
    }
}
