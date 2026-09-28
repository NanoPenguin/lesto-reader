package io.github.nanopenguin.pace.reader

import io.github.nanopenguin.pace.book.Block
import io.github.nanopenguin.pace.book.Book
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RsvpTextTest {
    private val text =
        Book(
            title = "Test",
            author = null,
            blocks =
            listOf(
                Block.Heading("  Chapter   One ", level = 1),
                Block.Paragraph("It was late. The Rabbit ran, “Oh dear!” it said."),
                Block.Paragraph("   "),
                Block.Heading("Two", level = 2),
                Block.Paragraph("The end"),
            ),
        ).toRsvpText()

    private val words get() = text.frames.map { it.text }

    @Test
    fun `headings are one frame and paragraphs one frame per word`() {
        assertEquals(
            listOf("Chapter One", "It", "was", "late.", "The", "Rabbit", "ran,", "“Oh", "dear!”", "it", "said.", "Two", "The", "end"),
            words,
        )
        assertEquals(listOf(true, false), text.frames.take(2).map { it.isHeading })
    }

    @Test
    fun `pauses follow punctuation and block ends`() {
        val pauses = text.frames.associate { it.text to it.pause }
        assertEquals(Pause.Heading, pauses["Chapter One"])
        assertEquals(Pause.None, pauses["It"])
        assertEquals(Pause.Sentence, pauses["late."])
        assertEquals(Pause.Clause, pauses["ran,"])
        assertEquals(Pause.Sentence, pauses["dear!”"])
        assertEquals(Pause.Paragraph, pauses["said."])
        assertEquals(Pause.Paragraph, pauses["end"])
    }

    @Test
    fun `chapters point at their heading frame`() {
        assertEquals(listOf(Chapter("Chapter One", 0), Chapter("Two", 11)), text.chapters)
        assertEquals("Chapter One", text.chapterAt(5)?.title)
        assertEquals("Two", text.chapterAt(12)?.title)
        assertNull(Book("", null, listOf(Block.Paragraph("No headings"))).toRsvpText().chapterAt(0))
    }

    @Test
    fun `going back a sentence first returns to the start of the current one`() {
        val rabbit = words.indexOf("Rabbit")
        val sentenceStart = words.indexOf("The")
        assertEquals(sentenceStart, text.previousSentenceStart(rabbit))
        assertEquals(words.indexOf("It"), text.previousSentenceStart(sentenceStart))
        assertEquals(0, text.previousSentenceStart(words.indexOf("It")))
        assertEquals(0, text.previousSentenceStart(0))
    }

    @Test
    fun `context words stay within their block`() {
        val it = words.indexOf("It")
        assertEquals(emptyList<String>(), text.wordsBefore(it, 3))
        assertEquals(listOf("was", "late.", "The"), text.wordsAfter(it, 3))
        assertEquals(listOf("it", "said."), text.wordsAfter(words.indexOf("dear!”"), 5))
        assertEquals(emptyList<String>(), text.wordsAfter(0, 3))
    }

    @Test
    fun `time left counts the remaining frames`() {
        assertEquals(0.0, text.unitsLeft(text.frames.size), 0.0)
        assertEquals(2.5, text.unitsLeft(text.lastIndex), 0.0)
    }
}
