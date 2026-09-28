package io.github.nanopenguin.pace.reader

/** A heading and the frame it starts at. Headings of any level count as chapters. */
data class Chapter(
    val title: String,
    val level: Int,
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

    /** Display time of the frames in [range], in word-times. */
    fun units(range: IntRange): Double = unitsFromEnd[range.first] - unitsFromEnd[range.last + 1]

    /** Index into [chapters] of the chapter containing [index], or null before the first heading. */
    fun chapterIndexAt(index: Int): Int? = chapters.indexOfLast { it.firstFrame <= index }.takeIf { it >= 0 }

    /**
     * Frames of the chapter containing [index]. Text before the first heading is its own section,
     * and a book without headings is one section.
     */
    fun sectionAt(index: Int): IntRange {
        val chapter = chapterIndexAt(index)
        val start = chapter?.let { chapters[it].firstFrame } ?: 0
        val next = chapters.getOrNull(chapter?.plus(1) ?: 0)
        val end = next?.let { it.firstFrame - 1 } ?: lastIndex
        return start..end
    }

    /** Frames of the sentence containing [index]. A heading is a sentence of its own. */
    fun sentenceAt(index: Int): IntRange {
        var start = index
        while (start > 0 && frames[start - 1].pause < Pause.Sentence) start--
        var end = index
        while (end < lastIndex && frames[end].pause < Pause.Sentence) end++
        return start..end
    }

    /**
     * Frames of the paused-page line containing [index]: its sentence, split into lines of at most
     * [maxWords] words if longer, so that text without punctuation cannot make an endless line.
     */
    fun lineAt(
        index: Int,
        maxWords: Int,
    ): IntRange {
        val sentence = sentenceAt(index)
        val start = sentence.first + (index - sentence.first) / maxWords * maxWords
        return start..minOf(sentence.last, start + maxWords - 1)
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
