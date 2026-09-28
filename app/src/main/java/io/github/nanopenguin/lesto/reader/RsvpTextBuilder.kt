package io.github.nanopenguin.lesto.reader

import io.github.nanopenguin.lesto.book.Block
import io.github.nanopenguin.lesto.book.Book

private val Whitespace = Regex("\\s+")

/** Closing marks that may follow sentence punctuation, as in `late!”` or `(end.)`. */
private const val CLOSING_MARKS = "\"'”’)]»"

/** Turns a book into frames: one per word of a paragraph, one per heading. */
fun Book.toRsvpText(): RsvpText {
    val frames = mutableListOf<Frame>()
    val chapters = mutableListOf<Chapter>()

    blocks.forEachIndexed { blockIndex, block ->
        val words = block.text.split(Whitespace).filter { it.isNotEmpty() }
        if (words.isEmpty()) return@forEachIndexed

        when (block) {
            is Block.Heading -> {
                val title = words.joinToString(" ")
                chapters += Chapter(title, block.level, firstFrame = frames.size)
                frames += Frame(title, isHeading = true, Pause.Heading, blockIndex)
            }

            is Block.Paragraph -> {
                words.forEachIndexed { i, word ->
                    val pause = if (i == words.lastIndex) Pause.Paragraph else pauseAfter(word)
                    frames += Frame(word, isHeading = false, pause, blockIndex)
                }
            }
        }
    }
    return RsvpText(frames, chapters)
}

internal fun pauseAfter(word: String): Pause = when (word.trimEnd { it in CLOSING_MARKS }.lastOrNull()) {
    '.', '!', '?', '…' -> Pause.Sentence
    ',', ';', ':', '–', '—' -> Pause.Clause
    else -> Pause.None
}
