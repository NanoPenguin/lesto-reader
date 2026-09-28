package io.github.nanopenguin.pace.reader

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import kotlin.math.abs

/** Extra space above chapter headings, as a fraction of the line height. */
private const val HEADING_GAP = 0.8f

/**
 * Horizontal layout of the paused page. All lines start at a shared `scrollX`, like a page that
 * is panned sideways, except lines that would end before the focal point: those are right-aligned
 * so that their last word sits on it.
 */
class PageGeometry(
    private val measurer: TextMeasurer,
    style: TextStyle,
    /** x of the focal point on screen. */
    val focalX: Float,
    val lineHeight: Float,
) {
    /** Extra space above chapter headings. */
    private val headingGap = lineHeight * HEADING_GAP

    /** Vertical position of each line of [page] relative to the current one. */
    fun lineOffsets(page: TextPage): FloatArray = page.lineOffsets(lineHeight, headingGap)

    private val bodyStyle = style
    private val headingStyle = style.copy(fontWeight = FontWeight.SemiBold)

    fun layout(line: PageLine): TextLayoutResult = layout(AnnotatedString(line.text), line.isHeading)

    /** Lays out [text] as a line; styling spans must not change its width. */
    fun layout(
        text: AnnotatedString,
        isHeading: Boolean,
    ): TextLayoutResult = measurer.measure(text, if (isHeading) headingStyle else bodyStyle, softWrap = false, maxLines = 1)

    /** x of [word]'s anchor, relative to the start of its line. */
    fun anchorX(
        line: PageLine,
        word: Int,
    ): Float = layout(line).getBoundingBox(line.anchorChar(word)).center.x

    /** Where [line] starts on screen. */
    fun lineX(
        line: PageLine,
        scrollX: Float,
    ): Float = maxOf(scrollX, focalX - anchorX(line, line.words.lastIndex))

    /** The `scrollX` that puts [word] of [line] on the focal point. */
    fun scrollXFor(
        line: PageLine,
        word: Int,
    ): Float = focalX - anchorX(line, word)

    /** The word of [line] closest to the focal point at [scrollX]. */
    fun wordAt(
        line: PageLine,
        scrollX: Float,
    ): Int {
        val x = focalX - lineX(line, scrollX)
        return line.words.indices.minBy { abs(anchorX(line, it) - x) }
    }
}
