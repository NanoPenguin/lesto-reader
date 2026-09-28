package io.github.nanopenguin.lesto.book

import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode
import org.jsoup.parser.Parser
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.net.URLDecoder
import java.util.zip.ZipInputStream

/**
 * Reads EPUB 2 and 3 books: metadata, the reading order, and the text as headings and paragraphs.
 *
 * Headings are `h1`–`h6` elements plus everything the table of contents points at, which catches
 * books that style chapter titles as ordinary paragraphs.
 */
object EpubParser {
    fun parse(input: InputStream): Book {
        val files = readTextEntries(input)
        val packagePath =
            files["META-INF/container.xml"]?.let { parseXml(it).elementsNamed("rootfile").firstOrNull()?.attr("full-path") }
                ?: throw BookFormatException("No package document")
        val packageDocument = parseXml(files[packagePath] ?: throw BookFormatException("Missing $packagePath"))
        val packageDir = packagePath.substringBeforeLast('/', missingDelimiterValue = "")

        val manifest =
            packageDocument.elementsNamed("item").associate {
                it.attr("id") to ManifestItem(resolve(packageDir, it.attr("href")).path, it.attr("media-type"), it.attr("properties"))
            }
        val readingOrder =
            packageDocument.elementsNamed("itemref")
                .filter { it.attr("linear") != "no" }
                .mapNotNull { manifest[it.attr("idref")] }
                .filter { "html" in it.mediaType }
        val toc = readTableOfContents(manifest.values, files)

        val blocks = mutableListOf<Block>()
        for (item in readingOrder) {
            val bytes = files[item.path] ?: continue
            DocumentReader(toc[item.path].orEmpty(), blocks).read(parseXhtml(bytes).bodyElement())
        }

        return Book(
            title = packageDocument.elementsNamed("title").firstOrNull()?.text()?.let(::cleanText).orEmpty(),
            author = packageDocument.elementsNamed("creator").firstOrNull()?.text()?.let(::cleanText)?.ifEmpty { null },
            blocks = blocks,
        )
    }
}

/** Only these entries are read; images, fonts and styles are skipped. */
private val TextExtensions = setOf("xhtml", "html", "htm", "xml", "opf", "ncx")

/** Protects against archives that expand to absurd sizes. */
private const val MAX_TEXT_BYTES = 64L * 1024 * 1024

private class ManifestItem(
    val path: String,
    val mediaType: String,
    val properties: String,
)

/** A table of contents entry, as the heading it marks. */
private class TocEntry(
    val label: String,
    val level: Int,
)

private data class Target(
    val path: String,
    val fragment: String,
)

private fun readTextEntries(input: InputStream): Map<String, ByteArray> {
    val files = mutableMapOf<String, ByteArray>()
    var total = 0L
    ZipInputStream(input).use { zip ->
        while (true) {
            val entry = zip.nextEntry ?: break
            if (entry.isDirectory || entry.name.substringAfterLast('.').lowercase() !in TextExtensions) continue
            val bytes = zip.readBytes()
            total += bytes.size
            if (total > MAX_TEXT_BYTES) throw BookFormatException("Book is too large")
            files[entry.name] = bytes
        }
    }
    if (files.isEmpty()) throw BookFormatException("Not an EPUB")
    return files
}

private fun parseXml(bytes: ByteArray): Document = Jsoup.parse(ByteArrayInputStream(bytes), "UTF-8", "", Parser.xmlParser())

/**
 * Content documents are XHTML, so they are parsed as XML: an HTML parser would treat `<a id="x"/>`
 * as an open tag swallowing what follows. Documents that are really HTML fall back to the HTML parser.
 */
private fun parseXhtml(bytes: ByteArray): Document {
    val xml = parseXml(bytes)
    return if (xml.selectFirst("body") != null) xml else Jsoup.parse(ByteArrayInputStream(bytes), null, "")
}

/** The body element; [Document.body] is meant for documents parsed as HTML. */
private fun Document.bodyElement(): Element = selectFirst("body") ?: this

/** Elements with the given local name, whatever their namespace prefix (`dc:title`, `opf:item`, ...). */
private fun Element.elementsNamed(name: String): List<Element> = getAllElements().filter { it.tagName().substringAfter(':') == name }

/** Resolves a link relative to [baseDir] into a path inside the archive and a fragment. */
private fun resolve(
    baseDir: String,
    href: String,
): Target {
    val path = URLDecoder.decode(href.substringBefore('#').replace("+", "%2B"), "UTF-8")
    val segments = ArrayDeque<String>()
    for (segment in (if (baseDir.isEmpty()) "" else "$baseDir/").plus(path).split('/')) {
        when (segment) {
            "", "." -> Unit
            ".." -> segments.removeLastOrNull()
            else -> segments.addLast(segment)
        }
    }
    return Target(segments.joinToString("/"), href.substringAfter('#', missingDelimiterValue = ""))
}

/** Table of contents entries by document path, then by fragment ("" for the start of a document). */
private fun readTableOfContents(
    manifest: Collection<ManifestItem>,
    files: Map<String, ByteArray>,
): Map<String, Map<String, TocEntry>> {
    val entries = mutableMapOf<String, MutableMap<String, TocEntry>>()
    fun add(
        target: Target,
        label: String,
        level: Int,
    ) {
        val text = cleanText(label)
        if (text.isNotEmpty()) entries.getOrPut(target.path) { mutableMapOf() }.putIfAbsent(target.fragment, TocEntry(text, level))
    }

    val nav = manifest.firstOrNull { "nav" in it.properties.split(' ') }
    val ncx = manifest.firstOrNull { it.mediaType == "application/x-dtbncx+xml" }
    when {
        nav != null && files[nav.path] != null -> {
            val navDir = nav.path.substringBeforeLast('/', missingDelimiterValue = "")
            val navElements = parseXhtml(files.getValue(nav.path)).select("nav")
            val toc = navElements.firstOrNull { it.attr("epub:type") == "toc" } ?: navElements.firstOrNull()

            fun walk(
                list: Element,
                level: Int,
            ) {
                for (item in list.children().filter { it.tagName() == "li" }) {
                    item.children().firstOrNull { it.tagName() == "a" }?.let { add(resolve(navDir, it.attr("href")), it.text(), level) }
                    item.children().filter { it.tagName() == "ol" }.forEach { walk(it, level + 1) }
                }
            }
            toc?.children()?.filter { it.tagName() == "ol" }?.forEach { walk(it, level = 1) }
        }

        ncx != null && files[ncx.path] != null -> {
            val ncxDir = ncx.path.substringBeforeLast('/', missingDelimiterValue = "")

            fun walk(
                parent: Element,
                level: Int,
            ) {
                for (point in parent.children().filter { it.tagName().substringAfter(':') == "navPoint" }) {
                    val label = point.elementsNamed("navLabel").firstOrNull()?.text().orEmpty()
                    val source = point.elementsNamed("content").firstOrNull()?.attr("src").orEmpty()
                    if (source.isNotEmpty()) add(resolve(ncxDir, source), label, level)
                    walk(point, level + 1)
                }
            }
            parseXml(files.getValue(ncx.path)).elementsNamed("navMap").firstOrNull()?.let { walk(it, level = 1) }
        }
    }
    return entries
}

/** Elements whose content is never read aloud: code, media, navigation, footnotes and the like. */
private val SkippedTags =
    setOf("head", "script", "style", "title", "svg", "math", "img", "audio", "video", "object", "iframe", "noscript", "nav", "rt", "rp")

private val BlockTags =
    setOf(
        "body", "div", "p", "section", "article", "header", "footer", "main", "aside", "blockquote", "hgroup", "address",
        "center", "ul", "ol", "li", "dl", "dt", "dd", "pre", "figure", "figcaption", "table", "thead", "tbody", "tfoot",
        "tr", "td", "th", "caption", "h1", "h2", "h3", "h4", "h5", "h6", "hr",
    )

/** Short paragraphs at a table of contents target are taken as the chapter title. */
private const val MAX_TITLE_WORDS = 12

/**
 * Turns the body of one document into blocks. A table of contents entry that points at a container
 * or at the start of the document stays pending until the first text: if that text looks like the
 * title it becomes the heading, otherwise the entry's label is inserted as the heading.
 */
private class DocumentReader(
    private val toc: Map<String, TocEntry>,
    private val out: MutableList<Block>,
) {
    private var pending: TocEntry? = toc[""]

    fun read(body: Element) {
        readChildren(body)
        pending?.let { addHeading("", it.level) }
    }

    private fun readChildren(element: Element) {
        val inline = StringBuilder()
        for (node in element.childNodes()) {
            when {
                node is TextNode -> inline.append(node.text())

                node !is Element || node.isSkipped() -> Unit

                node.tagName() == "br" -> inline.append(' ')

                node.tagName() in BlockTags || node.children().any { it.tagName() in BlockTags } -> {
                    addParagraph(inline.toString())
                    inline.clear()
                    readBlock(node)
                }

                else -> {
                    // Inline elements can carry the anchor a table of contents entry points at.
                    node.getAllElements().firstNotNullOfOrNull { it.tocEntry() }?.let { if (pending == null) pending = it }
                    inline.append(node.readableText())
                }
            }
        }
        addParagraph(inline.toString())
    }

    private fun readBlock(element: Element) {
        val target = element.tocEntry()
        val headingLevel = element.tagName().removePrefix("h").toIntOrNull()?.takeIf { element.tagName().length == 2 }
        when {
            headingLevel != null -> addHeading(element.readableText(), target?.level ?: headingLevel)

            // A heading with its subtitle, like <hgroup><h2>I</h2><p>Down the Rabbit-Hole</p></hgroup>.
            element.tagName() == "hgroup" -> {
                val parts = element.children().filterNot { it.isSkipped() }.map { cleanText(it.readableText()) }.filter { it.isNotEmpty() }
                val level = element.children().firstNotNullOfOrNull { it.tagName().removePrefix("h").toIntOrNull() } ?: 1
                addHeading(parts.joinToString(": "), target?.level ?: level)
            }

            target != null && element.children().none { it.tagName() in BlockTags } -> addHeading(element.readableText(), target.level)

            else -> {
                if (target != null) pending = target
                readChildren(element)
            }
        }
    }

    /** The table of contents entry pointing at this element's id, if any. */
    private fun Element.tocEntry(): TocEntry? = id().takeIf { it.isNotEmpty() }?.let(toc::get)

    private fun addHeading(
        text: String,
        level: Int,
    ) {
        // A heading made of an image has no text; the table of contents label names it instead.
        val title = cleanText(text).ifEmpty { pending?.label.orEmpty() }
        pending = null
        if (title.isNotEmpty()) out += Block.Heading(title, level)
    }

    private fun addParagraph(text: String) {
        val paragraph = cleanText(text)
        if (paragraph.isEmpty()) return
        val entry = pending
        if (entry != null) {
            pending = null
            if (paragraph.split(' ').size <= MAX_TITLE_WORDS && sameTitle(paragraph, entry.label)) {
                out += Block.Heading(paragraph, entry.level)
                return
            }
            out += Block.Heading(entry.label, entry.level)
        }
        out += Block.Paragraph(paragraph)
    }
}

/** The element's text without the parts that are not read, such as footnote markers and captions. */
private fun Element.readableText(): String {
    val text = StringBuilder()
    fun collect(node: Node) {
        when {
            node is TextNode -> text.append(node.text())
            node !is Element || node.isSkipped() -> Unit
            node.tagName() == "br" -> text.append(' ')
            else -> node.childNodes().forEach(::collect)
        }
    }
    childNodes().forEach(::collect)
    return text.toString()
}

private fun Element.isSkipped(): Boolean {
    val type = attr("epub:type")
    return tagName() in SkippedTags ||
        // Image captions interrupt the text, and in headings they are not part of the title.
        tagName() == "figcaption" ||
        hasClass("caption") ||
        "noteref" in type ||
        (tagName() == "aside" && "note" in type) ||
        // Footnote markers like <sup><a href="#note1">1</a></sup>.
        (tagName() == "sup" && children().size == 1 && child(0).tagName() == "a" && ownText().isBlank())
}

/** Whether a paragraph and a table of contents label name the same thing, e.g. "CHAPTER I" and "Chapter I. The Beginning". */
private fun sameTitle(
    a: String,
    b: String,
): Boolean {
    fun normalize(text: String) = text.lowercase().filter { it.isLetterOrDigit() || it == ' ' }.trim()
    val x = normalize(a)
    val y = normalize(b)
    return x.isNotEmpty() && y.isNotEmpty() && (x in y || y in x)
}
