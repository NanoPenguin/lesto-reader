package io.github.nanopenguin.pace.reader

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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.nanopenguin.pace.R
import kotlin.math.roundToInt

/** Controls shown while paused: where you are, seeking, speed and context words. */
@Composable
fun ReaderControls(
    state: ReaderUiState,
    onSeek: (Int) -> Unit,
    onPreviousSentence: () -> Unit,
    onSlower: () -> Unit,
    onFaster: () -> Unit,
    onShowContextChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant

    Column(modifier = modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp)) {
        Row {
            Text(
                text = state.chapterTitle ?: state.bookTitle,
                style = MaterialTheme.typography.labelLarge,
                color = muted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = stringResource(R.string.reader_minutes_left, state.minutesLeft),
                style = MaterialTheme.typography.labelLarge,
                color = muted,
            )
        }

        Slider(
            value = state.position.toFloat(),
            onValueChange = { onSeek(it.roundToInt()) },
            valueRange = 0f..state.lastPosition.coerceAtLeast(1).toFloat(),
        )

        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onPreviousSentence) {
                Icon(
                    painter = painterResource(R.drawable.ic_replay),
                    contentDescription = stringResource(R.string.reader_previous_sentence),
                )
            }
            Spacer(modifier = Modifier.weight(1f))
            IconButton(onClick = onSlower) {
                Icon(
                    painter = painterResource(R.drawable.ic_remove),
                    contentDescription = stringResource(R.string.reader_slower),
                )
            }
            Text(
                text = stringResource(R.string.reader_words_per_minute, state.wordsPerMinute),
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.widthIn(min = 88.dp),
            )
            IconButton(onClick = onFaster) {
                Icon(
                    painter = painterResource(R.drawable.ic_add),
                    contentDescription = stringResource(R.string.reader_faster),
                )
            }
            Spacer(modifier = Modifier.weight(1f))
            FilterChip(
                selected = state.showContext,
                onClick = { onShowContextChange(!state.showContext) },
                label = { Text(stringResource(R.string.reader_context)) },
            )
        }
    }
}
