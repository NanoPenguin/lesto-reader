package io.github.nanopenguin.pace.reader

import kotlin.math.roundToLong

/** Words with more letters than this get [LONG_WORD_UNITS] instead of one word-time. */
private const val LONG_WORD_LETTERS = 8
private const val LONG_WORD_UNITS = 1.3

/** Number of frames over which playback eases in to full speed after pressing play. */
private const val RAMP_UP_FRAMES = 5

/**
 * How long a frame is shown, in word-times: one per word (a little more for long words),
 * plus its [Pause]. A word-time lasts `60 000 / wordsPerMinute` milliseconds.
 */
fun Frame.displayUnits(): Double {
    val wordCount = text.count { it == ' ' } + 1
    val wordUnits =
        when {
            wordCount > 1 -> wordCount.toDouble()
            text.count { it.isLetterOrDigit() } > LONG_WORD_LETTERS -> LONG_WORD_UNITS
            else -> 1.0
        }
    return wordUnits + pause.extraUnits
}

fun millisPerUnit(wordsPerMinute: Int): Double = 60_000.0 / wordsPerMinute

/** Slows down the first frames after pressing play: twice as long at first, easing to normal. */
fun rampUpFactor(framesSincePlay: Int): Double = if (framesSincePlay >= RAMP_UP_FRAMES) {
    1.0
} else {
    1.0 + (RAMP_UP_FRAMES - framesSincePlay).toDouble() / RAMP_UP_FRAMES
}

fun displayMillis(
    frame: Frame,
    wordsPerMinute: Int,
    framesSincePlay: Int,
): Long = (frame.displayUnits() * millisPerUnit(wordsPerMinute) * rampUpFactor(framesSincePlay)).roundToLong()
