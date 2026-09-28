package io.github.nanopenguin.pace.reader

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import io.github.nanopenguin.pace.R
import io.github.nanopenguin.pace.ui.BackButton
import io.github.nanopenguin.pace.ui.theme.PaceTheme

@Composable
fun ReaderScreen(
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxSize().safeDrawingPadding()) {
        BackButton(onClick = onClose, modifier = Modifier.align(Alignment.TopStart))
        Text(
            text = stringResource(R.string.reader_placeholder),
            style = MaterialTheme.typography.displaySmall,
            modifier = Modifier.align(Alignment.Center),
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun ReaderScreenPreview() {
    PaceTheme {
        ReaderScreen(onClose = {})
    }
}
