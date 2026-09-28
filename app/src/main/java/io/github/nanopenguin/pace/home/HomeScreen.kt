package io.github.nanopenguin.pace.home

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.nanopenguin.pace.R
import io.github.nanopenguin.pace.appContainer
import io.github.nanopenguin.pace.library.LibraryEntry
import io.github.nanopenguin.pace.ui.theme.PaceTheme
import kotlin.math.roundToInt

/** File types offered in the system file picker. */
private val BookTypes = arrayOf("application/epub+zip", "application/pdf")

@Composable
fun HomeScreen(
    onOpenBook: (Uri) -> Unit,
    onOpenSettings: () -> Unit,
) {
    val container = LocalContext.current.appContainer
    val viewModel = viewModel { HomeViewModel(container.library, container.books) }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val picker =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) {
                viewModel.keepAccess(uri)
                onOpenBook(uri)
            }
        }

    HomeScreen(
        state = state,
        onPickBook = { picker.launch(BookTypes) },
        onOpenBook = { onOpenBook(it.uri.toUri()) },
        onRemoveBook = viewModel::remove,
        onOpenSettings = onOpenSettings,
    )
}

@Composable
private fun HomeScreen(
    state: HomeUiState,
    onPickBook: () -> Unit,
    onOpenBook: (LibraryEntry) -> Unit,
    onRemoveBook: (LibraryEntry) -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
        modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .padding(horizontal = 24.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 24.dp)) {
            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onOpenSettings) {
                Icon(
                    painter = painterResource(R.drawable.ic_settings),
                    contentDescription = stringResource(R.string.home_settings),
                )
            }
        }

        when {
            !state.isLoaded -> Spacer(modifier = Modifier.weight(1f))

            state.books.isEmpty() -> Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                Text(
                    text = stringResource(R.string.home_empty),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.align(Alignment.Center),
                )
            }

            else -> LazyColumn(modifier = Modifier.weight(1f)) {
                item {
                    SectionLabel(stringResource(R.string.home_continue), Modifier.padding(top = 32.dp, bottom = 12.dp))
                    BookMenu(state.books.first(), onOpenBook, onRemoveBook) { ContinueCard(state.books.first(), it) }
                }
                if (state.books.size > 1) {
                    item { SectionLabel(stringResource(R.string.home_recent), Modifier.padding(top = 40.dp, bottom = 4.dp)) }
                    items(state.books.drop(1), key = { it.uri }) { entry ->
                        BookMenu(entry, onOpenBook, onRemoveBook) { RecentBook(entry, it) }
                    }
                }
            }
        }

        Button(onClick = onPickBook, modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp)) {
            Text(stringResource(R.string.home_open_book))
        }
    }
}

@Composable
private fun SectionLabel(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier,
    )
}

/** Opens [entry] on tap; a long press offers to remove it from the library. */
@Composable
private fun BookMenu(
    entry: LibraryEntry,
    onOpen: (LibraryEntry) -> Unit,
    onRemove: (LibraryEntry) -> Unit,
    content: @Composable (Modifier) -> Unit,
) {
    var showMenu by remember { mutableStateOf(false) }
    Box {
        content(Modifier.combinedClickable(onClick = { onOpen(entry) }, onLongClick = { showMenu = true }))
        DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.home_remove)) },
                onClick = {
                    showMenu = false
                    onRemove(entry)
                },
            )
        }
    }
}

@Composable
private fun ContinueCard(
    entry: LibraryEntry,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .then(modifier)
            .padding(20.dp),
    ) {
        Text(
            text = entry.title,
            style = MaterialTheme.typography.titleLarge,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        entry.author?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 20.dp)) {
            ProgressBar(entry.progress, Modifier.weight(1f))
            Text(
                text = percent(entry.progress),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 12.dp),
            )
        }
    }
}

@Composable
private fun RecentBook(
    entry: LibraryEntry,
    modifier: Modifier = Modifier,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.fillMaxWidth().padding(vertical = 12.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = entry.title,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            entry.author?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Text(
            text = percent(entry.progress),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 16.dp),
        )
    }
}

@Composable
private fun ProgressBar(
    progress: Float,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier =
        modifier
            .height(3.dp)
            .clip(RoundedCornerShape(2.dp))
            .background(MaterialTheme.colorScheme.outline),
    ) {
        Box(
            modifier =
            Modifier
                .fillMaxWidth(progress)
                .height(3.dp)
                .background(MaterialTheme.colorScheme.primary),
        )
    }
}

@Composable
private fun percent(progress: Float) = stringResource(R.string.progress_percent, (progress * 100).roundToInt())

@Preview(showBackground = true)
@Composable
private fun HomeScreenPreview() {
    PaceTheme {
        HomeScreen(
            state =
            HomeUiState(
                isLoaded = true,
                books =
                listOf(
                    LibraryEntry("a", "Alice’s Adventures in Wonderland", "Lewis Carroll", 380, 1000, lastOpened = 2),
                    LibraryEntry("b", "Moby Dick; Or, The Whale", "Herman Melville", 50, 1000, lastOpened = 1),
                ),
            ),
            onPickBook = {},
            onOpenBook = {},
            onRemoveBook = {},
            onOpenSettings = {},
        )
    }
}
