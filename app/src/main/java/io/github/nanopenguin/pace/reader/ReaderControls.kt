package io.github.nanopenguin.pace.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.nanopenguin.pace.R
import io.github.nanopenguin.pace.ui.SpeedControl
import kotlin.math.roundToInt

/**
 * Controls shown while paused: chapter, position within it, speed and context words. When [wide],
 * as in landscape, the position sits between speed and context words, to leave room for the page.
 */
@Composable
fun ReaderControls(
    state: ReaderUiState,
    wide: Boolean,
    onSeek: (Int) -> Unit,
    onOpenChapters: () -> Unit,
    onSlower: () -> Unit,
    onFaster: () -> Unit,
    onShowContextChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Opaque, with a soft top edge, so the paused page's lines slide under the controls.
    val background = MaterialTheme.colorScheme.surface
    Column(
        modifier =
        modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(0f to background.copy(alpha = 0f), 0.15f to background))
            .padding(start = 24.dp, end = 24.dp, top = 32.dp, bottom = 16.dp),
    ) {
        ChapterTitle(state, onOpenChapters)

        if (wide) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SpeedControl(state.wordsPerMinute, onSlower, onFaster)
                Position(state, onSeek, modifier = Modifier.weight(1f).padding(horizontal = 16.dp))
                ContextToggle(state.showContext, onShowContextChange)
            }
        } else {
            Position(state, onSeek)
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
                SpeedControl(state.wordsPerMinute, onSlower, onFaster)
                Spacer(modifier = Modifier.weight(1f))
                ContextToggle(state.showContext, onShowContextChange)
            }
        }
    }
}

/** A slider over the current section, with the progress and time left below it. */
@Composable
private fun Position(
    state: ReaderUiState,
    onSeek: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val percent = (state.bookProgress * 100).roundToInt()

    Column(modifier = modifier) {
        val sliderLabel = stringResource(R.string.reader_position)
        Slider(
            modifier = Modifier.semantics { contentDescription = sliderLabel },
            value = state.position.toFloat(),
            onValueChange = { onSeek(it.roundToInt()) },
            valueRange = state.section.first.toFloat()..maxOf(state.section.last, state.section.first + 1).toFloat(),
        )

        Row {
            Text(
                text =
                if (state.chapterIndex != null) {
                    stringResource(R.string.reader_chapter_progress, state.chapterIndex + 1, state.chapters.size, percent)
                } else {
                    stringResource(R.string.progress_percent, percent)
                },
                style = MaterialTheme.typography.labelMedium,
                color = muted,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = stringResource(R.string.reader_minutes_left, state.minutesLeftInSection),
                style = MaterialTheme.typography.labelMedium,
                color = muted,
                modifier = Modifier.padding(start = 12.dp),
            )
        }
    }
}

@Composable
private fun ContextToggle(
    showContext: Boolean,
    onShowContextChange: (Boolean) -> Unit,
) {
    FilterChip(
        selected = showContext,
        onClick = { onShowContextChange(!showContext) },
        label = { Text(stringResource(R.string.reader_context)) },
    )
}

/** The current chapter's title; opens the chapter list when the book has chapters. */
@Composable
private fun ChapterTitle(
    state: ReaderUiState,
    onOpenChapters: () -> Unit,
) {
    val title = state.chapterIndex?.let { state.chapters[it].title } ?: state.bookTitle
    val hasChapters = state.chapters.isNotEmpty()

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier =
        Modifier
            .clip(MaterialTheme.shapes.small)
            .clickable(
                enabled = hasChapters,
                onClickLabel = stringResource(R.string.reader_chapters),
                onClick = onOpenChapters,
            ).padding(vertical = 8.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
        if (hasChapters) {
            Icon(
                painter = painterResource(R.drawable.ic_expand_more),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
