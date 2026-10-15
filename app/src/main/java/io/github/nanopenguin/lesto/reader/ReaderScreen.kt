package io.github.nanopenguin.lesto.reader

import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animate
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
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.keepScreenOn
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.rememberViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.nanopenguin.lesto.R
import io.github.nanopenguin.lesto.appContainer
import io.github.nanopenguin.lesto.settings.TextSize
import io.github.nanopenguin.lesto.ui.BackButton
import io.github.nanopenguin.lesto.ui.theme.LestoTheme
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** How far the paused page can be dragged past its first or last line and word. */
private val Overscroll = 32.dp

/** Duration of the sweep that zooms the page back to its original size before playing. */
private const val SWEEP_IN_MILLIS = 300

/** Enough to keep every line of a fully zoomed-out page measured, and the current one highlighted. */
private const val MEASURE_CACHE_SIZE = 64

/** Reads the book at [uri]. Its view model, and with it the book, lives as long as this screen. */
@Composable
fun ReaderScreen(
    uri: Uri,
    onClose: () -> Unit,
) {
    val container = LocalContext.current.appContainer
    val viewModel =
        viewModel(viewModelStoreOwner = rememberViewModelStoreOwner()) {
            ReaderViewModel(uri, container.books, container.library, container.settings, container.scope)
        }
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LifecycleEventEffect(Lifecycle.Event.ON_PAUSE) { viewModel.pause() }
    DisposableEffect(viewModel) { onDispose { viewModel.pause() } }

    ReaderScreen(
        state = state,
        onTogglePlayback = viewModel::togglePlayback,
        onSeek = viewModel::seekTo,
        onPause = viewModel::pause,
        onPageZoomChange = viewModel::setPageZoom,
        currentPage = { viewModel.uiState.value.page },
        onJumpToChapter = viewModel::jumpToChapter,
        onSlower = viewModel::slower,
        onFaster = viewModel::faster,
        onShowContextChange = viewModel::setShowContext,
        onRemove = { viewModel.removeFromLibrary(onRemoved = onClose) },
        onClose = onClose,
    )
}

@Composable
private fun ReaderScreen(
    state: ReaderUiState,
    onTogglePlayback: () -> Unit,
    onSeek: (Int) -> Unit,
    onPause: () -> Unit,
    onPageZoomChange: (Float) -> Unit,
    /** The latest page, read directly so that quick successive moves never see a stale one. */
    currentPage: () -> TextPage?,
    onJumpToChapter: (Int) -> Unit,
    onSlower: () -> Unit,
    onFaster: () -> Unit,
    onShowContextChange: (Boolean) -> Unit,
    onRemove: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    state.error?.let {
        ReaderError(it, if (state.canRemove) onRemove else null, onClose)
        return
    }
    if (state.isLoading) {
        Box(modifier = modifier.fillMaxSize()) {
            val progress = state.loadingProgress
            if (progress != null) {
                CircularProgressIndicator(progress = { progress }, modifier = Modifier.align(Alignment.Center))
            } else {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            }
        }
        return
    }

    var showChapters by rememberSaveable { mutableStateOf(false) }
    val wordSize = state.textSize.wordSize
    val wordStyle = MaterialTheme.typography.displaySmall.copy(fontSize = wordSize)
    val lineHeight = with(LocalDensity.current) { wordSize.toPx() } * LINE_SPACING
    val measurer = rememberTextMeasurer(cacheSize = MEASURE_CACHE_SIZE)
    val scroll = remember { PageScroll() }

    val currentOnPageZoomChange by rememberUpdatedState(onPageZoomChange)
    LaunchedEffect(scroll) { snapshotFlow { scroll.zoom }.collect { currentOnPageZoomChange(it) } }

    // Zoomed out, playing starts with a sweep back to the original size, so the word to read stays where it is.
    val scope = rememberCoroutineScope()
    var sweepIn by remember { mutableStateOf<Job?>(null) }
    val togglePlayback: () -> Unit = {
        when {
            sweepIn?.isActive == true -> Unit

            state.isPlaying || scroll.zoom == 1f -> onTogglePlayback()

            else ->
                sweepIn =
                    scope.launch {
                        animate(scroll.zoom, 1f, animationSpec = tween(SWEEP_IN_MILLIS)) { value, _ -> scroll.zoom = value }
                        onTogglePlayback()
                    }
        }
    }

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

        // Unless a gesture is moving it, the page shows the current word on the focal point, placed
        // before the frame is drawn: on opening, pausing or a jump, its first frame is already in place.
        val isScrolling = scroll.isScrolling
        SideEffect {
            if (targetScrollX != null && !isScrolling) scroll.scrollX = targetScrollX
        }

        val overscroll = with(LocalDensity.current) { Overscroll.toPx() }
        val navigator = PageNavigator(geometry, scroll, currentPage, onSeek, overscroll)

        // The gesture detector lives across recompositions, so it reads the latest callbacks.
        val currentOnTap by rememberUpdatedState(togglePlayback)
        val currentOnPause by rememberUpdatedState(onPause)
        val currentNavigator by rememberUpdatedState(navigator)

        val playbackLabel = stringResource(if (state.isPlaying) R.string.reader_pause else R.string.reader_play)
        Box(
            modifier =
            Modifier
                .fillMaxSize()
                // Tapping anywhere plays or pauses; screen readers need that as an action.
                .semantics {
                    onClick(label = playbackLabel) {
                        currentOnTap()
                        true
                    }
                }
                .readerGestures(
                    haptics = LocalHapticFeedback.current,
                    scroll = scroll,
                    onTap = { currentOnTap() },
                    onGestureStart = {
                        // Taking hold of the page stops the sweep, and with it the playback it leads to.
                        sweepIn?.cancel()
                        currentOnPause()
                        currentNavigator.start()
                    },
                    onMove = { currentNavigator.move(it) },
                    onZoom = { currentNavigator.zoom(it) },
                    restingPlace = { currentNavigator.restingPlace() },
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
                wide = constraints.maxWidth > constraints.maxHeight,
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

private val MIN_HEADING_FONT_SIZE = 16.sp

@Composable
private fun HeadingText(
    text: String,
    modifier: Modifier = Modifier,
) {
    val style = MaterialTheme.typography.headlineMedium
    Text(
        text = text,
        style = style,
        fontWeight = FontWeight.SemiBold,
        textAlign = TextAlign.Center,
        maxLines = 4,
        // Long chapter titles shrink to fit rather than lose their last words.
        autoSize = TextAutoSize.StepBased(minFontSize = MIN_HEADING_FONT_SIZE, maxFontSize = style.fontSize),
        overflow = TextOverflow.Ellipsis,
        modifier = modifier.padding(horizontal = 32.dp),
    )
}

@Preview(showBackground = true)
@Composable
private fun ReaderScreenPreview() {
    LestoTheme {
        ReaderScreen(
            state =
            ReaderUiState(
                isLoading = false,
                loadingProgress = null,
                error = null,
                canRemove = false,
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
                textSize = TextSize.Medium,
                showContext = false,
            ),
            onTogglePlayback = {},
            onSeek = {},
            onPause = {},
            onPageZoomChange = {},
            currentPage = { null },
            onJumpToChapter = {},
            onSlower = {},
            onFaster = {},
            onShowContextChange = {},
            onRemove = {},
            onClose = {},
        )
    }
}
