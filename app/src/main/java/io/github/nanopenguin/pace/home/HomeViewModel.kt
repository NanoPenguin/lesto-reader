package io.github.nanopenguin.pace.home

import android.net.Uri
import androidx.core.net.toUri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.nanopenguin.pace.book.BookRepository
import io.github.nanopenguin.pace.library.LibraryEntry
import io.github.nanopenguin.pace.library.LibraryStore
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class HomeUiState(
    /** False until the library has been read, so an empty library is not flashed on start. */
    val isLoaded: Boolean,
    /** Most recently opened first. */
    val books: List<LibraryEntry>,
)

class HomeViewModel(
    private val library: LibraryStore,
    private val books: BookRepository,
) : ViewModel() {
    val uiState: StateFlow<HomeUiState> =
        library.entries
            .map { HomeUiState(isLoaded = true, books = it) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState(isLoaded = false, books = emptyList()))

    fun keepAccess(uri: Uri) = books.keepAccess(uri)

    fun remove(entry: LibraryEntry) {
        viewModelScope.launch {
            library.remove(entry.uri)
            books.forget(entry.uri.toUri())
        }
    }
}
