package com.voicechoicer.core.subtitle

import com.voicechoicer.core.model.Cue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SpeakerDetectorTest {

    @Test
    fun `splits dual dash dialogue into two fragments proportional to text length`() {
        val cue = Cue(
            index = 1,
            startMs = 0,
            endMs = 4000,
            text = "- Hi!\n- Hello there, how are you doing today?",
        )

        val fragments = SpeakerDetector.detectFragments(listOf(cue))

        assertEquals(2, fragments.size)
        assertEquals("Hi!", fragments[0].text)
        assertEquals("Hello there, how are you doing today?", fragments[1].text)
        assertEquals(0L, fragments[0].startMs)
        assertEquals(fragments[0].endMs, fragments[1].startMs)
        assertEquals(4000L, fragments[1].endMs)
        assertTrue("longer line should get a bigger time slice", fragments[1].durationMs > fragments[0].durationMs)
        assertTrue(fragments[0].characterKey != fragments[1].characterKey)
    }

    @Test
    fun `extracts named speaker prefix`() {
        val cue = Cue(index = 1, startMs = 1000, endMs = 2000, text = "JOHN: We need to leave, now.")

        val fragments = SpeakerDetector.detectFragments(listOf(cue))

        assertEquals(1, fragments.size)
        assertEquals("We need to leave, now.", fragments[0].text)
        assertEquals("name_john", fragments[0].characterKey)

        val characters = SpeakerDetector.charactersOf(fragments)
        assertEquals(1, characters.size)
        assertEquals("John", characters[0].displayName)
    }

    @Test
    fun `unlabeled lines are grouped under a single unknown character`() {
        val cues = listOf(
            Cue(index = 1, startMs = 0, endMs = 1000, text = "Plain line one"),
            Cue(index = 2, startMs = 1000, endMs = 2000, text = "Plain line two"),
        )

        val fragments = SpeakerDetector.detectFragments(cues)
        val characters = SpeakerDetector.charactersOf(fragments)

        assertEquals(1, characters.size)
        assertEquals(SpeakerDetector.UNKNOWN_DISPLAY_NAME, characters[0].displayName)
        assertEquals(2, fragments.size)
        assertEquals(fragments[0].characterKey, fragments[1].characterKey)
    }

    @Test
    fun `fragments stay in chronological order index`() {
        val cues = listOf(
            Cue(index = 1, startMs = 0, endMs = 1000, text = "- A\n- B"),
            Cue(index = 2, startMs = 1000, endMs = 2000, text = "JANE: hi"),
        )

        val fragments = SpeakerDetector.detectFragments(cues)

        assertEquals(listOf(0, 1, 2), fragments.map { it.orderIndex })
    }
}
