package io.github.nanopenguin.pace.reader

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp

/** Long words are scaled down rather than come closer to the edge than this. */
private val EdgeWidth = 24.dp

/**
 * Draws [word] so that its focal letter always sits at the same spot, marked by two short guides.
 * Optional context words are drawn dimmed on either side and fade out towards the edges.
 * Words too long for the screen are scaled down.
 */
@Composable
fun FocusWord(
    word: String,
    wordsBefore: List<String>,
    wordsAfter: List<String>,
    style: TextStyle,
    modifier: Modifier = Modifier,
) {
    val measurer = rememberTextMeasurer()
    val ink = MaterialTheme.colorScheme.onSurface
    val accent = MaterialTheme.colorScheme.primary
    val guideColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
    val height = with(LocalDensity.current) { style.fontSize.toDp() * LINE_SPACING } + GuideLength * 4

    Canvas(
        modifier =
        modifier
            .fillMaxWidth()
            .height(height)
            // Offscreen so the edge fade only masks what this canvas drew.
            .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
            .semantics { contentDescription = word },
    ) {
        val focal = focalIndex(word)
        val focalX = size.width * FOCAL_LINE
        val edge = EdgeWidth.toPx()

        var wordStyle = style.copy(color = ink)
        var layout = measureWord(measurer, word, focal, wordStyle, accent)
        val focalCenter = layout.focalCenter(focal)
        val scale =
            minOf(
                1f,
                (focalX - edge) / focalCenter.coerceAtLeast(1f),
                (size.width - focalX - edge) / (layout.size.width - focalCenter).coerceAtLeast(1f),
            )
        if (scale < 1f) {
            wordStyle = wordStyle.copy(fontSize = style.fontSize * scale)
            layout = measureWord(measurer, word, focal, wordStyle, accent)
        }

        val left = focalX - layout.focalCenter(focal)
        val top = (size.height - layout.size.height) / 2

        // Context is drawn and faded first, so the fade never touches the word itself.
        if (wordsBefore.isNotEmpty() || wordsAfter.isNotEmpty()) {
            val contextStyle = wordStyle.copy(color = ink.copy(alpha = CONTEXT_ALPHA))
            val space = measurer.measure(" ", contextStyle).size.width
            if (wordsBefore.isNotEmpty()) {
                val before = measurer.measure(wordsBefore.joinToString(" "), contextStyle, softWrap = false, maxLines = 1)
                drawText(before, topLeft = Offset(left - space - before.size.width, top))
            }
            if (wordsAfter.isNotEmpty()) {
                val after = measurer.measure(wordsAfter.joinToString(" "), contextStyle, softWrap = false, maxLines = 1)
                drawText(after, topLeft = Offset(left + layout.size.width + space, top))
            }
            fadeHorizontalEdges(CONTEXT_EDGE_FADE)
        }

        drawText(layout, topLeft = Offset(left, top))

        drawFocalGuides(focalX, top, top + layout.size.height, guideColor)
    }
}

private fun measureWord(
    measurer: TextMeasurer,
    word: String,
    focal: Int,
    style: TextStyle,
    accent: Color,
): TextLayoutResult {
    val text =
        buildAnnotatedString {
            append(word)
            if (focal < word.length) addStyle(SpanStyle(color = accent), focal, focal + 1)
        }
    return measurer.measure(text, style, softWrap = false, maxLines = 1)
}

internal fun TextLayoutResult.focalCenter(focal: Int): Float = if (focal < layoutInput.text.length) getBoundingBox(focal).center.x else size.width / 2f
