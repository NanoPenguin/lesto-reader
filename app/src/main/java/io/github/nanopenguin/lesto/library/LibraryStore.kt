package io.github.nanopenguin.lesto.library

import androidx.datastore.core.DataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/** The books the user has opened, most recent first, with their reading positions. */
class LibraryStore(
    private val dataStore: DataStore<Library>,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    val entries: Flow<List<LibraryEntry>> = dataStore.data.map { library -> library.entries.sortedByDescending { it.lastOpened } }

    /**
     * Records that a book was opened and returns the position to continue from. If its frame count
     * changed, e.g. after a parser update, it continues at the same fraction of the way through.
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
