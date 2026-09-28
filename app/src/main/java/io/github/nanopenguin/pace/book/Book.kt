package io.github.nanopenguin.pace.book

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** A book reduced to what the reader needs: metadata and a flat sequence of text blocks. */
@Serializable
data class Book(
    val title: String,
    val author: String?,
    val blocks: List<Block>,
)

@Serializable
sealed interface Block {
    val text: String

    @Serializable
    @SerialName("heading")
    data class Heading(override val text: String, val level: Int) : Block

    @Serializable
    @SerialName("paragraph")
    data class Paragraph(override val text: String) : Block
}
