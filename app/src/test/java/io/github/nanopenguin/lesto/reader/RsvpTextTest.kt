package io.github.nanopenguin.lesto.reader

import io.github.nanopenguin.lesto.book.Block
import io.github.nanopenguin.lesto.book.Book
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
        assertEquals(listOf(Chapter("Chapter One", 1, 0), Chapter("Two", 2, 11)), text.chapters)
        assertEquals(0, text.chapterIndexAt(5))
        assertEquals(1, text.chapterIndexAt(12))
    }

    @Test
    fun `sections span a chapter, or the whole book without headings`() {
        assertEquals(0..10, text.sectionAt(5))
        assertEquals(11..13, text.sectionAt(11))

        val untitled = Book("", null, listOf(Block.Paragraph("No headings here"))).toRsvpText()
        assertNull(untitled.chapterIndexAt(0))
        assertEquals(0..2, untitled.sectionAt(1))

        val preface =
            Book("", null, listOf(Block.Paragraph("Before"), Block.Heading("One", 1), Block.Paragraph("After")))
                .toRsvpText()
        assertEquals(0..0, preface.sectionAt(0))
        assertEquals(1..2, preface.sectionAt(2))
    }

    @Test
    fun `sentences end at sentence punctuation and headings`() {
        assertEquals(0..0, text.sentenceAt(0))
        assertEquals(1..3, text.sentenceAt(2))
        assertEquals(4..8, text.sentenceAt(words.indexOf("ran,")))
    }

    @Test
    fun `long sentences are split into lines`() {
        val ran = words.indexOf("ran,")
        assertEquals(4..8, text.lineAt(ran, maxWords = 10))
        assertEquals(6..7, text.lineAt(ran, maxWords = 2))
        assertEquals(8..8, text.lineAt(words.indexOf("dear!”"), maxWords = 2))
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
    fun `units add up the frames in a range`() {
        assertEquals(2.5, text.units(text.lastIndex..text.lastIndex), 0.0)
        assertEquals(3.5, text.units(12..13), 0.0)
    }
}
