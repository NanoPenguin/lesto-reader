package io.github.nanopenguin.pace.library

import androidx.datastore.core.CorruptionException
import androidx.datastore.core.DataStore
import androidx.datastore.core.Serializer
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.io.InputStream
import java.io.OutputStream

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

/** The books the user has opened, most recent first, with their reading positions. */
class LibraryStore(
    private val dataStore: DataStore<Library>,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    val entries: Flow<List<LibraryEntry>> = dataStore.data.map { library -> library.entries.sortedByDescending { it.lastOpened } }

    /**
     * Records that a book was opened and returns the position to continue from. A book whose frame
     * count changed since last time, for example after an app update, continues at the same
     * fraction of the way through.
     */
    suspend fun open(
        uri: String,
        title: String,
        author: String?,
        frameCount: Int,
    ): Int {
        val previous = dataStore.data.first().entries.find { it.uri == uri }
        val position = previous?.let { restoredPosition(it.position, it.frameCount, frameCount) } ?: 0
        update(uri) { LibraryEntry(uri, title, author, position, frameCount, lastOpened = clock()) }
        return position
    }

    suspend fun savePosition(
        uri: String,
        position: Int,
    ) = update(uri) { it?.copy(position = position) }

    suspend fun remove(uri: String) = update(uri) { null }

    /** Replaces the entry for [uri] with the result of [change], or removes it if that is null. */
    private suspend fun update(
        uri: String,
        change: (LibraryEntry?) -> LibraryEntry?,
    ) {
        dataStore.updateData { library ->
            val others = library.entries.filter { it.uri != uri }
            library.copy(entries = others + listOfNotNull(change(library.entries.find { it.uri == uri })))
        }
    }
}

/** [position] out of [savedFrameCount] frames, moved to the same fraction of [frameCount] frames. */
fun restoredPosition(
    position: Int,
    savedFrameCount: Int,
    frameCount: Int,
): Int {
    if (frameCount <= 0) return 0
    if (savedFrameCount == frameCount || savedFrameCount <= 1) return position.coerceIn(0, frameCount - 1)
    return (position.toLong() * (frameCount - 1) / (savedFrameCount - 1)).toInt().coerceIn(0, frameCount - 1)
}

private val LibraryJson = Json { ignoreUnknownKeys = true }

object LibrarySerializer : Serializer<Library> {
    override val defaultValue = Library()

    override suspend fun readFrom(input: InputStream): Library = try {
        LibraryJson.decodeFromString(input.readBytes().decodeToString())
    } catch (exception: SerializationException) {
        throw CorruptionException("Library file is unreadable", exception)
    }

    override suspend fun writeTo(
        t: Library,
        output: OutputStream,
    ) = output.write(LibraryJson.encodeToString(t).encodeToByteArray())
}
