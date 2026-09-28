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
import kotlin.math.abs
import kotlin.math.ceil

private const val DEFAULT_WORDS_PER_MINUTE = 300
private const val SPEED_STEP = 25
private val SpeedRange = 100..1000

/** How many neighbouring words are offered on each side; the screen shows as many as fit. */
private const val CONTEXT_WORD_COUNT = 8

/** The sentence preview is cut to this many words around the current one, so it stays short. */
private const val PREVIEW_WORDS_BEFORE = 10
private const val PREVIEW_WORDS_AFTER = 14

/** The current sentence while paused, cut around [currentWord] if long. */
data class SentencePreview(
    val words: List<String>,
    val currentWord: Int,
)

data class ReaderUiState(
    val bookTitle: String,
    /** The frame on screen, or null if the book has no text. */
    val frame: Frame?,
    val wordsBefore: List<String>,
    val wordsAfter: List<String>,
    /** The sentence around the current word; null for headings. Shown only while paused. */
    val preview: SentencePreview?,
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
)

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

    /** Moves [count] words forward, or back if negative. Pauses playback. */
    fun stepWords(count: Int) {
        pause()
        seekTo(position + count)
    }

    /** Moves [count] sentences forward, or back if negative. Pauses playback. */
    fun stepSentences(count: Int) {
        pause()
        if (text.frames.isEmpty()) return
        var target = position
        repeat(abs(count)) {
            target = if (count < 0) text.previousSentenceStart(target) else text.nextSentenceStart(target)
        }
        seekTo(target)
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
        val frame = text.frames.getOrNull(position)
        val section = if (frame != null) text.sectionAt(position) else 0..0
        val secondsLeft =
            if (frame != null) text.units(position..section.last) * millisPerUnit(wordsPerMinute) / 1000 else 0.0
        return ReaderUiState(
            bookTitle = book.title,
            frame = frame,
            wordsBefore = if (frame != null && showContext) text.wordsBefore(position, CONTEXT_WORD_COUNT) else emptyList(),
            wordsAfter = if (frame != null && showContext) text.wordsAfter(position, CONTEXT_WORD_COUNT) else emptyList(),
            preview = if (frame != null && !frame.isHeading) sentencePreview() else null,
            chapters = text.chapters,
            chapterIndex = text.chapterIndexAt(position),
            position = position,
            section = section,
            bookProgress = if (text.lastIndex > 0) position.toFloat() / text.lastIndex else 0f,
            minutesLeftInSection = ceil(secondsLeft / 60).toInt(),
            isPlaying = playback != null,
            wordsPerMinute = wordsPerMinute,
            showContext = showContext,
        )
    }

    private fun sentencePreview(): SentencePreview {
        val sentence = text.sentenceAt(position)
        val first = maxOf(sentence.first, position - PREVIEW_WORDS_BEFORE)
        val last = minOf(sentence.last, position + PREVIEW_WORDS_AFTER)
        val words = (first..last).map { text.frames[it].text }
        return SentencePreview(
            words = listOfNotNull("…".takeIf { first > sentence.first }) + words + listOfNotNull("…".takeIf { last < sentence.last }),
            currentWord = position - first + if (first > sentence.first) 1 else 0,
        )
    }
}
