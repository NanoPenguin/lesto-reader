package io.github.nanopenguin.pace

import android.content.Context
import androidx.datastore.core.DataStoreFactory
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.dataStoreFile
import io.github.nanopenguin.pace.book.BookCache
import io.github.nanopenguin.pace.book.BookRepository
import io.github.nanopenguin.pace.library.Library
import io.github.nanopenguin.pace.library.LibrarySerializer
import io.github.nanopenguin.pace.library.LibraryStore
import io.github.nanopenguin.pace.settings.Settings
import io.github.nanopenguin.pace.settings.SettingsSerializer
import io.github.nanopenguin.pace.settings.SettingsStore
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

    // A damaged file starts over empty rather than failing every time it is read.
    val library =
        LibraryStore(
            DataStoreFactory.create(LibrarySerializer, ReplaceFileCorruptionHandler { Library() }) {
                context.dataStoreFile("library.json")
            },
        )

    val settings =
        SettingsStore(
            DataStoreFactory.create(SettingsSerializer, ReplaceFileCorruptionHandler { Settings() }) {
                context.dataStoreFile("settings.json")
            },
        )
}
