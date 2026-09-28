package io.github.nanopenguin.pace.reader

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
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
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.drawText

/** The rest of the current sentence is a little brighter than the lines around it. */
private const val CURRENT_LINE_ALPHA = 0.55f

/** Headings stand out from the lines around them, as chapter separators. */
private const val HEADING_LINE_ALPHA = 0.7f

/** Lines run across most of the width, so they only fade out close to the edges. */
private const val PAGE_EDGE_FADE = 0.12f

/**
 * The paused view: one sentence per line, like a page. The current word sits at the focal point;
 * the lines around it are dimmed and fade out towards the edges. Positions come from [geometry]
 * and [scroll], which are read while drawing so scrolling only redraws.
 */
@Composable
fun SentenceLines(
    page: TextPage,
    geometry: PageGeometry,
    scroll: PageScroll,
    modifier: Modifier = Modifier,
) {
    val ink = MaterialTheme.colorScheme.onSurface
    val accent = MaterialTheme.colorScheme.primary
    val guideColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
    val current = page.lines[page.currentLine]
    // The focal letter is only marked once the page has settled.
    val markFocalLetter = !scroll.isScrolling
    val currentText =
        remember(current, page.currentWord, ink, accent, markFocalLetter) {
            currentLineText(current, page.currentWord, ink, accent, markFocalLetter)
        }

    Canvas(
        modifier =
        modifier
            .fillMaxSize()
            // Offscreen so the edge fades only mask what this canvas drew.
            .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
            .semantics { contentDescription = current.text },
    ) {
        val centerY = size.height / 2
        val textHeight = geometry.layout(current).size.height
        val scrollX = scroll.scrollX.value
        val offsets = geometry.lineOffsets(page)

        page.lines.forEachIndexed { index, line ->
            val top = centerY + scroll.offsetY + offsets[index] - textHeight / 2
            if (top + textHeight < 0 || top > size.height) return@forEachIndexed

            val topLeft = Offset(geometry.lineX(line, scrollX), top)
            when {
                index == page.currentLine -> drawText(geometry.layout(currentText, line.isHeading), topLeft = topLeft)
                line.isHeading -> drawText(geometry.layout(line), ink.copy(alpha = HEADING_LINE_ALPHA), topLeft)
                else -> drawText(geometry.layout(line), ink.copy(alpha = CONTEXT_ALPHA), topLeft)
            }
        }

        fadeHorizontalEdges(PAGE_EDGE_FADE)
        fadeVerticalEdges()

        if (!current.isHeading) {
            drawFocalGuides(geometry.focalX, centerY - textHeight / 2, centerY + textHeight / 2, guideColor)
        }
    }
}

/** The current line, with the current word in full ink and optionally its focal letter marked. */
private fun currentLineText(
    line: PageLine,
    currentWord: Int,
    ink: Color,
    accent: Color,
    markFocalLetter: Boolean,
): AnnotatedString = buildAnnotatedString {
    append(line.text)
    if (line.isHeading) {
        addStyle(SpanStyle(color = ink), 0, length)
        return@buildAnnotatedString
    }
    addStyle(SpanStyle(color = ink.copy(alpha = CURRENT_LINE_ALPHA)), 0, length)
    val start = line.wordStarts[currentWord]
    addStyle(SpanStyle(color = ink), start, start + line.words[currentWord].length)
    if (markFocalLetter && line.words[currentWord].isNotEmpty()) {
        val focal = line.anchorChar(currentWord)
        addStyle(SpanStyle(color = accent), focal, focal + 1)
    }
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
