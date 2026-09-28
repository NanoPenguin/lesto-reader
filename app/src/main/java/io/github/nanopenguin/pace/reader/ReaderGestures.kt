package io.github.nanopenguin.pace.reader

import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import kotlin.math.abs

/** Drag distance per word step and per sentence step. */
private val WordStep = 24.dp
private val SentenceStep = 56.dp

/**
 * Tap to play or pause. Drag sideways to move word by word, up or down to move sentence by
 * sentence. Like scrolling a page, dragging right or down pulls earlier text into view.
 *
 * The callbacks are captured once, so they must not change between recompositions.
 */
fun Modifier.readerGestures(
    haptics: HapticFeedback,
    onTap: () -> Unit,
    onStepWords: (Int) -> Unit,
    onStepSentences: (Int) -> Unit,
): Modifier = pointerInput(Unit) { detectTapGestures(onTap = { onTap() }) }
    .pointerInput(Unit) {
        var axis: Orientation? = null
        var distance = 0f

        detectDragGestures(
            onDragStart = {
                axis = null
                distance = 0f
            },
        ) { change, drag ->
            change.consume()
            val dragAxis =
                axis ?: (if (abs(drag.x) >= abs(drag.y)) Orientation.Horizontal else Orientation.Vertical)
                    .also { axis = it }
            val stepSize = (if (dragAxis == Orientation.Horizontal) WordStep else SentenceStep).toPx()
            distance += if (dragAxis == Orientation.Horizontal) drag.x else drag.y

            val steps = (distance / stepSize).toInt()
            if (steps != 0) {
                distance -= steps * stepSize
                haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                if (dragAxis == Orientation.Horizontal) onStepWords(-steps) else onStepSentences(-steps)
            }
        }
    }
