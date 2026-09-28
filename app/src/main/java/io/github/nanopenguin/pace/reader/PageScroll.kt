package io.github.nanopenguin.pace.reader

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** Position of the paused page, shared by the gestures that move it and the drawing. */
class PageScroll {
    /** Where every line starts on screen; see [PageGeometry]. */
    var scrollX by mutableFloatStateOf(0f)

    /**
     * While scrolling, how far the centre of the line starting at [anchorFrame] is below the
     * focal point. At rest, the current line is on the focal point.
     */
    var offsetY by mutableFloatStateOf(0f)

    /** First frame of the line that [offsetY] is measured from. */
    var anchorFrame by mutableIntStateOf(0)

    /** True from the start of a drag until the page has settled. */
    var isScrolling by mutableStateOf(false)

    /** Whether [scrollX] has been set since the page appeared; the first placement snaps. */
    var isPlaced = false
}
