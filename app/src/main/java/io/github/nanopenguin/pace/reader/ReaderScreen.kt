package io.github.nanopenguin.pace.reader

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.keepScreenOn
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.nanopenguin.pace.book.SampleBook
import io.github.nanopenguin.pace.ui.BackButton
import io.github.nanopenguin.pace.ui.theme.PaceTheme

private val WordFontSize = 40.sp

@Composable
fun ReaderScreen(
    onClose: () -> Unit,
    viewModel: ReaderViewModel = viewModel { ReaderViewModel(SampleBook) },
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LifecycleEventEffect(Lifecycle.Event.ON_PAUSE) { viewModel.pause() }
    DisposableEffect(viewModel) { onDispose { viewModel.pause() } }

    ReaderScreen(
        state = state,
        onTogglePlayback = viewModel::togglePlayback,
        onSeek = viewModel::seekTo,
        onPause = viewModel::pause,
        onStepWords = viewModel::stepWords,
        onStepSentences = viewModel::stepSentences,
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
    onStepWords: (Int) -> Unit,
    onStepSentences: (Int) -> Unit,
    onJumpToChapter: (Int) -> Unit,
    onSlower: () -> Unit,
    onFaster: () -> Unit,
    onShowContextChange: (Boolean) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showChapters by rememberSaveable { mutableStateOf(false) }
    val wordStyle = MaterialTheme.typography.displaySmall.copy(fontSize = WordFontSize)
    val lineHeight = with(LocalDensity.current) { WordFontSize.toPx() } * LINE_SPACING
    val verticalOffset = remember { mutableFloatStateOf(0f) }

    // The gesture detector lives across recompositions, so it reads the latest callbacks.
    val currentOnTap by rememberUpdatedState(onTogglePlayback)
    val currentOnPause by rememberUpdatedState(onPause)
    val currentOnStepWords by rememberUpdatedState(onStepWords)
    val currentOnStepSentences by rememberUpdatedState(onStepSentences)

    Box(
        modifier =
        modifier
            .fillMaxSize()
            .then(if (state.isPlaying) Modifier.keepScreenOn() else Modifier)
            .readerGestures(
                haptics = LocalHapticFeedback.current,
                lineHeight = lineHeight,
                verticalOffset = verticalOffset,
                onTap = { currentOnTap() },
                onDragStart = { currentOnPause() },
                onStepWords = { currentOnStepWords(it) },
                onStepSentences = { currentOnStepSentences(it) },
            ).safeDrawingPadding(),
    ) {
        val frame = state.frame
        val page = state.page
        when {
            page != null -> SentenceLines(page, wordStyle, verticalOffset = { verticalOffset.floatValue })

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
                bookTitle = "Alice’s Adventures in Wonderland",
                frame = Frame("considering", isHeading = false, Pause.None, blockIndex = 0),
                wordsBefore = emptyList(),
                wordsAfter = emptyList(),
                page =
                TextPage(
                    current = "So she was considering in her own mind".split(" "),
                    currentWord = 3,
                    before = listOf("Alice was beginning to get very tired of sitting by her sister on the bank"),
                    after = listOf("There was nothing so very remarkable in that;"),
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
            onStepWords = {},
            onStepSentences = {},
            onJumpToChapter = {},
            onSlower = {},
            onFaster = {},
            onShowContextChange = {},
            onClose = {},
        )
    }
}
