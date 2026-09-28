package io.github.nanopenguin.pace.book

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.FileNotFoundException

/** Bump when parsing changes, so that cached books are parsed again. */
private const val PARSER_VERSION = 1

enum class BookError {
    /** The file was moved or deleted, or the app lost permission to read it. */
    Missing,

    /** The file is not a book this app can read, or it is damaged. */
    Unreadable,

    /** The book contains no text, e.g. only images. */
    NoText,
}

class BookException(
    val error: BookError,
    cause: Throwable? = null,
) : Exception(error.name, cause)

/** Opens books the user picked in the system file picker. */
class BookRepository(
    private val context: Context,
    private val cache: BookCache,
) {
    private val resolver get() = context.contentResolver

    /** Keeps access to a picked file across restarts. Not every file provider allows this. */
    fun keepAccess(uri: Uri) {
        runCatching { resolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
    }

    /** Releases the access and cached data of a book removed from the library. */
    fun forget(uri: Uri) {
        runCatching { resolver.releasePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
        cache.delete(uri.toString())
    }

    /** Loads the book at [uri], from the cache if the file has not changed since it was parsed. */
    suspend fun load(uri: Uri): Book = withContext(Dispatchers.IO) {
        val file = fileInfo(uri)
        val source = "$PARSER_VERSION:${file.size}:${file.lastModified}"
        cache.read(uri.toString(), source)?.let { return@withContext it }

        val book =
            try {
                val stream = resolver.openInputStream(uri) ?: throw BookException(BookError.Missing)
                stream.buffered().use { parse(it) }
            } catch (exception: BookException) {
                throw exception
            } catch (exception: FileNotFoundException) {
                throw BookException(BookError.Missing, exception)
            } catch (exception: SecurityException) {
                throw BookException(BookError.Missing, exception)
            } catch (exception: Exception) {
                // Damaged files can make parsers fail in unexpected ways; that must not crash the app.
                throw BookException(BookError.Unreadable, exception)
            }
        if (book.blocks.isEmpty()) throw BookException(BookError.NoText)

        val named = if (book.title.isBlank()) book.copy(title = file.name.substringBeforeLast('.')) else book
        runCatching { cache.write(uri.toString(), source, named) }
        named
    }

    private fun parse(input: BufferedInputStream): Book {
        input.mark(ZIP_SIGNATURE.size)
        val header = ByteArray(ZIP_SIGNATURE.size)
        val read = input.read(header)
        input.reset()
        return when {
            read == header.size && header.contentEquals(ZIP_SIGNATURE) -> EpubParser.parse(input)
            else -> throw BookFormatException("Unsupported file type")
        }
    }

    private class FileInfo(
        val name: String,
        val size: Long,
        val lastModified: Long,
    )

    private fun fileInfo(uri: Uri): FileInfo {
        val columns = arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE, DocumentsContract.Document.COLUMN_LAST_MODIFIED)
        try {
            resolver.query(uri, columns, null, null, null)?.use { cursor ->
                if (!cursor.moveToFirst()) throw BookException(BookError.Missing)
                fun long(column: String) = cursor.getColumnIndex(column).takeIf { it >= 0 }?.let(cursor::getLong) ?: 0L
                val name = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME).takeIf { it >= 0 }?.let(cursor::getString)
                return FileInfo(name.orEmpty(), long(OpenableColumns.SIZE), long(DocumentsContract.Document.COLUMN_LAST_MODIFIED))
            }
        } catch (exception: SecurityException) {
            throw BookException(BookError.Missing, exception)
        } catch (exception: IllegalArgumentException) {
            throw BookException(BookError.Missing, exception)
        }
        // Some providers answer no queries; the file can still be read, just not cached reliably.
        return FileInfo(name = "", size = 0, lastModified = 0)
    }
}

/** Every EPUB is a ZIP archive, which starts with these bytes. */
private val ZIP_SIGNATURE = byteArrayOf(0x50, 0x4B, 0x03, 0x04)
