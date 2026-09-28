package io.github.nanopenguin.lesto.reader

import androidx.compose.ui.geometry.Offset

/**
 * Moves the paused page as one sheet, in any direction. While it moves, the word closest to the
 * focal point becomes the current one, so that it is highlighted and is where the page settles.
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

    /** Moves the page by [delta]. Returns whether another word became the current one. */
    fun move(delta: Offset): Boolean {
        val page = currentPage() ?: return false
        val offsets = geometry.lineOffsets(page)
        scroll.offsetY =
            limit(scroll.offsetY, scroll.offsetY + delta.y, -overscroll - offsets.last(), overscroll - offsets.first())
        val firstWordX = page.lines.minOf { geometry.anchorX(it, 0) }
        val lastWordX = page.lines.maxOf { geometry.anchorX(it, it.words.lastIndex) }
        scroll.scrollX =
            limit(
                scroll.scrollX,
                scroll.scrollX + delta.x,
                geometry.focalX - lastWordX - overscroll,
                geometry.focalX - firstWordX + overscroll,
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
