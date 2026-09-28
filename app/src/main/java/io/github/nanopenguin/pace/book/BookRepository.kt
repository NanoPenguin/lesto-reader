package io.github.nanopenguin.pace.book

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.File
import java.io.FileNotFoundException

/** Bump when parsing changes, so that cached books are parsed again. */
private const val PARSER_VERSION = 1

enum class BookError {
    /** The file was moved or deleted, or the app lost permission to read it. */
    Missing,

    /** The file is not a book this app can read, or it is damaged. */
    Unreadable,

    /** The book contains no text, e.g. only images or a scanned PDF. */
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

    /**
     * Loads the book at [uri], from the cache if the file has not changed since it was parsed.
     * Parsing a PDF reports its progress from 0 to 1 to [onProgress], on a background thread.
     */
    suspend fun load(
        uri: Uri,
        onProgress: (Float) -> Unit = {},
    ): Book = withContext(Dispatchers.IO) {
        val file = fileInfo(uri)
        val source = "$PARSER_VERSION:${file.size}:${file.lastModified}"
        cache.read(uri.toString(), source)?.let { return@withContext it }

        val book =
            try {
                val stream = resolver.openInputStream(uri) ?: throw BookException(BookError.Missing)
                stream.buffered().use { parse(it, onProgress) }
            } catch (exception: BookException) {
                throw exception
            } catch (exception: FileNotFoundException) {
                throw BookException(BookError.Missing, exception)
            } catch (exception: SecurityException) {
                throw BookException(BookError.Missing, exception)
            } catch (exception: Exception) {
                // Damaged files can make parsers fail in unexpected ways; that must not crash the app.
                throw BookException(BookError.Unreadable, exception)
            } catch (error: VirtualMachineError) {
                // Huge or deeply nested PDFs can exhaust memory or the stack; what was allocated is garbage now.
                throw BookException(BookError.Unreadable, error)
            }
        if (book.blocks.isEmpty()) throw BookException(BookError.NoText)

        val named = if (book.title.isBlank()) book.copy(title = file.name.substringBeforeLast('.')) else book
        runCatching { cache.write(uri.toString(), source, named) }
        named
    }

    private fun parse(
        input: BufferedInputStream,
        onProgress: (Float) -> Unit,
    ): Book {
        input.mark(SIGNATURE_SIZE)
        val header = ByteArray(SIGNATURE_SIZE)
        val read = input.read(header)
        input.reset()
        return when {
            read == SIGNATURE_SIZE && header.contentEquals(ZIP_SIGNATURE) -> EpubParser.parse(input)

            read == SIGNATURE_SIZE && header.contentEquals(PDF_SIGNATURE) -> {
                if (!PDFBoxResourceLoader.isReady()) PDFBoxResourceLoader.init(context)
                PdfParser.parse(input, File(context.cacheDir, "pdf"), onProgress)
            }

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

private const val SIGNATURE_SIZE = 4

/** Every EPUB is a ZIP archive, which starts with these bytes. */
private val ZIP_SIGNATURE = byteArrayOf(0x50, 0x4B, 0x03, 0x04)

/** "%PDF", the start of every PDF. */
private val PDF_SIGNATURE = "%PDF".toByteArray()
