package io.github.nanopenguin.pace.reader

/**
 * Index of the letter the eye should fixate on: the "optimal recognition point", slightly left of
 * the word's middle. Surrounding punctuation such as quotes is not counted as part of the word.
 */
fun focalIndex(word: String): Int {
    val start = word.indexOfFirst { it.isLetterOrDigit() }
    if (start < 0) return 0
    val end = word.indexOfLast { it.isLetterOrDigit() }
    val offset =
        when (end - start + 1) {
            1 -> 0
            in 2..5 -> 1
            in 6..9 -> 2
            in 10..13 -> 3
            else -> 4
        }
    return start + offset
}
