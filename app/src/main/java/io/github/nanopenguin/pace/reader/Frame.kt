package io.github.nanopenguin.pace.reader

/** Extra time the reader lingers on a frame, in word-times. Ordered from shortest to longest. */
enum class Pause(val extraUnits: Double) {
    None(0.0),
    Clause(0.5),
    Sentence(1.0),
    Paragraph(1.5),
    Heading(2.0),
}

/**
 * One step of playback: a single word, or a whole heading.
 *
 * @property blockIndex the block this frame came from. Context words are only shown within a block.
 */
data class Frame(
    val text: String,
    val isHeading: Boolean,
    val pause: Pause,
    val blockIndex: Int,
)
