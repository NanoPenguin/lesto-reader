package io.github.nanopenguin.pace.reader

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
)
