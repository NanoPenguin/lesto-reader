package io.github.nanopenguin.lesto.reader

import androidx.compose.animation.core.AnimationState
import androidx.compose.animation.core.AnimationVector
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateDecay
import androidx.compose.animation.core.tween
import androidx.compose.animation.splineBasedDecay
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroidSize
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.input.pointer.util.VelocityTracker
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlin.math.abs

/** Duration of the glide that brings the nearest word onto the focal point after a drag. */
private const val SETTLE_MILLIS = 250

/**
 * Tap to play or pause. Drag or fling to move the paused page, and pinch to zoom it with [onZoom];
 * once it stops, it glides to [restingPlace] (`scrollX`, `offsetY`). [onMove] returns whether
 * another word became current; landing on another word ticks. The callbacks are captured once, so
 * they must not change between recompositions.
 */
fun Modifier.readerGestures(
    haptics: HapticFeedback,
    scroll: PageScroll,
    onTap: () -> Unit,
    onGestureStart: () -> Unit,
    onMove: (Offset) -> Boolean,
    onZoom: (Float) -> Unit,
    restingPlace: () -> Offset?,
): Modifier = pointerInput(Unit) { detectTapGestures(onTap = { onTap() }) }
    .pointerInput(Unit) {
        coroutineScope {
            val velocityTracker = VelocityTracker()
            var settling: Job? = null
            // Where the fingers' centre has moved since the gesture started, for the fling.
            var travelled = Offset.Zero

            // Only the landing ticks: ticking for every word passed would buzz on long moves.
            var wordChanged = false

            fun move(delta: Offset) {
                if (onMove(delta)) wordChanged = true
            }

            fun settle() {
                val velocity = velocityTracker.calculateVelocity()
                settling =
                    launch {
                        var flung = Offset.Zero
                        AnimationState(Offset.VectorConverter, Offset.Zero, AnimationVector(velocity.x, velocity.y))
                            .animateDecay(splineBasedDecay(this@pointerInput)) {
                                move(value - flung)
                                flung = value
                            }
                        restingPlace()?.let { target ->
                            animate(Offset.VectorConverter, Offset(scroll.scrollX, scroll.offsetY), target, animationSpec = tween(SETTLE_MILLIS)) { value, _ ->
                                scroll.scrollX = value.x
                                scroll.offsetY = value.y
                            }
                            if (wordChanged) haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                            wordChanged = false
                        }
                        scroll.isScrolling = false
                    }
            }

            detectPanAndZoom(
                onStart = {
                    settling?.cancel()
                    velocityTracker.resetTracking()
                    travelled = Offset.Zero
                    onGestureStart()
                    scroll.isScrolling = true
                },
                onEnd = ::settle,
            ) { uptimeMillis, pan, zoom ->
                if (zoom != 1f) onZoom(zoom)
                travelled += pan
                velocityTracker.addPosition(uptimeMillis, travelled)
                if (pan != Offset.Zero) move(pan)
            }
        }
    }

/**
 * Drags with any number of fingers, which also zoom when there are several. Once the fingers have
 * moved or spread past the touch slop, [onGesture] gets each movement of their centre and the
 * factor by which their spread changed, and [onEnd] follows when they are lifted.
 */
private suspend fun PointerInputScope.detectPanAndZoom(
    onStart: () -> Unit,
    onEnd: () -> Unit,
    onGesture: (uptimeMillis: Long, pan: Offset, zoom: Float) -> Unit,
) = awaitEachGesture {
    awaitFirstDown(requireUnconsumed = false)
    var isStarted = false
    var panBeforeStart = Offset.Zero
    var zoomBeforeStart = 1f
    do {
        val event = awaitPointerEvent()
        if (event.changes.any { it.isConsumed }) break
        val pan = event.calculatePan()
        val zoom = event.calculateZoom()
        if (!isStarted) {
            panBeforeStart += pan
            zoomBeforeStart *= zoom
            val spread = abs(1 - zoomBeforeStart) * event.calculateCentroidSize(useCurrent = false)
            if (panBeforeStart.getDistance() > viewConfiguration.touchSlop || spread > viewConfiguration.touchSlop) {
                isStarted = true
                onStart()
            }
        }
        if (isStarted) {
            onGesture(event.changes.first().uptimeMillis, pan, zoom)
            event.changes.forEach { if (it.positionChanged()) it.consume() }
        }
    } while (event.changes.any { it.pressed })
    if (isStarted) onEnd()
}
