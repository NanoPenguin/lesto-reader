package io.github.nanopenguin.lesto.reader

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.nanopenguin.lesto.book.BookError
import io.github.nanopenguin.lesto.book.BookException
import io.github.nanopenguin.lesto.book.BookRepository
import io.github.nanopenguin.lesto.library.LibraryStore
import io.github.nanopenguin.lesto.settings.DEFAULT_WORDS_PER_MINUTE
import io.github.nanopenguin.lesto.settings.SPEED_STEP
import io.github.nanopenguin.lesto.settings.SettingsStore
import io.github.nanopenguin.lesto.settings.SpeedRange
import io.github.nanopenguin.lesto.settings.TextSize
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.ceil

/** How many neighbouring words are offered on each side; the screen shows as many as fit. */
private const val CONTEXT_WORD_COUNT = 8

/** Lines shown above and below the current one while paused; the screen fades out the rest. */
private const val PAGE_LINES = 6

/** Sentences longer than this are split over several page lines. */
private const val MAX_LINE_WORDS = 200

/** The position is saved once it has stopped changing for this long. */
private const val SAVE_DELAY_MILLIS = 1000L

data class ReaderUiState(
    val isLoading: Boolean,
    /** How far a first parse has come, from 0 to 1, when that is known. */
    val loadingProgress: Float?,
    /** Why the book could not be opened, if it could not. */
    val error: BookError?,
    /** Whether the book that could not be opened is in the library, and can be removed from it. */
    val canRemove: Boolean,
    val bookTitle: String,
    /** The frame on screen, or null if the book has no text. */
    val frame: Frame?,
    val wordsBefore: List<String>,
    val wordsAfter: List<String>,
    /** Only while paused. */
    val page: TextPage?,
    val chapters: List<Chapter>,
    /** Index into [chapters], or null before the first heading. */
    val chapterIndex: Int?,
    val position: Int,
    /** The frames the slider covers: the current chapter, or the whole book without headings. */
    val section: IntRange,
    val bookProgress: Float,
    val minutesLeftInSection: Int,
    val isPlaying: Boolean,
    val wordsPerMinute: Int,
    val showContext: Boolean,
    val textSize: TextSize,
)

/**
 * Plays the book at [uri] and keeps its reading position in the [library]. Speed and context words
 * start from the [settings], and changes to them are saved there.
 */
class ReaderViewModel(
    private val uri: Uri,
    private val books: BookRepository,
    private val library: LibraryStore,
    private val settings: SettingsStore,
    /** Outlives this view model, so the last position is saved even as the reader closes. */
    private val appScope: CoroutineScope,
) : ViewModel() {
    private var isLoading = true

    /** Written by the parser's thread. */
    @Volatile private var loadingProgress: Float? = null
    private var error: BookError? = null
    private var canRemove = false
    private var bookTitle = ""
    private var text = RsvpText(emptyList(), emptyList())
    private var position = 0
    private var savedPosition = 0
    private var wordsPerMinute = DEFAULT_WORDS_PER_MINUTE
    private var showContext = false
    private var textSize = TextSize.Medium
    private var playback: Job? = null
    private var pendingSave: Job? = null

    private val _uiState = MutableStateFlow(render())
    val uiState: StateFlow<ReaderUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch { load() }
    }

    private suspend fun load() {
        settings.current().let {
            wordsPerMinute = it.wordsPerMinute.coerceIn(SpeedRange)
            showContext = it.showContext
            textSize = it.textSize
        }
        try {
            val book =
                books.load(uri) { progress ->
                    loadingProgress = progress
                    _uiState.update { it.copy(loadingProgress = progress) }
                }
            text = withContext(Dispatchers.Default) { book.toRsvpText() }
            bookTitle = book.title
            position = library.open(uri.toString(), book.title, book.author, text.frames.size)
            savedPosition = position
        } catch (exception: BookException) {
            error = exception.error
            canRemove = library.entries.first().any { it.uri == uri.toString() }
        }
        isLoading = false
        publish()
    }

    override fun onCleared() {
        if (position != savedPosition) {
            val last = position
            appScope.launch { library.savePosition(uri.toString(), last) }
        }
    }

    /** Removes the book that could not be opened from the library, then calls [onRemoved]. */
    fun removeFromLibrary(onRemoved: () -> Unit) {
        appScope.launch {
            library.remove(uri.toString())
            books.forget(uri)
        }
        onRemoved()
    }

    fun togglePlayback() {
        if (playback != null) pause() else play()
    }

    fun pause() {
        playback?.cancel()
        playback = null
        publish()
    }

    fun seekTo(index: Int) {
        position = index.coerceIn(0, text.lastIndex.coerceAtLeast(0))
        publish()
    }

    fun jumpToChapter(chapterIndex: Int) {
        pause()
        seekTo(text.chapters[chapterIndex].firstFrame)
    }

    fun slower() = setSpeed(wordsPerMinute - SPEED_STEP)

    fun faster() = setSpeed(wordsPerMinute + SPEED_STEP)

    fun setShowContext(show: Boolean) {
        showContext = show
        publish()
        appScope.launch { settings.update { it.copy(showContext = show) } }
    }

    private fun setSpeed(value: Int) {
        wordsPerMinute = value.coerceIn(SpeedRange)
        publish()
        val saved = wordsPerMinute
        appScope.launch { settings.update { it.copy(wordsPerMinute = saved) } }
    }

    private fun play() {
        if (text.frames.isEmpty()) return
        if (position == text.lastIndex) position = 0

        // The current frame is shown again first, eased in, before moving on.
        playback =
            viewModelScope.launch {
                var framesSincePlay = 0
                while (true) {
                    delay(displayMillis(text.frames[position], wordsPerMinute, framesSincePlay++))
                    if (position == text.lastIndex) break
                    position++
                    publish()
                }
                playback = null
                publish()
            }
        publish()
    }

    private fun publish() {
        _uiState.value = render()
        if (position != savedPosition) scheduleSave()
    }

    private fun scheduleSave() {
        pendingSave?.cancel()
        pendingSave =
            viewModelScope.launch {
                delay(SAVE_DELAY_MILLIS)
                library.savePosition(uri.toString(), position)
                savedPosition = position
            }
    }

    private fun render(): ReaderUiState {
        val frame = text.frames.getOrNull(position)
        val section = if (frame != null) text.sectionAt(position) else 0..0
        val secondsLeft =
            if (frame != null) text.units(position..section.last) * millisPerUnit(wordsPerMinute) / 1000 else 0.0
        val hasContext = frame != null && showContext
        return ReaderUiState(
            isLoading = isLoading,
            loadingProgress = loadingProgress,
            error = error,
            canRemove = canRemove,
            bookTitle = bookTitle,
            frame = frame,
            wordsBefore = if (hasContext) text.wordsBefore(position, CONTEXT_WORD_COUNT) else emptyList(),
            wordsAfter = if (hasContext) text.wordsAfter(position, CONTEXT_WORD_COUNT) else emptyList(),
            page = if (frame != null && playback == null) textPage() else null,
            chapters = text.chapters,
            chapterIndex = text.chapterIndexAt(position),
            position = position,
            section = section,
            bookProgress = if (text.lastIndex > 0) position.toFloat() / text.lastIndex else 0f,
            minutesLeftInSection = ceil(secondsLeft / 60).toInt(),
            isPlaying = playback != null,
            wordsPerMinute = wordsPerMinute,
            showContext = showContext,
            textSize = textSize,
        )
    }

    private fun textPage(): TextPage {
        val current = text.lineAt(position, MAX_LINE_WORDS)

        val before = ArrayDeque<IntRange>()
        while (before.size < PAGE_LINES && (before.firstOrNull() ?: current).first > 0) {
            before.addFirst(text.lineAt((before.firstOrNull() ?: current).first - 1, MAX_LINE_WORDS))
        }
        val after = ArrayDeque<IntRange>()
        while (after.size < PAGE_LINES && (after.lastOrNull() ?: current).last < text.lastIndex) {
            after.addLast(text.lineAt((after.lastOrNull() ?: current).last + 1, MAX_LINE_WORDS))
        }

        return TextPage(
            lines = (before + listOf(current) + after).map(::pageLine),
            currentLine = before.size,
            currentWord = position - current.first,
        )
    }

    private fun pageLine(frames: IntRange) = PageLine(
        words = frames.map { text.frames[it].text },
        firstFrame = frames.first,
        isHeading = text.frames[frames.first].isHeading,
    )
}
