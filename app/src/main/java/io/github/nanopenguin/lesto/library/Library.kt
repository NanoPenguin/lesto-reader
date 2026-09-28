package io.github.nanopenguin.lesto.library

import io.github.nanopenguin.lesto.JsonSerializer
import kotlinx.serialization.Serializable

/** A book the user has opened, and where they are in it. */
@Serializable
data class LibraryEntry(
    val uri: String,
    val title: String,
    val author: String?,
    /** Index of the current frame. */
    val position: Int,
    /** Number of frames the book had when [position] was saved. */
    val frameCount: Int,
    val lastOpened: Long,
) {
    val progress: Float get() = if (frameCount > 1) position.toFloat() / (frameCount - 1) else 0f
}

@Serializable
data class Library(
    val entries: List<LibraryEntry> = emptyList(),
)

val LibrarySerializer = JsonSerializer(Library.serializer(), Library())
