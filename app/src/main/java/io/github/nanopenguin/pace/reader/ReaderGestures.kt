package io.github.nanopenguin.pace.reader

import androidx.compose.animation.core.animate
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlin.math.abs

/** Drag distance per word step. */
private val WordStep = 24.dp

/** How far the page can be dragged past the first or last line. */
private val EdgeOverscroll = 32.dp

/**
 * Tap to play or pause. Drag sideways to move word by word. Drag up or down to scroll the page:
 * the lines follow the finger, the current line changes once dragged the distance to the next,
 * and the lines settle when released. Like scrolling a page, dragging right or down pulls earlier
 * text into view.
 *
 * [onStepWords] moves within the current line and returns false when it cannot. [lineDistance]
 * gives the distance to the line above (-1) or below (1), or null if there is none, and
 * [onStepLine] moves there.
 * The callbacks are captured once, so they must not change between recompositions.
 */
fun Modifier.readerGestures(
    haptics: HapticFeedback,
    scroll: PageScroll,
    onTap: () -> Unit,
    onDragStart: () -> Unit,
    onStepWords: (Int) -> Boolean,
    lineDistance: (Int) -> Float?,
    onStepLine: (Int) -> Unit,
): Modifier = pointerInput(Unit) { detectTapGestures(onTap = { onTap() }) }
    .pointerInput(Unit) {
        coroutineScope {
            var axis: Orientation? = null
            var horizontalDistance = 0f
            var settling: Job? = null

            // Moves to the next line while the drag covers at least [fraction] of the distance to it.
            fun stepLines(fraction: Float) {
                while (scroll.offsetY != 0f) {
                    val direction = if (scroll.offsetY > 0) -1 else 1
                    val distance = lineDistance(direction)
                    if (distance == null) {
                        val overscroll = EdgeOverscroll.toPx()
                        scroll.offsetY = scroll.offsetY.coerceIn(-overscroll, overscroll)
                        return
                    }
                    if (abs(scroll.offsetY) < distance * fraction) return
                    onStepLine(direction)
                    scroll.offsetY += direction * distance
                    haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                }
            }

            fun settle() {
                if (axis != Orientation.Vertical) return
                stepLines(fraction = 0.5f)
                val from = scroll.offsetY
                settling =
                    launch {
                        animate(from, 0f) { value, _ -> scroll.offsetY = value }
                        scroll.isScrolling = false
                    }
            }

            detectDragGestures(
                onDragStart = {
                    axis = null
                    horizontalDistance = 0f
                    onDragStart()
                },
                onDragEnd = ::settle,
                onDragCancel = ::settle,
            ) { change, drag ->
                change.consume()
                val dragAxis =
                    axis ?: (if (abs(drag.x) >= abs(drag.y)) Orientation.Horizontal else Orientation.Vertical)
                        .also {
                            axis = it
                            if (it == Orientation.Vertical) {
                                // Continue from wherever a previous drag is still settling.
                                settling?.cancel()
                                scroll.isScrolling = true
                            }
                        }

                if (dragAxis == Orientation.Vertical) {
                    scroll.offsetY += drag.y
                    stepLines(fraction = 1f)
                } else {
                    horizontalDistance += drag.x
                    val stepSize = WordStep.toPx()
                    val steps = (horizontalDistance / stepSize).toInt()
                    if (steps != 0) {
                        horizontalDistance -= steps * stepSize
                        if (onStepWords(-steps)) haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                    }
                }
            }
        }
    }
