package io.github.nanopenguin.lesto.book

import com.tom_roush.pdfbox.io.MemoryUsageSetting
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.interactive.action.PDActionGoTo
import com.tom_roush.pdfbox.pdmodel.interactive.documentnavigation.destination.PDNamedDestination
import com.tom_roush.pdfbox.pdmodel.interactive.documentnavigation.destination.PDPageDestination
import com.tom_roush.pdfbox.pdmodel.interactive.documentnavigation.destination.PDPageXYZDestination
import com.tom_roush.pdfbox.pdmodel.interactive.documentnavigation.outline.PDOutlineNode
import com.tom_roush.pdfbox.text.PDFTextStripper
import com.tom_roush.pdfbox.text.TextPosition
import java.io.File
import java.io.InputStream
import java.io.Writer

/** Larger documents are buffered in temporary files instead of memory while they are read. */
private const val MAX_MEMORY_BYTES = 32L * 1024 * 1024

/** Deeper outline levels are left out; they are rarely chapters. */
private const val MAX_OUTLINE_DEPTH = 6

/** Also stops damaged outlines whose entries point back at each other. */
private const val MAX_OUTLINE_ITEMS = 10_000

/** Metadata titles that are really file names, as some programs write them. */
private val FileName = Regex("""(?i).*\.(pdf|docx?|odt|rtf|tex|txt|indd|pages)$""")

/**
 * Reads the text layer of a PDF with PdfBox, line by line with font sizes and positions, plus the
 * outline and metadata. [PdfLayout] then infers headings and paragraphs from these.
 */
object PdfParser {
    fun parse(
        input: InputStream,
        scratchDirectory: File,
        onProgress: (Float) -> Unit,
    ): Book {
        scratchDirectory.mkdirs()
        val memory = MemoryUsageSetting.setupMixed(MAX_MEMORY_BYTES).setTempDir(scratchDirectory)
        return PDDocument.load(input, memory).use { document ->
            val pages = LineCollector(onProgress).collect(document)
            val information = document.documentInformation
            Book(
                title = information.title?.let(::cleanText)?.takeUnless { FileName.matches(it) }.orEmpty(),
                author = information.author?.let(::cleanText)?.ifEmpty { null },
                blocks = PdfLayout.blocks(pages, readOutline(document)),
            )
        }
    }
}

/** Collects the lines PdfBox finds on each page, in reading order. */
private class LineCollector(
    private val onProgress: (Float) -> Unit,
) : PDFTextStripper() {
    private val pages = mutableListOf<List<PdfLine>>()
    private var lines = mutableListOf<PdfLine>()
    private val text = StringBuilder()
    private val positions = mutableListOf<TextPosition>()
    private var pageCount = 1

    fun collect(document: PDDocument): List<List<PdfLine>> {
        pageCount = document.numberOfPages.coerceAtLeast(1)
        writeText(document, NullWriter)
        return pages
    }

    override fun startPage(page: PDPage) {
        lines = mutableListOf()
        super.startPage(page)
    }

    override fun writeString(
        text: String,
        textPositions: List<TextPosition>,
    ) {
        // Rotated text is usually a margin note or stamp, like arXiv's identifier, not part of the text.
        if (textPositions.none { it.dir == 0f }) return
        this.text.append(text)
        positions += textPositions
    }

    override fun writeWordSeparator() {
        text.append(' ')
    }

    override fun writeLineSeparator() = endLine()

    // PdfBox separates some lines as paragraphs instead; the layout decides on paragraphs itself.
    override fun writeParagraphStart() {
        endLine()
        super.writeParagraphStart()
    }

    override fun writeParagraphEnd() {
        endLine()
        super.writeParagraphEnd()
    }

    override fun endPage(page: PDPage) {
        endLine()
        pages += lines
        onProgress(currentPageNo.toFloat() / pageCount)
        super.endPage(page)
    }

    private fun endLine() {
        if (positions.isNotEmpty()) {
            lines +=
                PdfLine(
                    text = text.toString(),
                    // The scale includes the page's transformations, unlike the nominal font size.
                    fontSize = median(positions.map { it.yScale }),
                    left = positions.minOf { it.xDirAdj },
                    right = positions.maxOf { it.xDirAdj + it.widthDirAdj },
                    baseline = median(positions.map { it.yDirAdj }),
                )
        }
        text.clear()
        positions.clear()
    }

    private fun median(values: List<Float>) = values.sorted()[values.size / 2]
}

private object NullWriter : Writer() {
    override fun write(
        buffer: CharArray,
        offset: Int,
        length: Int,
    ) = Unit

    override fun flush() = Unit

    override fun close() = Unit
}

private fun readOutline(document: PDDocument): List<PdfOutlineEntry> {
    val outline = document.documentCatalog.documentOutline ?: return emptyList()
    val entries = mutableListOf<PdfOutlineEntry>()
    var visited = 0

    fun visit(
        node: PDOutlineNode,
        level: Int,
    ) {
        for (item in node.children()) {
            if (++visited > MAX_OUTLINE_ITEMS) return
            val destination = runCatching { item.destination ?: (item.action as? PDActionGoTo)?.destination }.getOrNull()
            val pageDestination =
                when (destination) {
                    is PDNamedDestination -> runCatching { document.documentCatalog.findNamedDestinationPage(destination) }.getOrNull()
                    else -> destination as? PDPageDestination
                }
            val pageIndex = pageDestination?.page?.let { document.pages.indexOf(it) } ?: pageDestination?.pageNumber ?: -1
            if (pageIndex in 0 until document.numberOfPages && !item.title.isNullOrBlank()) {
                val page = document.getPage(pageIndex)
                // PDF heights count up from the bottom; the text positions count down from the top.
                val top = (pageDestination as? PDPageXYZDestination)?.top?.takeIf { it >= 0 }?.let { page.cropBox.upperRightY - it }
                entries += PdfOutlineEntry(item.title, level, pageIndex, top)
            }
            if (level < MAX_OUTLINE_DEPTH) visit(item, level + 1)
        }
    }
    visit(outline, level = 1)
    return entries
}
