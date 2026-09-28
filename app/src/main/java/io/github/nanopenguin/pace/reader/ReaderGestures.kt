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

/**
 * Tap to play or pause. Drag sideways to move word by word. Drag up or down to scroll the page:
 * the lines follow the finger, move one line per [lineHeight], and settle when released. Like
 * scrolling a page, dragging right or down pulls earlier text into view.
 *
 * [onStepWords] moves within the current line and [onStepLine] one line up (-1) or down (1); both
 * return false when there is nowhere to go.
 * The callbacks are captured once, so they must not change between recompositions.
 */
fun Modifier.readerGestures(
    haptics: HapticFeedback,
    lineHeight: Float,
    scroll: PageScroll,
    onTap: () -> Unit,
    onDragStart: () -> Unit,
    onStepWords: (Int) -> Boolean,
    onStepLine: (Int) -> Boolean,
): Modifier = pointerInput(Unit) { detectTapGestures(onTap = { onTap() }) }
    .pointerInput(lineHeight) {
        coroutineScope {
            var axis: Orientation? = null
            var horizontalDistance = 0f
            var settling: Job? = null

            // Moves whole lines while the drag covers at least [threshold] of one.
            fun stepLines(threshold: Float) {
                while (abs(scroll.offsetY) >= threshold) {
                    val direction = if (scroll.offsetY > 0) -1 else 1
                    if (!onStepLine(direction)) {
                        scroll.offsetY = scroll.offsetY.coerceIn(-lineHeight / 2, lineHeight / 2)
                        return
                    }
                    scroll.offsetY += direction * lineHeight
                    haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                }
            }

            fun settle() {
                if (axis != Orientation.Vertical) return
                stepLines(threshold = lineHeight / 2)
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
                    stepLines(threshold = lineHeight)
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
