package io.github.nanopenguin.lesto.reader

import androidx.compose.ui.geometry.Offset

/** How far the page zooms out: to a quarter of its size, for an overview of the text around. */
private const val MIN_ZOOM = 0.25f

/**
 * Moves the paused page as one sheet, in any direction, and zooms it. While it moves, the word
 * closest to the focal point becomes the current one, so that it is highlighted and is where the
 * page settles.
 *
 * [currentPage] must return the latest page, which [onSeek] updates at once.
 */
class PageNavigator(
    private val geometry: PageGeometry,
    private val scroll: PageScroll,
    private val currentPage: () -> TextPage?,
    private val onSeek: (Int) -> Unit,
    /** How far the page can be moved past its first or last line and word. */
    private val overscroll: Float,
) {
    /** Starts moving from where the page rests, or from wherever a previous move is settling. */
    fun start() {
        if (scroll.isScrolling) return
        val page = currentPage() ?: return
        val line = page.lines[page.currentLine]
        scroll.anchorFrame = line.firstFrame
        scroll.offsetY = 0f
        scroll.scrollX = geometry.scrollXFor(line, page.currentWord)
    }

    /**
     * Moves the page by [delta] on screen; zoomed out, that covers more of it. Returns whether
     * another word became the current one.
     */
    fun move(delta: Offset): Boolean {
        val page = currentPage() ?: return false
        val step = delta / scroll.zoom
        val margin = overscroll / scroll.zoom
        val offsets = geometry.lineOffsets(page)
        scroll.offsetY =
            limit(scroll.offsetY, scroll.offsetY + step.y, -margin - offsets.last(), margin - offsets.first())
        val firstWordX = page.lines.minOf { geometry.anchorX(it, 0) }
        val lastWordX = page.lines.maxOf { geometry.anchorX(it, it.words.lastIndex) }
        scroll.scrollX =
            limit(
                scroll.scrollX,
                scroll.scrollX + step.x,
                geometry.focalX - lastWordX - margin,
                geometry.focalX - firstWordX + margin,
            )

        val lineIndex = page.lineNearest(scroll.offsetY, offsets)
        val line = page.lines[lineIndex]
        val word = geometry.wordAt(line, scroll.scrollX)
        if (lineIndex == page.currentLine && word == page.currentWord) return false
        // The offset is measured from the current line, so it follows the move to another one.
        scroll.offsetY += offsets[lineIndex]
        scroll.anchorFrame = line.firstFrame
        onSeek(line.firstFrame + word)
        return true
    }

    /**
     * Zooms the page by [factor] around the focal point, so the current word stays put. It zooms
     * out to [MIN_ZOOM], and never in past its original size.
     */
    fun zoom(factor: Float) {
        scroll.zoom = (scroll.zoom * factor).coerceIn(MIN_ZOOM, 1f)
    }

    /** Where the page comes to rest: with the current word on the focal point. */
    fun restingPlace(): Offset? {
        val page = currentPage() ?: return null
        return Offset(geometry.scrollXFor(page.lines[page.currentLine], page.currentWord), 0f)
    }
}

/**
 * Moves from [old] to [new], but not further outside [min]..[max] than it already is: the bounds
 * change as other lines come into view, and must not make the page jump.
 */
private fun limit(
    old: Float,
    new: Float,
    min: Float,
    max: Float,
): Float = when {
    new > max && new > old -> maxOf(old, max)
    new < min && new < old -> minOf(old, min)
    else -> new
}
