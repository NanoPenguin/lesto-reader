package io.github.nanopenguin.pace.book

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

/** A line of text on a PDF page. Positions are in points from the top-left corner of the page. */
data class PdfLine(
    val text: String,
    val fontSize: Float,
    val left: Float,
    val right: Float,
    val baseline: Float,
)

/** A PDF outline (bookmark) entry: a title pointing at a page, and at a height on it if known. */
data class PdfOutlineEntry(
    val title: String,
    val level: Int,
    val pageIndex: Int,
    val top: Float?,
)

/** Shorter outlines are often just a cover or a single link, not a table of contents. */
private const val MIN_OUTLINE_ENTRIES = 3

/** Headings are set at least this much larger than the body text. */
private const val HEADING_SIZE_RATIO = 1.15f

private const val MAX_HEADING_LENGTH = 120

/** More large lines in a row are display text, like an introduction set in a larger font. */
private const val MAX_HEADING_LINES = 3

/** Lines of one heading are at most this many of their font sizes apart. */
private const val HEADING_LINE_GAP_RATIO = 2.5f

/** Only this many lines at the top and at the bottom of a page can be headers, footers or page numbers. */
private const val EDGE_LINES = 2

/** A line repeated at a page edge on at least this many pages is a running header or footer. */
private const val MIN_RUNNING_PAGES = 3

/** A vertical gap this many times the usual line spacing starts a new paragraph. */
private const val PARAGRAPH_GAP_RATIO = 1.4f

private val PageNumber =
    Regex(
        """(?:page\s+)?[(\[\-–—]?\s*(?:\d{1,4}|m{0,3}(?:cm|cd|d?c{0,3})(?:xc|xl|l?x{0,3})(?:ix|iv|v?i{0,3}))\s*[)\]\-–—]?(?:\s*(?:of|/)\s*\d{1,4})?""",
        RegexOption.IGNORE_CASE,
    )

private val Digits = Regex("""\d+""")

private val LeadingNumber = Regex("""^(\d{1,4})\b""")

private val TrailingNumber = Regex("""\b(\d{1,4})$""")

private val Word = Regex("""\p{L}{3,}""")

private const val SENTENCE_ENDS = ".!?:\"”’)"

/**
 * Infers headings and paragraphs from where a PDF draws its text. Headings come from the outline,
 * or from larger fonts without one; using both would find most headings twice.
 */
object PdfLayout {
    fun blocks(
        pages: List<List<PdfLine>>,
        outline: List<PdfOutlineEntry>,
    ): List<Block> {
        val cleaned = pages.map { page -> page.map { it.copy(text = cleanLine(it.text)) }.filter { it.text.isNotEmpty() } }
        val bodySize = bodyFontSize(cleaned.flatten()) ?: return emptyList()
        val headingSize = bodySize * HEADING_SIZE_RATIO
        val content = removePageFurniture(cleaned, headingSize)
        val bodyLines = content.map { page -> page.filter { abs(it.fontSize - bodySize) <= bodySize * 0.1f } }
        val metrics =
            Metrics(
                bodySize = bodySize,
                lineSpacing = lineSpacing(bodyLines, bodySize) ?: (bodySize * 1.2f),
                fullWidth = fullLineWidth(bodyLines.flatten()) ?: 0f,
            )

        val entries = outline.filter { it.pageIndex in content.indices }.distinctBy { it.title to it.pageIndex }
        val items =
            if (entries.size >= MIN_OUTLINE_ENTRIES) {
                val items = content.map { page -> page.map<PdfLine, PageItem>(::LineItem).toMutableList() }
                entries.forEach { applyOutlineEntry(items[it.pageIndex], it) }
                items
            } else {
                rankHeadings(content.map { groupHeadings(it, headingSize) })
            }
        return assemble(items, metrics)
    }
}

private class Metrics(
    val bodySize: Float,
    /** The usual distance between the baselines of consecutive body lines. */
    val lineSpacing: Float,
    /** The width of a full line of body text; shorter lines may end a paragraph. */
    val fullWidth: Float,
)

private sealed interface PageItem {
    val baseline: Float
}

private data class LineItem(
    val line: PdfLine,
) : PageItem {
    override val baseline get() = line.baseline
}

private data class HeadingItem(
    val text: String,
    val fontSize: Float,
    override val baseline: Float,
    val level: Int = 1,
) : PageItem

/** Keeps a soft hyphen at a line end as a hyphen, so the word is joined with the next line. */
private fun cleanLine(text: String): String {
    val trimmed = text.trimEnd()
    return cleanText(if (trimmed.endsWith('­')) trimmed.dropLast(1) + "-" else trimmed)
}

/** The font size most of the text is set in. */
private fun bodyFontSize(lines: List<PdfLine>): Float? = lines
    .groupBy { (it.fontSize * 2).roundToInt() }
    .maxByOrNull { (_, group) -> group.sumOf { it.text.length } }
    ?.let { (halfPoints, _) -> halfPoints / 2f }
    ?.takeIf { it > 0 }

/** The median distance between consecutive lines; larger gaps separate paragraphs or columns. */
private fun lineSpacing(
    pages: List<List<PdfLine>>,
    bodySize: Float,
): Float? {
    val gaps = pages.flatMap { page -> page.zipWithNext { above, below -> below.baseline - above.baseline } }.filter { it > 0 && it < bodySize * 3 }
    return gaps.sorted().getOrNull(gaps.size / 2)
}

private fun fullLineWidth(lines: List<PdfLine>): Float? {
    val widths = lines.map { it.right - it.left }.sorted()
    return widths.getOrNull(widths.size * 3 / 4)
}

/**
 * Drops page numbers and running headers and footers: lines at a page edge that are repeated on
 * several pages, or that start or end with the page's printed number.
 */
private fun removePageFurniture(
    pages: List<List<PdfLine>>,
    headingSize: Float,
): List<List<PdfLine>> {
    // Edges are found by position: some PDFs draw their headers and footers after the text.
    fun edgeLines(page: List<PdfLine>): Set<PdfLine> {
        val byHeight = page.sortedBy { it.baseline }
        return (byHeight.take(EDGE_LINES) + byHeight.takeLast(EDGE_LINES)).toSet()
    }

    fun runningKey(line: PdfLine) = line.text.lowercase().replace(Digits, "#")

    // Headings are exempt: chapters often open at the same height, with the same words and increasing numbers.
    val edges = pages.map { page -> edgeLines(page).filter { it.fontSize < headingSize }.toSet() }
    val pageCounts = mutableMapOf<String, Int>()
    for (pageEdges in edges) {
        pageEdges.map(::runningKey).toSet().forEach { pageCounts[it] = (pageCounts[it] ?: 0) + 1 }
    }
    val numberOffset = pageNumberOffset(edges)
    return pages.mapIndexed { index, page ->
        page.filterNot { line ->
            val isRunning = (pageCounts[runningKey(line)] ?: 0) >= MIN_RUNNING_PAGES
            val isNumbered = numberOffset != null && edgeNumbers(line.text).any { it - index == numberOffset }
            line in edges[index] && (PageNumber.matches(line.text) || isRunning || isNumbered)
        }
    }
}

/** The difference between printed page numbers and page indices, if the pages show numbers at their edges. */
private fun pageNumberOffset(edges: List<Set<PdfLine>>): Int? {
    val pageCounts = mutableMapOf<Int, Int>()
    for ((index, pageEdges) in edges.withIndex()) {
        pageEdges.flatMap { edgeNumbers(it.text) }.map { it - index }.toSet().forEach { pageCounts[it] = (pageCounts[it] ?: 0) + 1 }
    }
    return pageCounts.maxByOrNull { it.value }?.takeIf { it.value >= MIN_RUNNING_PAGES }?.key
}

/** Numbers at the start or end of a line, where headers and footers put the page number. */
private fun edgeNumbers(text: String): List<Int> = listOfNotNull(
    LeadingNumber.find(text)?.groupValues?.get(1)?.toInt(),
    TrailingNumber.find(text)?.groupValues?.get(1)?.toInt(),
)

/** Turns short runs of large lines into headings; stacked ones, like "Chapter 1" over its title, become one. */
private fun groupHeadings(
    page: List<PdfLine>,
    headingSize: Float,
): List<PageItem> {
    // Large numbers and codes, as in figures, are not headings.
    fun isLarge(line: PdfLine) = line.fontSize >= headingSize && Word.containsMatchIn(line.text)

    val items = mutableListOf<PageItem>()
    var index = 0
    while (index < page.size) {
        if (!isLarge(page[index])) {
            items += LineItem(page[index++])
            continue
        }
        var end = index + 1
        while (end < page.size && isLarge(page[end])) {
            val gap = page[end].baseline - page[end - 1].baseline
            if (gap <= 0 || gap > max(page[end].fontSize, page[end - 1].fontSize) * HEADING_LINE_GAP_RATIO) break
            end++
        }
        val run = page.subList(index, end)
        val text =
            run.drop(1).fold(run.first().text) { text, line ->
                // A wrapped heading continues with a space; a differently sized line is a separate part.
                val sameSize = abs(line.fontSize - run.first().fontSize) <= run.first().fontSize * 0.05f
                text + (if (sameSize || text.last() in ".:") " " else ": ") + line.text
            }
        if (run.size <= MAX_HEADING_LINES && text.length <= MAX_HEADING_LENGTH) {
            items += HeadingItem(text, run.maxOf { it.fontSize }, run.first().baseline)
        } else {
            run.forEach { items += LineItem(it) }
        }
        index = end
    }
    return items
}

/** Ranks headings found by size into levels: the largest font is level 1. */
private fun rankHeadings(pages: List<List<PageItem>>): List<List<PageItem>> {
    val sizes = pages.flatten().filterIsInstance<HeadingItem>().map { it.fontSize.roundToInt() }.distinct().sortedDescending()
    return pages.map { items ->
        items.map { item ->
            if (item is HeadingItem) item.copy(level = (sizes.indexOf(item.fontSize.roundToInt()) + 1).coerceAtMost(6)) else item
        }
    }
}

/**
 * Turns the lines showing the outline entry's title into its heading, choosing the lines nearest
 * to where the entry points. If the title is not printed, it is inserted there.
 */
private fun applyOutlineEntry(
    items: MutableList<PageItem>,
    entry: PdfOutlineEntry,
) {
    val title = cleanText(entry.title)
    val key = matchKey(title)
    val matches = if (key.isEmpty()) emptyList() else items.indices.mapNotNull { titleLinesAt(items, it, key) }
    val match = if (entry.top == null) matches.firstOrNull() else matches.minByOrNull { abs(items[it.first].baseline - entry.top) }
    if (match != null) {
        val first = (items[match.first] as LineItem).line
        repeat(match.count()) { items.removeAt(match.first) }
        items.add(match.first, HeadingItem(title, first.fontSize, first.baseline, entry.level))
        return
    }
    // The destination is the top of the title; its baseline lies a little lower.
    val index = entry.top?.let { top -> items.indexOfFirst { it.baseline >= top - 2f }.takeIf { it >= 0 } } ?: 0
    val baseline = items.getOrNull(index)?.baseline ?: Float.MAX_VALUE
    items.add(index, HeadingItem(title, fontSize = 0f, baseline, entry.level))
}

/** The lines starting at [start] that together spell the title with [key], if any; titles may wrap. */
private fun titleLinesAt(
    items: List<PageItem>,
    start: Int,
    key: String,
): IntRange? {
    var matched = ""
    for (end in start until items.size) {
        val lineKey = matchKey((items[end] as? LineItem)?.line?.text ?: return null)
        if (lineKey.isEmpty() && end == start) return null
        matched += lineKey
        if (matched == key) return start..end
        if (!key.startsWith(matched)) return null
    }
    return null
}

/** Titles are compared by letters only: outlines and pages differ in case, punctuation and numbering. */
private fun matchKey(text: String) = text.lowercase().filter { it.isLetter() }

private fun assemble(
    pages: List<List<PageItem>>,
    metrics: Metrics,
): List<Block> {
    val blocks = mutableListOf<Block>()
    var paragraph = ""
    var previous: PdfLine? = null
    var previousPage = -1

    fun endParagraph() {
        if (paragraph.isNotEmpty()) blocks += Block.Paragraph(paragraph)
        paragraph = ""
    }

    for ((pageIndex, items) in pages.withIndex()) {
        for ((index, item) in items.withIndex()) {
            when (item) {
                is HeadingItem -> {
                    endParagraph()
                    blocks += Block.Heading(item.text, item.level)
                    previous = null
                }

                is LineItem -> {
                    val last = previous
                    val next = (items.getOrNull(index + 1) as? LineItem)?.line
                    if (last == null || startsParagraph(last, item.line, next, samePage = pageIndex == previousPage, metrics)) {
                        endParagraph()
                        paragraph = item.line.text
                    } else {
                        paragraph = joinLines(paragraph, item.line.text)
                    }
                    previous = item.line
                    previousPage = pageIndex
                }
            }
        }
    }
    endParagraph()
    return blocks
}

private fun startsParagraph(
    previous: PdfLine,
    line: PdfLine,
    next: PdfLine?,
    samePage: Boolean,
    metrics: Metrics,
): Boolean {
    val em = metrics.bodySize
    if (abs(line.fontSize - previous.fontSize) > max(line.fontSize, previous.fontSize) * 0.1f) return true
    // A line further up on the same page starts a new column, which usually continues the text.
    val gap = line.baseline - previous.baseline
    if (samePage && gap > metrics.lineSpacing * PARAGRAPH_GAP_RATIO) return true
    val endsSentence = previous.text.last() in SENTENCE_ENDS
    // An indented first line is followed by one at the margin again; in a hanging indent, the next line stays in.
    val indent = line.left - previous.left
    val nextReturns = next == null || next.baseline <= line.baseline || next.left < line.left - em * 0.8f
    if (samePage && gap > 0 && endsSentence && indent > em * 0.8f && indent < em * 8 && nextReturns) return true
    val endsShort = previous.right - previous.left < metrics.fullWidth - em * 3
    return endsShort && endsSentence
}

/** Joins two lines of a paragraph, undoing hyphenation at the line break. */
private fun joinLines(
    text: String,
    next: String,
): String {
    val hyphenated = text.length >= 2 && text.last() == '-' && text[text.length - 2].isLetter() && next.first().isLowerCase()
    return when {
        hyphenated -> text.dropLast(1) + next
        text.last() == '—' || next.first() == '—' -> text + next
        else -> "$text $next"
    }
}
