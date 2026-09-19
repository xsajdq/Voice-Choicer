package com.voicechoicer.core.subtitle

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TurboScribeParserTest {

    private val sample = """
        Wykorzystano 1 z 3 dziennych transkrypcji

        videoplayback
        19 wrz 2026, 15:59
        videoplayback
        Play

        00:00
        01:14
        Mute
        Settings
        (0:04) Wystarczy. Wszystko wyśpiewa. (0:19) Ciach, ciach, ciach! Biegni, ile masz sił.
        I tak nie uciekniesz. (0:24) W męczarniach będziesz się wił! (0:26) Ty potworze! (0:27) Nie ja tu jestem, potworze.
        (0:29) Ty, ty i ta bajkowa hałastra, która psuje mu idealny świat.
        (0:34) Gadaj, gdzie się schowali! (0:36) Ugryź się!
    """.trimIndent()

    @Test
    fun `looksLikeTurboScribe is true for TurboScribe export`() {
        assertTrue(TurboScribeParser.looksLikeTurboScribe(sample))
    }

    @Test
    fun `looksLikeTurboScribe is false for srt content`() {
        val srt = "1\n00:00:01,000 --> 00:00:02,000\nHello\n"
        assertFalse(TurboScribeParser.looksLikeTurboScribe(srt))
    }

    @Test
    fun `looksLikeTurboScribe is false for plain text with no timestamps`() {
        assertFalse(TurboScribeParser.looksLikeTurboScribe("Just some plain text, no markers here."))
    }

    @Test
    fun `ignores UI chrome before the first timestamp`() {
        val cues = TurboScribeParser.parse(sample)
        assertEquals("Wystarczy. Wszystko wyśpiewa.", cues.first().text)
    }

    @Test
    fun `splits text at each timestamp marker across line breaks`() {
        val cues = TurboScribeParser.parse(sample)

        assertEquals(8, cues.size)
        assertEquals(4000L to 19000L, cues[0].startMs to cues[0].endMs)
        assertEquals("Wystarczy. Wszystko wyśpiewa.", cues[0].text)

        assertEquals(19000L to 24000L, cues[1].startMs to cues[1].endMs)
        assertEquals("Ciach, ciach, ciach! Biegni, ile masz sił. I tak nie uciekniesz.", cues[1].text)

        assertEquals(24000L to 26000L, cues[2].startMs to cues[2].endMs)
        assertEquals("W męczarniach będziesz się wił!", cues[2].text)
    }

    @Test
    fun `last cue gets a fallback duration since nothing bounds it`() {
        val cues = TurboScribeParser.parse(sample)
        val last = cues.last()
        assertEquals(36000L, last.startMs)
        assertEquals(40000L, last.endMs)
        assertEquals("Ugryź się!", last.text)
    }

    @Test
    fun `parses hour-minute-second timestamps`() {
        val cues = TurboScribeParser.parse("(1:02:03) Long video line. (1:02:10) Another line.")
        assertEquals(((1 * 60 + 2) * 60 + 3) * 1000L, cues[0].startMs)
    }

    @Test
    fun `empty input yields no cues`() {
        assertEquals(emptyList<Any>(), TurboScribeParser.parse(""))
    }
}
