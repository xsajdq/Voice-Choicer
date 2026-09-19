package com.voicechoicer.core.subtitle

import com.voicechoicer.core.model.Cue
import com.voicechoicer.core.model.DetectedCharacter
import com.voicechoicer.core.model.Fragment
import kotlin.math.max

/**
 * Turns raw subtitle [Cue]s into [Fragment]s assigned to characters, using
 * two cheap-but-common heuristics found in real-world subtitle files:
 *
 * 1. "NAME: line" - a script-style speaker prefix. The name becomes the
 *    character key.
 * 2. "- line one" / "- line two" on separate lines within the *same* cue -
 *    the classic dual-dialogue convention. Each dash line is treated as a
 *    different speaker turn; we split the cue's time range proportionally
 *    by character count so both turns get a plausible slice of the cue.
 *
 * Cues that match neither heuristic are all bucketed under a single
 * "unknown" character - the app lets the user split/rename these by hand
 * afterwards, since there is no reliable offline way to tell speakers
 * apart from text alone.
 */
object SpeakerDetector {

    private val NAME_PREFIX = Regex("""^([\p{Lu}][\p{Lu}0-9 .'\-]{1,24}):\s*(.+)$""")
    private const val UNKNOWN_KEY = "unknown"
    const val UNKNOWN_DISPLAY_NAME = "Nieznana postać"

    fun detectFragments(cues: List<Cue>): List<Fragment> {
        val fragments = mutableListOf<Fragment>()
        var order = 0
        for (cue in cues) {
            val lines = cue.text.split("\n").map { it.trim() }.filter { it.isNotEmpty() }
            val dashLines = lines.filter { it.startsWith("-") }

            if (dashLines.size >= 2) {
                fragments += splitDashDialogue(order, cue, dashLines)
                order += dashLines.size
                continue
            }

            val singleLine = lines.joinToString(" ")
            val nameMatch = NAME_PREFIX.find(singleLine)
            if (nameMatch != null) {
                val (name, spokenText) = nameMatch.destructured
                fragments += Fragment(
                    orderIndex = order++,
                    startMs = cue.startMs,
                    endMs = cue.endMs,
                    text = spokenText.trim(),
                    characterKey = keyFor(name.trim()),
                )
            } else {
                fragments += Fragment(
                    orderIndex = order++,
                    startMs = cue.startMs,
                    endMs = cue.endMs,
                    text = singleLine,
                    characterKey = UNKNOWN_KEY,
                )
            }
        }
        return fragments
    }

    fun charactersOf(fragments: List<Fragment>): List<DetectedCharacter> {
        return fragments
            .map { it.characterKey }
            .distinct()
            .map { key ->
                DetectedCharacter(
                    key = key,
                    displayName = if (key == UNKNOWN_KEY) UNKNOWN_DISPLAY_NAME else displayNameFor(key),
                )
            }
    }

    private fun splitDashDialogue(startOrder: Int, cue: Cue, dashLines: List<String>): List<Fragment> {
        val texts = dashLines.map { it.removePrefix("-").trim() }
        val totalChars = max(1, texts.sumOf { it.length })
        val totalDuration = cue.endMs - cue.startMs
        var cursor = cue.startMs
        return texts.mapIndexed { i, text ->
            val share = if (i == texts.lastIndex) {
                cue.endMs - cursor
            } else {
                (totalDuration * text.length.coerceAtLeast(1) / totalChars).coerceAtLeast(1)
            }
            val segmentStart = cursor
            val segmentEnd = (cursor + share).coerceAtMost(cue.endMs)
            cursor = segmentEnd
            Fragment(
                orderIndex = startOrder + i,
                startMs = segmentStart,
                endMs = segmentEnd,
                text = text,
                characterKey = "dash_speaker_${i + 1}",
            )
        }
    }

    private fun keyFor(name: String): String = "name_" + name.lowercase().replace(Regex("""\s+"""), "_")

    private fun displayNameFor(key: String): String {
        if (key.startsWith("dash_speaker_")) {
            val n = key.substringAfterLast('_')
            return "Postać $n"
        }
        return key.removePrefix("name_").split('_').joinToString(" ") { part ->
            part.replaceFirstChar { it.uppercase() }
        }
    }
}
