package io.github.nanopenguin.pace.book

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class EpubParserTest {
    @Test
    fun `reads metadata and text in reading order`() {
        val book =
            parse(
                packageDocument(
                    metadata = "<dc:title>Alice</dc:title><dc:creator>Lewis Carroll</dc:creator>",
                    items = listOf("two" to "two.xhtml", "one" to "one.xhtml"),
                    spine = listOf("one", "two"),
                ),
                "OEBPS/one.xhtml" to xhtml("<h1>Chapter One</h1><p>First   paragraph.</p>"),
                "OEBPS/two.xhtml" to xhtml("<h2>Chapter Two</h2><p>Second.</p>"),
            )

        assertEquals("Alice", book.title)
        assertEquals("Lewis Carroll", book.author)
        assertEquals(
            listOf(
                Block.Heading("Chapter One", 1),
                Block.Paragraph("First paragraph."),
                Block.Heading("Chapter Two", 2),
                Block.Paragraph("Second."),
            ),
            book.blocks,
        )
    }

    @Test
    fun `skips non-text content, footnote markers and non-linear documents`() {
        val book =
            parse(
                packageDocument(items = listOf("cover" to "cover.xhtml", "text" to "text.xhtml"), spine = listOf("cover!", "text")),
                "OEBPS/cover.xhtml" to xhtml("<p>Cover page</p>"),
                "OEBPS/text.xhtml" to
                    xhtml(
                        """
                        <style>p { color: red; }</style>
                        <p>Word<sup><a href="#n1">1</a></sup> and <em>emphasis</em>,<br/>then&#160;more.</p>
                        <p><img src="x.png" alt="picture"/></p>
                        <aside epub:type="footnote"><p>A footnote.</p></aside>
                        """,
                    ),
            )

        assertEquals(listOf(Block.Paragraph("Word and emphasis, then more.")), book.blocks)
    }

    @Test
    fun `text mixed with nested blocks keeps its order`() {
        val book =
            parse(
                packageDocument(items = listOf("text" to "text.xhtml"), spine = listOf("text")),
                "OEBPS/text.xhtml" to xhtml("<div>Before <blockquote><p>Quoted.</p></blockquote> after.</div>"),
            )

        assertEquals(listOf(Block.Paragraph("Before"), Block.Paragraph("Quoted."), Block.Paragraph("after.")), book.blocks)
    }

    @Test
    fun `table of contents targets become headings`() {
        val nav =
            xhtml(
                """
                <nav epub:type="toc"><ol>
                  <li><a href="one.xhtml">Chapter I. The Beginning</a></li>
                  <li><a href="two.xhtml">Chapter II. The Middle</a>
                    <ol><li><a href="two.xhtml#part">A Part</a></li></ol>
                  </li>
                </ol></nav>
                """,
            )
        val book =
            parse(
                packageDocument(
                    items = listOf("nav" to "nav.xhtml", "one" to "one.xhtml", "two" to "two.xhtml"),
                    spine = listOf("one", "two"),
                    nav = "nav",
                ),
                "OEBPS/nav.xhtml" to nav,
                // A styled paragraph as the title, and a document with no title at all.
                "OEBPS/one.xhtml" to xhtml("""<p class="title">CHAPTER I</p><p>It began.</p>"""),
                "OEBPS/two.xhtml" to xhtml("""<p>It went on.</p><section id="part"><p>Deeper.</p></section>"""),
            )

        assertEquals(
            listOf(
                Block.Heading("CHAPTER I", 1),
                Block.Paragraph("It began."),
                Block.Heading("Chapter II. The Middle", 1),
                Block.Paragraph("It went on."),
                Block.Heading("A Part", 2),
                Block.Paragraph("Deeper."),
            ),
            book.blocks,
        )
    }

    @Test
    fun `reads an EPUB 2 table of contents and resolves relative paths`() {
        val ncx =
            """
            <ncx xmlns="http://www.daisy.org/z3986/2005/ncx/"><navMap>
              <navPoint><navLabel><text>Opening</text></navLabel><content src="text/chapter%201.xhtml#start"/></navPoint>
            </navMap></ncx>
            """
        val book =
            parse(
                packageDocument(
                    items = listOf("ncx" to "toc.ncx", "one" to "text/chapter%201.xhtml"),
                    spine = listOf("one"),
                    ncx = "ncx",
                ),
                "OEBPS/toc.ncx" to ncx,
                "OEBPS/text/chapter 1.xhtml" to xhtml("""<p id="start">Opening</p><p>Text.</p>"""),
            )

        assertEquals(listOf(Block.Heading("Opening", 1), Block.Paragraph("Text.")), book.blocks)
    }

    @Test
    fun `heading groups and captions`() {
        val book =
            parse(
                packageDocument(items = listOf("text" to "text.xhtml"), spine = listOf("text")),
                "OEBPS/text.xhtml" to
                    xhtml(
                        """
                        <hgroup><h2>I</h2><p>Down the Rabbit-Hole</p></hgroup>
                        <h2><img src="x.png"/><span class="caption">A picture.</span><br/>CHAPTER II.</h2>
                        <figure><img src="y.png"/><figcaption>Another picture.</figcaption></figure>
                        """,
                    ),
            )

        assertEquals(listOf(Block.Heading("I: Down the Rabbit-Hole", 2), Block.Heading("CHAPTER II.", 2)), book.blocks)
    }

    @Test
    fun `missing metadata is left empty`() {
        val book =
            parse(
                packageDocument(items = listOf("text" to "text.xhtml"), spine = listOf("text")),
                "OEBPS/text.xhtml" to xhtml("<p>Text.</p>"),
            )

        assertEquals("", book.title)
        assertNull(book.author)
    }

    @Test(expected = BookFormatException::class)
    fun `rejects files that are not EPUBs`() {
        EpubParser.parse(ByteArrayInputStream("%PDF-1.7 not a zip".toByteArray()))
    }

    private fun parse(vararg files: Pair<String, String>): Book {
        val bytes = ByteArrayOutputStream()
        ZipOutputStream(bytes).use { zip ->
            val all = listOf("mimetype" to "application/epub+zip", "META-INF/container.xml" to CONTAINER) + files
            for ((name, content) in all) {
                zip.putNextEntry(ZipEntry(name))
                zip.write(content.toByteArray())
                zip.closeEntry()
            }
        }
        return EpubParser.parse(ByteArrayInputStream(bytes.toByteArray()))
    }

    /** A package document in OEBPS/. A spine entry ending in "!" is non-linear. */
    private fun packageDocument(
        metadata: String = "",
        items: List<Pair<String, String>>,
        spine: List<String>,
        nav: String? = null,
        ncx: String? = null,
    ): Pair<String, String> {
        val manifest =
            items.joinToString("") { (id, href) ->
                val type =
                    when {
                        href.endsWith(".ncx") -> "application/x-dtbncx+xml"
                        else -> "application/xhtml+xml"
                    }
                val properties = if (id == nav) """ properties="nav"""" else ""
                """<item id="$id" href="$href" media-type="$type"$properties/>"""
            }
        val itemRefs =
            spine.joinToString("") {
                if (it.endsWith("!")) """<itemref idref="${it.dropLast(1)}" linear="no"/>""" else """<itemref idref="$it"/>"""
            }
        val tocAttribute = if (ncx != null) """ toc="$ncx"""" else ""
        return "OEBPS/content.opf" to
            """
            <?xml version="1.0" encoding="UTF-8"?>
            <package xmlns="http://www.idpf.org/2007/opf" version="3.0">
              <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">$metadata</metadata>
              <manifest>$manifest</manifest>
              <spine$tocAttribute>$itemRefs</spine>
            </package>
            """.trimIndent()
    }

    private fun xhtml(body: String) = """
        <?xml version="1.0" encoding="UTF-8"?>
        <html xmlns="http://www.w3.org/1999/xhtml" xmlns:epub="http://www.idpf.org/2007/ops">
        <head><title>Ignored</title></head>
        <body>$body</body>
        </html>
    """.trimIndent()

    private companion object {
        const val CONTAINER =
            """<?xml version="1.0"?>
<container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
  <rootfiles><rootfile full-path="OEBPS/content.opf" media-type="application/oebps-package+xml"/></rootfiles>
</container>"""
    }
}
