package io.github.nanopenguin.lesto.reader

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.dp

// Geometry shared by the playing word and the paused page, so switching between them does not
// move the text.

/** Horizontal position of the focal letter, as a fraction of the width: slightly left of centre. */
internal const val FOCAL_LINE = 0.4f

/** Opacity of surrounding text: context words and neighbouring sentences. */
internal const val CONTEXT_ALPHA = 0.3f

/** Context words fade out over this fraction of the width on each side. */
internal const val CONTEXT_EDGE_FADE = 0.3f

/** Line height as a multiple of the font size. */
internal const val LINE_SPACING = 1.5f

internal val GuideLength = 10.dp
private val GuideGap = 6.dp

/** Masks everything drawn so far so it fades out over [fraction] of the width at each side. */
internal fun DrawScope.fadeHorizontalEdges(fraction: Float) {
    drawRect(
        brush =
        Brush.horizontalGradient(
            0f to Color.Transparent,
            fraction to Color.Black,
            1f - fraction to Color.Black,
            1f to Color.Transparent,
        ),
        blendMode = BlendMode.DstIn,
    )
}

/** Two short vertical marks above and below the focal letter. */
internal fun DrawScope.drawFocalGuides(
    focalX: Float,
    textTop: Float,
    textBottom: Float,
    color: Color,
) {
    val top = textTop - GuideGap.toPx()
    val bottom = textBottom + GuideGap.toPx()
    val stroke = 1.dp.toPx()
    drawLine(color, Offset(focalX, top - GuideLength.toPx()), Offset(focalX, top), stroke)
    drawLine(color, Offset(focalX, bottom), Offset(focalX, bottom + GuideLength.toPx()), stroke)
}
