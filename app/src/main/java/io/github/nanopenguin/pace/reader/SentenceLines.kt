package io.github.nanopenguin.pace.reader

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer

/** The rest of the current sentence is a little brighter than the sentences around it. */
private const val CURRENT_SENTENCE_ALPHA = 0.55f

/** Lines run across most of the width, so they only fade out close to the edges. */
private const val PAGE_EDGE_FADE = 0.12f

/**
 * The paused view: one sentence per line, like a page. The current sentence is on the middle
 * line with its current word at the focal point; the sentences around it are dimmed and fade out
 * towards the edges. All lines start at the same x, so moving along the current sentence pans the
 * whole page.
 *
 * @param verticalOffset how far the lines are dragged, read while drawing so dragging only redraws.
 */
@Composable
fun SentenceLines(
    page: TextPage,
    style: TextStyle,
    verticalOffset: () -> Float,
    modifier: Modifier = Modifier,
) {
    val measurer = rememberTextMeasurer(cacheSize = 16)
    val ink = MaterialTheme.colorScheme.onSurface
    val accent = MaterialTheme.colorScheme.primary
    val guideColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
    val current = currentLine(page, ink, accent)
    val lineStyle = style.copy(color = ink.copy(alpha = CONTEXT_ALPHA))

    Canvas(
        modifier =
        modifier
            .fillMaxSize()
            // Offscreen so the edge fades only mask what this canvas drew.
            .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
            .semantics { contentDescription = current.text.text },
    ) {
        val focalX = size.width * FOCAL_LINE
        val centerY = size.height / 2
        val lineHeight = style.fontSize.toPx() * LINE_SPACING
        val offset = verticalOffset()

        val currentLayout =
            measurer.measure(current.text, style.copy(color = ink.copy(alpha = CURRENT_SENTENCE_ALPHA)), softWrap = false, maxLines = 1)
        val left = focalX - currentLayout.focalCenter(current.focalChar)
        val textHeight = currentLayout.size.height

        fun drawLine(
            text: AnnotatedString,
            lineIndex: Int,
            lineStyle: TextStyle,
        ) {
            val top = centerY + offset + lineIndex * lineHeight - textHeight / 2
            if (top + textHeight < 0 || top > size.height) return
            drawText(measurer.measure(text, lineStyle, softWrap = false, maxLines = 1), topLeft = Offset(left, top))
        }

        page.before.asReversed().forEachIndexed { i, line -> drawLine(AnnotatedString(line), -(i + 1), lineStyle) }
        page.after.forEachIndexed { i, line -> drawLine(AnnotatedString(line), i + 1, lineStyle) }
        drawText(currentLayout, topLeft = Offset(left, centerY + offset - textHeight / 2))

        fadeHorizontalEdges(PAGE_EDGE_FADE)
        fadeVerticalEdges()

        if (!current.isHeading) {
            drawFocalGuides(focalX, centerY - textHeight / 2, centerY + textHeight / 2, guideColor)
        }
    }
}

private class CurrentLine(
    val text: AnnotatedString,
    /** Character the line is aligned on. For a heading, the focal letter of its first word, unmarked. */
    val focalChar: Int,
    val isHeading: Boolean,
)

private fun currentLine(
    page: TextPage,
    ink: Color,
    accent: Color,
): CurrentLine {
    if (page.currentWord == null) {
        val heading =
            buildAnnotatedString {
                pushStyle(SpanStyle(color = ink, fontWeight = FontWeight.SemiBold))
                append(page.current.joinToString(" "))
            }
        return CurrentLine(heading, focalIndex(page.current.firstOrNull().orEmpty().substringBefore(' ')), isHeading = true)
    }

    var focalChar = 0
    val text =
        buildAnnotatedString {
            page.current.forEachIndexed { index, word ->
                if (index > 0) append(' ')
                val start = length
                append(word)
                if (index == page.currentWord && word.isNotEmpty()) {
                    val focal = start + focalIndex(word)
                    addStyle(SpanStyle(color = ink), start, length)
                    addStyle(SpanStyle(color = accent), focal, focal + 1)
                    focalChar = focal
                }
            }
        }
    return CurrentLine(text, focalChar, isHeading = false)
}

/** Lines fade out towards the top, and towards the controls at the bottom. */
private fun DrawScope.fadeVerticalEdges() {
    drawRect(
        brush =
        Brush.verticalGradient(
            0f to Color.Transparent,
            0.3f to Color.Black,
            0.62f to Color.Black,
            0.8f to Color.Transparent,
        ),
        blendMode = BlendMode.DstIn,
    )
}
