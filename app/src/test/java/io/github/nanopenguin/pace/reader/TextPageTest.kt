package io.github.nanopenguin.pace.reader

import org.junit.Assert.assertEquals
import org.junit.Test

class TextPageTest {
    @Test
    fun `words anchor on their focal letter within the line`() {
        val line = PageLine(listOf("So", "she", "considered."), firstFrame = 0, isHeading = false)
        assertEquals("So she considered.", line.text)
        assertEquals(1, line.anchorChar(0))
        assertEquals(4, line.anchorChar(1))
        assertEquals(10, line.anchorChar(2))
    }

    @Test
    fun `headings anchor on their first word`() {
        val heading = PageLine(listOf("The Pool of Tears"), firstFrame = 0, isHeading = true)
        assertEquals(1, heading.anchorChar(0))
    }

    @Test
    fun `headings get extra space above, except at the start of the book`() {
        fun line(
            firstFrame: Int,
            isHeading: Boolean = false,
        ) = PageLine(listOf("x"), firstFrame, isHeading)

        val page =
            TextPage(
                lines = listOf(line(0, isHeading = true), line(1), line(2), line(3, isHeading = true), line(4)),
                currentLine = 2,
                currentWord = 0,
            )
        assertEquals(listOf(-20f, -10f, 0f, 15f, 25f), page.lineOffsets(lineHeight = 10f, headingGap = 5f).toList())
    }
}
