package com.voicechoicer.core.subtitle

import org.junit.Assert.assertEquals
import org.junit.Test

class SubtitleParserTest {

    @Test
    fun `parses basic srt`() {
        val srt = """
            1
            00:00:01,000 --> 00:00:04,500
            Hello there, general Kenobi.

            2
            00:00:05,000 --> 00:00:06,250
            Goodbye.
        """.trimIndent()

        val cues = SubtitleParser.parse(srt)

        assertEquals(2, cues.size)
        assertEquals(1000L, cues[0].startMs)
        assertEquals(4500L, cues[0].endMs)
        assertEquals("Hello there, general Kenobi.", cues[0].text)
        assertEquals(5000L, cues[1].startMs)
        assertEquals(6250L, cues[1].endMs)
    }

    @Test
    fun `parses vtt with header and cue settings and strips tags`() {
        val vtt = """
            WEBVTT

            NOTE this is a comment

            00:00:01.000 --> 00:00:03.000 align:start position:0%
            <i>Hi!</i>

            00:01:02.500 --> 00:01:05.000
            Second line
        """.trimIndent()

        val cues = SubtitleParser.parse(vtt)

        assertEquals(2, cues.size)
        assertEquals("Hi!", cues[0].text)
        assertEquals(1000L, cues[0].startMs)
        assertEquals(62500L, cues[1].startMs)
        assertEquals(65000L, cues[1].endMs)
    }

    @Test
    fun `keeps multi-line dash dialogue as a single cue with newlines`() {
        val srt = """
            1
            00:00:01,000 --> 00:00:04,000
            - Hi John!
            - Hi Mary!
        """.trimIndent()

        val cues = SubtitleParser.parse(srt)

        assertEquals(1, cues.size)
        assertEquals("- Hi John!\n- Hi Mary!", cues[0].text)
    }

    @Test
    fun `ignores malformed blocks without failing`() {
        val srt = """
            1
            not a timestamp
            some text

            2
            00:00:10,000 --> 00:00:11,000
            Valid line
        """.trimIndent()

        val cues = SubtitleParser.parse(srt)

        assertEquals(1, cues.size)
        assertEquals("Valid line", cues[0].text)
    }
}
