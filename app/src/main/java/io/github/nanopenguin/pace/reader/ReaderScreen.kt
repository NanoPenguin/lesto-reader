package io.github.nanopenguin.pace.reader

import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.keepScreenOn
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.rememberViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.nanopenguin.pace.R
import io.github.nanopenguin.pace.appContainer
import io.github.nanopenguin.pace.book.BookError
import io.github.nanopenguin.pace.ui.BackButton
import io.github.nanopenguin.pace.ui.theme.PaceTheme
import kotlin.math.abs

private val WordFontSize = 40.sp

/** Duration of the page's sideways glide to the current word. */
private const val PAN_MILLIS = 200

/** Reads the book at [uri]. Its view model, and with it the book, lives as long as this screen. */
@Composable
fun ReaderScreen(
    uri: Uri,
    onClose: () -> Unit,
) {
    val container = LocalContext.current.appContainer
    val viewModel =
        viewModel(viewModelStoreOwner = rememberViewModelStoreOwner()) {
            ReaderViewModel(uri, container.books, container.library, container.scope)
        }
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LifecycleEventEffect(Lifecycle.Event.ON_PAUSE) { viewModel.pause() }
    DisposableEffect(viewModel) { onDispose { viewModel.pause() } }

    ReaderScreen(
        state = state,
        onTogglePlayback = viewModel::togglePlayback,
        onSeek = viewModel::seekTo,
        onPause = viewModel::pause,
        onStepWords = viewModel::stepWords,
        currentPage = { viewModel.uiState.value.page },
        onJumpToChapter = viewModel::jumpToChapter,
        onSlower = viewModel::slower,
        onFaster = viewModel::faster,
        onShowContextChange = viewModel::setShowContext,
        onClose = onClose,
    )
}

@Composable
private fun ReaderScreen(
    state: ReaderUiState,
    onTogglePlayback: () -> Unit,
    onSeek: (Int) -> Unit,
    onPause: () -> Unit,
    onStepWords: (Int) -> Boolean,
    /** The latest page, read directly so that quick successive line steps never see a stale one. */
    currentPage: () -> TextPage?,
    onJumpToChapter: (Int) -> Unit,
    onSlower: () -> Unit,
    onFaster: () -> Unit,
    onShowContextChange: (Boolean) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    state.error?.let {
        ReaderError(it, onClose)
        return
    }
    if (state.isLoading) {
        Box(modifier = modifier.fillMaxSize()) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
        }
        return
    }

    var showChapters by rememberSaveable { mutableStateOf(false) }
    val wordStyle = MaterialTheme.typography.displaySmall.copy(fontSize = WordFontSize)
    val lineHeight = with(LocalDensity.current) { WordFontSize.toPx() } * LINE_SPACING
    val measurer = rememberTextMeasurer(cacheSize = 32)
    val scroll = remember { PageScroll() }

    BoxWithConstraints(
        modifier =
        modifier
            .fillMaxSize()
            .then(if (state.isPlaying) Modifier.keepScreenOn() else Modifier)
            .safeDrawingPadding(),
    ) {
        val focalX = constraints.maxWidth * FOCAL_LINE
        val geometry = remember(measurer, wordStyle, focalX, lineHeight) { PageGeometry(measurer, wordStyle, focalX, lineHeight) }
        val page = state.page
        val targetScrollX = page?.let { geometry.scrollXFor(it.lines[it.currentLine], it.currentWord) }

        // Pans the page to the current word, but only once vertical scrolling has settled.
        LaunchedEffect(targetScrollX, scroll.isScrolling) {
            when {
                targetScrollX == null -> scroll.isPlaced = false

                scroll.isScrolling -> Unit

                !scroll.isPlaced || abs(targetScrollX - scroll.scrollX.value) > focalX -> {
                    scroll.scrollX.snapTo(targetScrollX)
                    scroll.isPlaced = true
                }

                else -> scroll.scrollX.animateTo(targetScrollX, tween(PAN_MILLIS))
            }
        }

        // Distance to the line above (-1) or below (1), if there is one.
        val lineDistance = { direction: Int ->
            currentPage()?.let { page ->
                geometry.lineOffsets(page).getOrNull(page.currentLine + direction)?.let(::abs)
            }
        }

        // Moves to the word under the focal point on the line above or below.
        val stepLine = { direction: Int ->
            val current = currentPage()
            val line = current?.lines?.getOrNull(current.currentLine + direction)
            if (line != null) onSeek(line.firstFrame + geometry.wordAt(line, scroll.scrollX.value))
        }

        // The gesture detector lives across recompositions, so it reads the latest callbacks.
        val currentOnTap by rememberUpdatedState(onTogglePlayback)
        val currentOnPause by rememberUpdatedState(onPause)
        val currentOnStepWords by rememberUpdatedState(onStepWords)
        val currentLineDistance by rememberUpdatedState(lineDistance)
        val currentStepLine by rememberUpdatedState(stepLine)

        Box(
            modifier =
            Modifier
                .fillMaxSize()
                .readerGestures(
                    haptics = LocalHapticFeedback.current,
                    scroll = scroll,
                    onTap = { currentOnTap() },
                    onDragStart = { currentOnPause() },
                    onStepWords = { currentOnStepWords(it) },
                    lineDistance = { currentLineDistance(it) },
                    onStepLine = { currentStepLine(it) },
                ),
        ) {
            val frame = state.frame
            when {
                page != null -> SentenceLines(page, geometry, scroll)

                frame == null -> Unit

                frame.isHeading -> HeadingText(frame.text, Modifier.align(Alignment.Center))

                else -> FocusWord(
                    word = frame.text,
                    wordsBefore = state.wordsBefore,
                    wordsAfter = state.wordsAfter,
                    style = wordStyle,
                    modifier = Modifier.align(Alignment.Center),
                )
            }
        }

        AnimatedVisibility(
            visible = !state.isPlaying,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.TopStart),
        ) {
            BackButton(onClick = onClose)
        }

        AnimatedVisibility(
            visible = !state.isPlaying,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            ReaderControls(
                state = state,
                onSeek = onSeek,
                onOpenChapters = { showChapters = true },
                onSlower = onSlower,
                onFaster = onFaster,
                onShowContextChange = onShowContextChange,
            )
        }

        if (state.isPlaying) {
            Box(
                modifier =
                Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth(state.bookProgress)
                    .height(2.dp)
                    .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)),
            )
        }
    }

    if (showChapters) {
        ChapterSheet(
            chapters = state.chapters,
            currentChapter = state.chapterIndex,
            onSelect = {
                onJumpToChapter(it)
                showChapters = false
            },
            onDismiss = { showChapters = false },
        )
    }
}

@Composable
private fun ReaderError(
    error: BookError,
    onClose: () -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize().safeDrawingPadding()) {
        BackButton(onClick = onClose, modifier = Modifier.align(Alignment.TopStart))
        Text(
            text =
            stringResource(
                when (error) {
                    BookError.Missing -> R.string.reader_error_missing
                    BookError.Unreadable -> R.string.reader_error_unreadable
                    BookError.NoText -> R.string.reader_error_no_text
                },
            ),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.align(Alignment.Center).padding(horizontal = 32.dp),
        )
    }
}

@Composable
private fun HeadingText(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.headlineMedium,
        fontWeight = FontWeight.SemiBold,
        textAlign = TextAlign.Center,
        maxLines = 4,
        modifier = modifier.padding(horizontal = 32.dp),
    )
}

@Preview(showBackground = true)
@Composable
private fun ReaderScreenPreview() {
    PaceTheme {
        ReaderScreen(
            state =
            ReaderUiState(
                isLoading = false,
                error = null,
                bookTitle = "Alice’s Adventures in Wonderland",
                frame = Frame("considering", isHeading = false, Pause.None, blockIndex = 0),
                wordsBefore = emptyList(),
                wordsAfter = emptyList(),
                page =
                TextPage(
                    lines =
                    listOf(
                        PageLine(listOf("Down the Rabbit-Hole"), firstFrame = 0, isHeading = true),
                        PageLine("So she was considering in her own mind".split(" "), firstFrame = 1, isHeading = false),
                        PageLine("There was nothing so very remarkable in that;".split(" "), firstFrame = 9, isHeading = false),
                    ),
                    currentLine = 1,
                    currentWord = 3,
                ),
                chapters = listOf(Chapter("Down the Rabbit-Hole", level = 1, firstFrame = 0)),
                chapterIndex = 0,
                position = 40,
                section = 0..200,
                bookProgress = 0.2f,
                minutesLeftInSection = 3,
                isPlaying = false,
                wordsPerMinute = 300,
                showContext = false,
            ),
            onTogglePlayback = {},
            onSeek = {},
            onPause = {},
            onStepWords = { false },
            currentPage = { null },
            onJumpToChapter = {},
            onSlower = {},
            onFaster = {},
            onShowContextChange = {},
            onClose = {},
        )
    }
}
