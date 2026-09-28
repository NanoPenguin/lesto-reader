package io.github.nanopenguin.pace.reader

import androidx.compose.animation.core.Animatable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** Scroll position of the paused page, shared by the gestures that move it and the drawing. */
class PageScroll {
    /** Shared start of the lines on screen; see [PageGeometry]. */
    val scrollX = Animatable(0f)

    /** How far the lines are dragged vertically from their resting place. */
    var offsetY by mutableFloatStateOf(0f)

    /** True from the start of a vertical drag until the lines have settled. */
    var isScrolling by mutableStateOf(false)

    /** Whether [scrollX] has been set since the page appeared; the first placement snaps. */
    var isPlaced = false
}
