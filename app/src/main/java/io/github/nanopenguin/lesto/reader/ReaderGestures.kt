package io.github.nanopenguin.lesto.reader

import androidx.compose.animation.core.AnimationState
import androidx.compose.animation.core.AnimationVector
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateDecay
import androidx.compose.animation.core.tween
import androidx.compose.animation.splineBasedDecay
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.input.pointer.util.addPointerInputChange
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

/** Duration of the glide that brings the nearest word onto the focal point after a drag. */
private const val SETTLE_MILLIS = 250

/**
 * Tap to play or pause. Drag or fling to move the paused page; once it stops, it glides to
 * [restingPlace] (`scrollX`, `offsetY`). [onMove] returns whether another word became current,
 * which ticks. The callbacks are captured once, so they must not change between recompositions.
 */
fun Modifier.readerGestures(
    haptics: HapticFeedback,
    scroll: PageScroll,
    onTap: () -> Unit,
    onDragStart: () -> Unit,
    onMove: (Offset) -> Boolean,
    restingPlace: () -> Offset?,
): Modifier = pointerInput(Unit) { detectTapGestures(onTap = { onTap() }) }
    .pointerInput(Unit) {
        coroutineScope {
            val velocityTracker = VelocityTracker()
            var settling: Job? = null

            fun settle() {
                val velocity = velocityTracker.calculateVelocity()
                settling =
                    launch {
                        // No ticks while flinging: they would buzz.
                        var flung = Offset.Zero
                        AnimationState(Offset.VectorConverter, Offset.Zero, AnimationVector(velocity.x, velocity.y))
                            .animateDecay(splineBasedDecay(this@pointerInput)) {
                                onMove(value - flung)
                                flung = value
                            }
                        restingPlace()?.let { target ->
                            animate(Offset.VectorConverter, Offset(scroll.scrollX, scroll.offsetY), target, animationSpec = tween(SETTLE_MILLIS)) { value, _ ->
                                scroll.scrollX = value.x
                                scroll.offsetY = value.y
                            }
                        }
                        scroll.isScrolling = false
                    }
            }

            detectDragGestures(
                onDragStart = {
                    settling?.cancel()
                    velocityTracker.resetTracking()
                    onDragStart()
                    scroll.isScrolling = true
                },
                onDragEnd = ::settle,
                onDragCancel = ::settle,
            ) { change, drag ->
                change.consume()
                velocityTracker.addPointerInputChange(change)
                if (onMove(drag)) haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
            }
        }
    }
