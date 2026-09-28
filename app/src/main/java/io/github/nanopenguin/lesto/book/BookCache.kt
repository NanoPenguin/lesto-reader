package io.github.nanopenguin.lesto.book

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.security.MessageDigest

/**
 * Parsed books on disk, so large books are only parsed once. One file per book; each remembers
 * which version of the source it was parsed from, and is ignored once that no longer matches.
 */
class BookCache(
    private val directory: File,
) {
    /** The cached book for [uri], if it was parsed from [source]. */
    fun read(
        uri: String,
        source: String,
    ): Book? {
        val cached = runCatching { Json.decodeFromString<CachedBook>(fileFor(uri).readText()) }.getOrNull()
        return cached?.book?.takeIf { cached.source == source }
    }

    fun write(
        uri: String,
        source: String,
        book: Book,
    ) {
        directory.mkdirs()
        // Written aside and moved into place, so a crash never leaves a half-written file behind.
        val temporary = File(directory, "${fileFor(uri).name}.tmp")
        temporary.writeText(Json.encodeToString(CachedBook(source, book)))
        temporary.renameTo(fileFor(uri))
    }

    fun delete(uri: String) {
        fileFor(uri).delete()
    }

    private fun fileFor(uri: String): File {
        val hash = MessageDigest.getInstance("SHA-256").digest(uri.toByteArray()).joinToString("") { "%02x".format(it) }
        return File(directory, "$hash.json")
    }
}

@Serializable
private class CachedBook(
    val source: String,
    val book: Book,
)
