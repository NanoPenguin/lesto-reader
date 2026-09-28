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
 * Layout of the paused page, which moves as one sheet: all lines start at a shared `scrollX`, and
 * lie a fixed distance apart.
 */
class PageGeometry(
    private val measurer: TextMeasurer,
    private val bodyStyle: TextStyle,
    /** x of the focal point on screen. */
    val focalX: Float,
    val lineHeight: Float,
) {
    private val headingStyle = bodyStyle.copy(fontWeight = FontWeight.SemiBold)
    private val headingGap = lineHeight * HEADING_GAP

    /** Vertical position of each line of [page] relative to the current one. */
    fun lineOffsets(page: TextPage): FloatArray = page.lineOffsets(lineHeight, headingGap)

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

    /** The `scrollX` that puts [word] of [line] on the focal point. */
    fun scrollXFor(
        line: PageLine,
        word: Int,
    ): Float = focalX - anchorX(line, word)

    /**
     * The word of [line] closest to the focal point at [scrollX]. Beyond either end of the line,
     * that is its first or last word.
     */
    fun wordAt(
        line: PageLine,
        scrollX: Float,
    ): Int {
        val x = focalX - scrollX
        return line.words.indices.minBy { abs(anchorX(line, it) - x) }
    }
}
