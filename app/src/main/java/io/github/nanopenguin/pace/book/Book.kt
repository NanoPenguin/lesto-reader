package io.github.nanopenguin.pace.book

/** A book reduced to what the reader needs: metadata and a flat sequence of text blocks. */
data class Book(
    val title: String,
    val author: String?,
    val blocks: List<Block>,
)

sealed interface Block {
    val text: String

    data class Heading(override val text: String, val level: Int) : Block

    data class Paragraph(override val text: String) : Block
}
