package io.github.nanopenguin.lesto.reader

import org.junit.Assert.assertEquals
import org.junit.Test

class TimingTest {
    private fun word(
        text: String,
        pause: Pause = Pause.None,
    ) = Frame(text, isHeading = false, pause, blockIndex = 0)

    @Test
    fun `a plain word takes one word-time`() {
        assertEquals(1.0, word("rabbit").displayUnits(), 0.0)
    }

    @Test
    fun `long words and pauses add time`() {
        assertEquals(1.3, word("remarkable").displayUnits(), 1e-9)
        assertEquals(1.5, word("bank,", Pause.Clause).displayUnits(), 0.0)
        assertEquals(2.0, word("her.", Pause.Sentence).displayUnits(), 0.0)
        assertEquals(2.5, word("feet!”", Pause.Paragraph).displayUnits(), 0.0)
    }

    @Test
    fun `a heading takes two word-times per word plus its pause`() {
        val heading = Frame("The Pool of Tears", isHeading = true, Pause.Heading, blockIndex = 0)
        assertEquals(10.0, heading.displayUnits(), 0.0)
        assertEquals(2000, displayMillis(heading, wordsPerMinute = 300, framesSincePlay = 100))
    }

    @Test
    fun `headings stay readable at high speeds`() {
        val heading = Frame("Prologue", isHeading = true, Pause.Heading, blockIndex = 0)
        assertEquals(1500, displayMillis(heading, wordsPerMinute = 900, framesSincePlay = 100))
    }

    @Test
    fun `duration follows words per minute`() {
        assertEquals(200, displayMillis(word("rabbit"), wordsPerMinute = 300, framesSincePlay = 100))
        assertEquals(400, displayMillis(word("her.", Pause.Sentence), wordsPerMinute = 300, framesSincePlay = 100))
    }

    @Test
    fun `playback eases in after pressing play`() {
        assertEquals(2.0, rampUpFactor(0), 0.0)
        assertEquals(1.2, rampUpFactor(4), 1e-9)
        assertEquals(1.0, rampUpFactor(5), 0.0)
    }
}
