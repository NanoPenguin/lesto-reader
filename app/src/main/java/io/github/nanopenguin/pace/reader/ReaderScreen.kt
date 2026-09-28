package io.github.nanopenguin.pace.reader

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.keepScreenOn
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.nanopenguin.pace.R
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
        onPreviousSentence = viewModel::previousSentence,
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
    onPreviousSentence: () -> Unit,
    onSlower: () -> Unit,
    onFaster: () -> Unit,
    onShowContextChange: (Boolean) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier =
        modifier
            .fillMaxSize()
            .then(if (state.isPlaying) Modifier.keepScreenOn() else Modifier)
            .clickable(interactionSource = null, indication = null, onClick = onTogglePlayback)
            .safeDrawingPadding(),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.align(Alignment.Center),
        ) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.heightIn(min = 120.dp)) {
                val frame = state.frame
                when {
                    frame == null -> Unit

                    frame.isHeading -> HeadingText(frame.text)

                    else -> FocusWord(
                        word = frame.text,
                        wordsBefore = state.wordsBefore,
                        wordsAfter = state.wordsAfter,
                        style = MaterialTheme.typography.displaySmall.copy(fontSize = WordFontSize),
                    )
                }
            }
            // Kept in the layout while playing so the word does not jump when pausing.
            Text(
                text = stringResource(R.string.reader_tap_to_read),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.alpha(if (state.isPlaying) 0f else 1f),
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
                onPreviousSentence = onPreviousSentence,
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
                    .fillMaxWidth(state.progress)
                    .height(2.dp)
                    .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)),
            )
        }
    }
}

@Composable
private fun HeadingText(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.headlineMedium,
        fontWeight = FontWeight.SemiBold,
        textAlign = TextAlign.Center,
        maxLines = 4,
        modifier = Modifier.padding(horizontal = 32.dp),
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
                chapterTitle = "Down the Rabbit-Hole",
                frame = Frame("considering", isHeading = false, Pause.None, blockIndex = 0),
                wordsBefore = listOf("So", "she", "was"),
                wordsAfter = listOf("in", "her", "own", "mind"),
                position = 40,
                lastPosition = 200,
                minutesLeft = 3,
                isPlaying = false,
                wordsPerMinute = 300,
                showContext = true,
            ),
            onTogglePlayback = {},
            onSeek = {},
            onPreviousSentence = {},
            onSlower = {},
            onFaster = {},
            onShowContextChange = {},
            onClose = {},
        )
    }
}
