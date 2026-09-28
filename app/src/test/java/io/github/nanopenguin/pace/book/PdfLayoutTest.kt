package io.github.nanopenguin.pace.book

import org.junit.Assert.assertEquals
import org.junit.Test

class PdfLayoutTest {
    @Test
    fun `joins lines into paragraphs split on larger gaps`() {
        val page =
            listOf(
                body("The first paragraph runs over", 100f),
                body("two lines.", 112f, right = 200f),
                body("The second one starts after a gap", 136f),
                body("and ends here.", 148f, right = 200f),
            )

        assertEquals(
            listOf(
                Block.Paragraph("The first paragraph runs over two lines."),
                Block.Paragraph("The second one starts after a gap and ends here."),
            ),
            PdfLayout.blocks(listOf(page), emptyList()),
        )
    }

    @Test
    fun `an indented line or a short line ending a sentence starts a paragraph`() {
        val page =
            listOf(
                body("One paragraph ends on a short line.", 100f, right = 300f),
                body("Another follows without a gap, and it", 112f),
                body("ends early too.", 124f, right = 200f),
                body("A third is indented", 136f, left = 90f),
                body("like books often do", 148f),
            )

        assertEquals(
            listOf(
                Block.Paragraph("One paragraph ends on a short line."),
                Block.Paragraph("Another follows without a gap, and it ends early too."),
                Block.Paragraph("A third is indented like books often do"),
            ),
            PdfLayout.blocks(listOf(page), emptyList()),
        )
    }

    @Test
    fun `a hanging indent continues the paragraph`() {
        val page =
            listOf(
                body("1) The first item wraps", 100f),
                body("onto a second line", 112f, left = 86f),
                body("and a third.", 124f, left = 86f, right = 200f),
                body("2) The second item.", 136f, right = 250f),
            )

        assertEquals(
            listOf(
                Block.Paragraph("1) The first item wraps onto a second line and a third."),
                Block.Paragraph("2) The second item."),
            ),
            PdfLayout.blocks(listOf(page), emptyList()),
        )
    }

    @Test
    fun `undoes hyphenation at line breaks but keeps real hyphens`() {
        val page =
            listOf(
                body("A hyphen-", 100f),
                body("ated word, a soft­", 112f),
                body("hyphen and a well-", 124f),
                body("Known name.", 136f),
            )

        assertEquals(
            listOf(Block.Paragraph("A hyphenated word, a softhyphen and a well- Known name.")),
            PdfLayout.blocks(listOf(page), emptyList()),
        )
    }

    @Test
    fun `larger short lines are headings, ranked by size and merged when stacked`() {
        val page =
            listOf(
                PdfLine("Chapter 1", 20f, 72f, 200f, 80f),
                PdfLine("The Beginning", 24f, 72f, 250f, 110f),
                body("Some text.", 150f),
                PdfLine("A Section", 14f, 72f, 180f, 180f),
                body("More text.", 210f),
            )

        assertEquals(
            listOf(
                Block.Heading("Chapter 1: The Beginning", 1),
                Block.Paragraph("Some text."),
                Block.Heading("A Section", 2),
                Block.Paragraph("More text."),
            ),
            PdfLayout.blocks(listOf(page), emptyList()),
        )
    }

    @Test
    fun `many large lines in a row are text, and figure labels are not headings`() {
        val page =
            listOf(
                PdfLine("An introduction set", 14f, 72f, 540f, 80f),
                PdfLine("in a larger font", 14f, 72f, 540f, 96f),
                PdfLine("runs over more", 14f, 72f, 540f, 112f),
                PdfLine("than three lines.", 14f, 72f, 540f, 128f),
                PdfLine("00 1F 3E 7A", 14f, 72f, 540f, 200f),
                body("The body text is set in a smaller font than the rest of this page.", 240f),
                body("It is the most common size on the page, which makes it the body.", 252f),
            )

        assertEquals(
            listOf(
                Block.Paragraph("An introduction set in a larger font runs over more than three lines."),
                Block.Paragraph("00 1F 3E 7A"),
                Block.Paragraph(
                    "The body text is set in a smaller font than the rest of this page. " +
                        "It is the most common size on the page, which makes it the body.",
                ),
            ),
            PdfLayout.blocks(listOf(page), emptyList()),
        )
    }

    @Test
    fun `drops running headers, footers and page numbers`() {
        val pages =
            listOf("first", "second", "third").mapIndexed { index, word ->
                listOf(
                    // Some PDFs draw the footer first.
                    body("- ${index + 1} -", 760f, right = 100f),
                    body("A Novel — page ${index + 1}", 40f, right = 200f),
                    body("Text of the $word page", 100f),
                    body("continues on the $word", 112f),
                )
            }

        assertEquals(
            listOf(
                Block.Paragraph(
                    "Text of the first page continues on the first Text of the second page continues on the second " +
                        "Text of the third page continues on the third",
                ),
            ),
            PdfLayout.blocks(pages, emptyList()),
        )
    }

    @Test
    fun `drops headers that change with the chapter but carry the page number`() {
        val chapters = listOf("Arrival", "Arrival", "Departure", "Return")
        val words = listOf("one", "two", "three", "four")
        val pages =
            chapters.mapIndexed { index, chapter ->
                listOfNotNull(
                    body("${index + 5} $chapter", 40f, right = 200f),
                    // Only numbers matching the page numbering are dropped.
                    if (index == 0) body("Section 2", 88f, right = 200f) else null,
                    body("Text on page ${words[index]}", 100f),
                )
            }

        assertEquals(
            listOf(Block.Paragraph("Section 2 Text on page one Text on page two Text on page three Text on page four")),
            PdfLayout.blocks(pages, emptyList()),
        )
    }

    @Test
    fun `keeps chapter headings that open pages the same way`() {
        val words = listOf("first", "second", "third")
        val pages =
            words.mapIndexed { index, word ->
                listOf(PdfLine("Chapter ${index + 1}", 20f, 72f, 200f, 80f), body("The text of the $word chapter.", 120f))
            }

        assertEquals(
            words.flatMapIndexed { index, word ->
                listOf(Block.Heading("Chapter ${index + 1}", 1), Block.Paragraph("The text of the $word chapter."))
            },
            PdfLayout.blocks(pages, emptyList()),
        )
    }

    @Test
    fun `outline titles mark lines as headings or are inserted where they point`() {
        val pages =
            listOf(
                listOf(body("Introduction", 100f, right = 150f), body("Why this matters.", 124f)),
                listOf(body("The first part of a", 100f), body("long title", 112f, right = 150f), body("Its text.", 136f)),
                listOf(body("Text before.", 100f), body("Text after.", 300f)),
            )
        val outline =
            listOf(
                PdfOutlineEntry("INTRODUCTION", level = 1, pageIndex = 0, top = 90f),
                PdfOutlineEntry("The first part of a long title", level = 2, pageIndex = 1, top = null),
                PdfOutlineEntry("Unprinted", level = 1, pageIndex = 2, top = 290f),
                PdfOutlineEntry("Nowhere", level = 1, pageIndex = 7, top = null),
            )

        assertEquals(
            listOf(
                Block.Heading("INTRODUCTION", 1),
                Block.Paragraph("Why this matters."),
                Block.Heading("The first part of a long title", 2),
                Block.Paragraph("Its text. Text before."),
                Block.Heading("Unprinted", 1),
                Block.Paragraph("Text after."),
            ),
            PdfLayout.blocks(pages, outline),
        )
    }

    @Test
    fun `with an outline, large lines are not headings`() {
        val words = listOf("one", "two", "three")
        val pages = words.map { listOf(PdfLine("Large", 20f, 72f, 150f, 80f), body("Body text $it.", 120f)) }
        val outline = words.mapIndexed { index, word -> PdfOutlineEntry("Part $word", level = 1, pageIndex = index, top = null) }

        assertEquals(
            words.flatMap { listOf(Block.Heading("Part $it", 1), Block.Paragraph("Large"), Block.Paragraph("Body text $it.")) },
            PdfLayout.blocks(pages, outline),
        )
    }

    @Test
    fun `pages without text give no blocks`() {
        assertEquals(emptyList<Block>(), PdfLayout.blocks(listOf(emptyList(), listOf(body("  ", 100f))), emptyList()))
    }

    private fun body(
        text: String,
        baseline: Float,
        left: Float = 72f,
        right: Float = 540f,
    ) = PdfLine(text, fontSize = 10f, left = left, right = right, baseline = baseline)
}
