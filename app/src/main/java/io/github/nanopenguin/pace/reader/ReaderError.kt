package io.github.nanopenguin.pace.reader

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.nanopenguin.pace.R
import io.github.nanopenguin.pace.book.BookError
import io.github.nanopenguin.pace.ui.BackButton

/** Says why a book could not be opened. */
@Composable
fun ReaderError(
    error: BookError,
    /** Removes the book from the library; null if it is not in it. */
    onRemove: (() -> Unit)?,
    onClose: () -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize().safeDrawingPadding()) {
        BackButton(onClick = onClose, modifier = Modifier.align(Alignment.TopStart))
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.align(Alignment.Center).padding(horizontal = 32.dp),
        ) {
            Text(
                text =
                stringResource(
                    when (error) {
                        BookError.Missing -> R.string.reader_error_missing
                        BookError.NoAccess -> R.string.reader_error_no_access
                        BookError.Unreadable -> R.string.reader_error_unreadable
                        BookError.NoText -> R.string.reader_error_no_text
                    },
                ),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            if (onRemove != null) {
                OutlinedButton(onClick = onRemove, modifier = Modifier.padding(top = 24.dp)) {
                    Text(stringResource(R.string.home_remove))
                }
            }
        }
    }
}
