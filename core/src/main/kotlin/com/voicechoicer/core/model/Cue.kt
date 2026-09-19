package com.voicechoicer.core.model

/**
 * A single subtitle cue parsed from an .srt/.vtt file, before speaker
 * assignment. [speakerLabel] is whatever raw hint the subtitle format gave
 * us (e.g. "JOHN" from a "JOHN: hello" line, or a stable per-line-position
 * key for dash-dialogue cues); it is null when no hint was found.
 */
data class Cue(
    val index: Int,
    val startMs: Long,
    val endMs: Long,
    val text: String,
    val speakerLabel: String? = null,
)

/** A time range in the source clip where someone is speaking, with no line-break info. */
data class SpeechSegment(
    val startMs: Long,
    val endMs: Long,
)

/**
 * A fragment of the clip assigned to a character, with the line the player
 * must read out loud. This is the unit the recording flow operates on.
 */
data class Fragment(
    val orderIndex: Int,
    val startMs: Long,
    val endMs: Long,
    val text: String,
    val characterKey: String,
) {
    val durationMs: Long get() = endMs - startMs
}

/** A detected (or user-defined) character/speaker, identified by a stable key. */
data class DetectedCharacter(
    val key: String,
    val displayName: String,
)
