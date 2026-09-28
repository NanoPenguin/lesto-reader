package io.github.nanopenguin.lesto.reader

import kotlin.math.abs

/** One line of the paused page: a sentence, or a heading. */
data class PageLine(
    val words: List<String>,
    val firstFrame: Int,
    val isHeading: Boolean,
) {
    val text: String = words.joinToString(" ")

    /** Character index at which each word starts in [text]. */
    val wordStarts: IntArray =
        IntArray(words.size).also { starts ->
            for (i in 1 until words.size) starts[i] = starts[i - 1] + words[i - 1].length + 1
        }

    /**
     * Character of [word] that lines up with the focal point: its focal letter. A heading is one
     * frame, so it lines up on the focal letter of its first word.
     */
    fun anchorChar(word: Int): Int = if (isHeading) {
        focalIndex(text.substringBefore(' '))
    } else {
        wordStarts[word] + focalIndex(words[word])
    }
}

/**
 * The text around the current position while paused, one line per sentence.
 *
 * @property currentLine index into [lines] of the line being read.
 * @property currentWord index into that line's words of the current word.
 */
data class TextPage(
    val lines: List<PageLine>,
    val currentLine: Int,
    val currentWord: Int,
) {
    /**
     * Vertical position of each line relative to the current one: [lineHeight] apart, with
     * [headingGap] extra above every heading except one that opens the book.
     */
    fun lineOffsets(
        lineHeight: Float,
        headingGap: Float,
    ): FloatArray {
        fun spaceAbove(line: PageLine) = lineHeight + if (line.isHeading && line.firstFrame > 0) headingGap else 0f

        val offsets = FloatArray(lines.size)
        for (i in currentLine + 1..lines.lastIndex) offsets[i] = offsets[i - 1] + spaceAbove(lines[i])
        for (i in currentLine - 1 downTo 0) offsets[i] = offsets[i + 1] - spaceAbove(lines[i + 1])
        return offsets
    }

    /**
     * The line closest to the focal point when the current line sits [offsetY] below it, given
     * the lines' [offsets] from [lineOffsets].
     */
    fun lineNearest(
        offsetY: Float,
        offsets: FloatArray,
    ): Int = lines.indices.minBy { abs(offsetY + offsets[it]) }
}
