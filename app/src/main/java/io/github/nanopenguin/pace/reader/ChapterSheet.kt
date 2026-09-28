package io.github.nanopenguin.pace.reader

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/** Sub-headings are indented by this much per level, up to [MAX_INDENT_LEVELS]. */
private val LevelIndent = 16.dp
private const val MAX_INDENT_LEVELS = 3

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChapterSheet(
    chapters: List<Chapter>,
    currentChapter: Int?,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val topLevel = chapters.minOfOrNull { it.level } ?: 1

    ModalBottomSheet(onDismissRequest = onDismiss) {
        LazyColumn(state = rememberLazyListState(initialFirstVisibleItemIndex = currentChapter ?: 0)) {
            itemsIndexed(chapters) { index, chapter ->
                val isCurrent = index == currentChapter
                val indent = LevelIndent * (chapter.level - topLevel).coerceIn(0, MAX_INDENT_LEVELS)
                Text(
                    text = chapter.title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = if (isCurrent) FontWeight.SemiBold else null,
                    color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    modifier =
                    Modifier
                        .fillMaxWidth()
                        .clickable { onSelect(index) }
                        .padding(start = 24.dp + indent, end = 24.dp, top = 14.dp, bottom = 14.dp),
                )
            }
        }
    }
}
