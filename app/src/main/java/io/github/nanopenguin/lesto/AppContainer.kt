package io.github.nanopenguin.lesto

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.DataStoreFactory
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.dataStoreFile
import io.github.nanopenguin.lesto.book.BookCache
import io.github.nanopenguin.lesto.book.BookRepository
import io.github.nanopenguin.lesto.library.LibrarySerializer
import io.github.nanopenguin.lesto.library.LibraryStore
import io.github.nanopenguin.lesto.settings.SettingsSerializer
import io.github.nanopenguin.lesto.settings.SettingsStore
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

    val library = LibraryStore(context.jsonDataStore("library.json", LibrarySerializer))

    val settings = SettingsStore(context.jsonDataStore("settings.json", SettingsSerializer))
}

/** A damaged file starts over with the default value rather than failing every time it is read. */
private fun <T> Context.jsonDataStore(
    fileName: String,
    serializer: JsonSerializer<T>,
): DataStore<T> = DataStoreFactory.create(serializer, ReplaceFileCorruptionHandler { serializer.defaultValue }) {
    dataStoreFile(fileName)
}
