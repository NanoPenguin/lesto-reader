package io.github.nanopenguin.pace.reader

/** A heading and the frame it starts at. Headings of any level count as chapters. */
data class Chapter(
    val title: String,
    val firstFrame: Int,
)

/** A book turned into frames for playback, with the lookups the reader needs. */
class RsvpText(
    val frames: List<Frame>,
    val chapters: List<Chapter>,
) {
    /** `unitsFromEnd[i]` is the display time of frames `i` until the end, in word-times. */
    private val unitsFromEnd =
        DoubleArray(frames.size + 1).also { units ->
            for (i in frames.indices.reversed()) units[i] = units[i + 1] + frames[i].displayUnits()
        }

    val lastIndex: Int get() = frames.lastIndex

    fun unitsLeft(index: Int): Double = unitsFromEnd[index]

    fun chapterAt(index: Int): Chapter? = chapters.lastOrNull { it.firstFrame <= index }

    /**
     * Start of the sentence containing [index], or of the previous sentence if [index] already
     * starts one, so that repeated calls keep moving back.
     */
    fun previousSentenceStart(index: Int): Int {
        var start = (index - 1).coerceAtLeast(0)
        while (start > 0 && frames[start - 1].pause < Pause.Sentence) start--
        return start
    }

    /** Up to [count] words from the same block before [index], in reading order. */
    fun wordsBefore(
        index: Int,
        count: Int,
    ): List<String> {
        val block = frames[index].blockIndex
        return (index - 1 downTo (index - count).coerceAtLeast(0))
            .takeWhile { frames[it].blockIndex == block }
            .map { frames[it].text }
            .reversed()
    }

    /** Up to [count] words from the same block after [index]. */
    fun wordsAfter(
        index: Int,
        count: Int,
    ): List<String> {
        val block = frames[index].blockIndex
        return (index + 1..(index + count).coerceAtMost(lastIndex))
            .takeWhile { frames[it].blockIndex == block }
            .map { frames[it].text }
    }
}
