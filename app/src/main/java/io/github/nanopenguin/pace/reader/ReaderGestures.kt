package io.github.nanopenguin.pace.reader

import androidx.compose.animation.core.animate
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.MutableFloatState
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.sign

/** Drag distance per word step. */
private val WordStep = 24.dp

/**
 * Tap to play or pause. Drag sideways to move word by word. Drag up or down to scroll through
 * sentences: the lines follow the finger through [verticalOffset], one sentence per [lineHeight],
 * and settle on the nearest sentence when released. Like scrolling a page, dragging right or down
 * pulls earlier text into view.
 *
 * The callbacks are captured once, so they must not change between recompositions.
 */
fun Modifier.readerGestures(
    haptics: HapticFeedback,
    lineHeight: Float,
    verticalOffset: MutableFloatState,
    onTap: () -> Unit,
    onDragStart: () -> Unit,
    onStepWords: (Int) -> Unit,
    onStepSentences: (Int) -> Unit,
): Modifier = pointerInput(Unit) { detectTapGestures(onTap = { onTap() }) }
    .pointerInput(lineHeight) {
        coroutineScope {
            var axis: Orientation? = null
            var horizontalDistance = 0f
            var verticalDistance = 0f
            var settling: Job? = null

            fun stepSentences(steps: Int) {
                verticalDistance -= steps * lineHeight
                haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                onStepSentences(-steps)
            }

            fun settle() {
                if (abs(verticalDistance) > lineHeight / 2) stepSentences(sign(verticalDistance).toInt())
                val from = verticalDistance
                settling = launch { animate(from, 0f) { value, _ -> verticalOffset.floatValue = value } }
            }

            detectDragGestures(
                onDragStart = {
                    // Continue from wherever a previous drag is still settling.
                    settling?.cancel()
                    axis = null
                    horizontalDistance = 0f
                    verticalDistance = verticalOffset.floatValue
                    onDragStart()
                },
                onDragEnd = ::settle,
                onDragCancel = ::settle,
            ) { change, drag ->
                change.consume()
                val dragAxis =
                    axis ?: (if (abs(drag.x) >= abs(drag.y)) Orientation.Horizontal else Orientation.Vertical)
                        .also { axis = it }

                if (dragAxis == Orientation.Vertical) {
                    verticalDistance += drag.y
                    val steps = (verticalDistance / lineHeight).toInt()
                    if (steps != 0) stepSentences(steps)
                    verticalOffset.floatValue = verticalDistance
                } else {
                    horizontalDistance += drag.x
                    val stepSize = WordStep.toPx()
                    val steps = (horizontalDistance / stepSize).toInt()
                    if (steps != 0) {
                        horizontalDistance -= steps * stepSize
                        haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                        onStepWords(-steps)
                    }
                }
            }
        }
    }
