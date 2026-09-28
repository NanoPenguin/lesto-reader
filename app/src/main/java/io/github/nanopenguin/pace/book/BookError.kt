package io.github.nanopenguin.pace.book

enum class BookError {
    /** The file was moved or deleted. */
    Missing,

    /** The app may no longer read the file. Some providers revoke access to deleted files, so it may also be gone. */
    NoAccess,

    /** The file is not a book this app can read, or it is damaged. */
    Unreadable,

    /** The book contains no text, e.g. only images or a scanned PDF. */
    NoText,
}

class BookException(
    val error: BookError,
    cause: Throwable? = null,
) : Exception(error.name, cause)

/** Thrown by parsers when a file is not a book they can read. */
class BookFormatException(
    message: String,
) : Exception(message)
