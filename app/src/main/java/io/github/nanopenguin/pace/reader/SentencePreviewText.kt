package io.github.nanopenguin.pace.reader

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle

/** The current sentence in small text, with the current word in the accent colour. */
@Composable
fun SentencePreviewText(
    preview: SentencePreview,
    modifier: Modifier = Modifier,
) {
    val highlight = SpanStyle(color = MaterialTheme.colorScheme.primary)
    Text(
        text =
        buildAnnotatedString {
            preview.words.forEachIndexed { index, word ->
                if (index > 0) append(' ')
                if (index == preview.currentWord) withStyle(highlight) { append(word) } else append(word)
            }
        },
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = modifier,
    )
}
