package io.github.nanopenguin.pace

import android.content.Context
import androidx.datastore.core.DataStoreFactory
import androidx.datastore.dataStoreFile
import io.github.nanopenguin.pace.book.BookCache
import io.github.nanopenguin.pace.book.BookRepository
import io.github.nanopenguin.pace.library.LibrarySerializer
import io.github.nanopenguin.pace.library.LibraryStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import java.io.File

/** The app's long-lived objects, wired by hand. */
class AppContainer(
    context: Context,
) {
    /** For work that must finish even if the screen that started it is gone, like saving a position. */
    val scope = CoroutineScope(SupervisorJob())

    val books = BookRepository(context, BookCache(File(context.cacheDir, "books")))

    val library = LibraryStore(DataStoreFactory.create(LibrarySerializer) { context.dataStoreFile("library.json") })
}
