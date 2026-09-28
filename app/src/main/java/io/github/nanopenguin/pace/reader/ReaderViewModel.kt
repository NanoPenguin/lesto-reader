package io.github.nanopenguin.pace.reader

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.nanopenguin.pace.book.Book
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.math.ceil

private const val DEFAULT_WORDS_PER_MINUTE = 300
private const val SPEED_STEP = 25
private val SpeedRange = 100..1000

/** How many neighbouring words are offered on each side; the screen shows as many as fit. */
private const val CONTEXT_WORD_COUNT = 8

data class ReaderUiState(
    val bookTitle: String,
    val chapterTitle: String?,
    /** The frame on screen, or null if the book has no text. */
    val frame: Frame?,
    val wordsBefore: List<String>,
    val wordsAfter: List<String>,
    val position: Int,
    val lastPosition: Int,
    val minutesLeft: Int,
    val isPlaying: Boolean,
    val wordsPerMinute: Int,
    val showContext: Boolean,
) {
    val progress: Float get() = if (lastPosition > 0) position.toFloat() / lastPosition else 0f
}

class ReaderViewModel(
    private val book: Book,
) : ViewModel() {
    private val text = book.toRsvpText()
    private var position = 0
    private var wordsPerMinute = DEFAULT_WORDS_PER_MINUTE
    private var showContext = false
    private var playback: Job? = null

    private val _uiState = MutableStateFlow(render())
    val uiState: StateFlow<ReaderUiState> = _uiState.asStateFlow()

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

    fun previousSentence() = seekTo(text.previousSentenceStart(position))

    fun slower() = setSpeed(wordsPerMinute - SPEED_STEP)

    fun faster() = setSpeed(wordsPerMinute + SPEED_STEP)

    fun setShowContext(show: Boolean) {
        showContext = show
        publish()
    }

    private fun setSpeed(value: Int) {
        wordsPerMinute = value.coerceIn(SpeedRange)
        publish()
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
    }

    private fun render(): ReaderUiState {
        val hasText = text.frames.isNotEmpty()
        return ReaderUiState(
            bookTitle = book.title,
            chapterTitle = text.chapterAt(position)?.title,
            frame = text.frames.getOrNull(position),
            wordsBefore = if (hasText && showContext) text.wordsBefore(position, CONTEXT_WORD_COUNT) else emptyList(),
            wordsAfter = if (hasText && showContext) text.wordsAfter(position, CONTEXT_WORD_COUNT) else emptyList(),
            position = position,
            lastPosition = text.lastIndex.coerceAtLeast(0),
            minutesLeft = ceil(text.unitsLeft(position) * millisPerUnit(wordsPerMinute) / 60_000).toInt(),
            isPlaying = playback != null,
            wordsPerMinute = wordsPerMinute,
            showContext = showContext,
        )
    }
}
