package io.github.nanopenguin.pace

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.net.toUri
import io.github.nanopenguin.pace.home.HomeScreen
import io.github.nanopenguin.pace.reader.ReaderScreen
import io.github.nanopenguin.pace.settings.SettingsScreen

private enum class Screen { Home, Reader, Settings }

/**
 * Root of the UI. With only three screens, navigation is a single piece of state;
 * every screen returns to Home when going back.
 */
@Composable
fun PaceApp() {
    var screen by rememberSaveable { mutableStateOf(Screen.Home) }
    // The book being read, as a content URI.
    var book by rememberSaveable { mutableStateOf<String?>(null) }
    val goHome = { screen = Screen.Home }

    BackHandler(enabled = screen != Screen.Home, onBack = goHome)

    Surface(modifier = Modifier.fillMaxSize()) {
        when (screen) {
            Screen.Home -> HomeScreen(
                onOpenBook = { uri ->
                    book = uri.toString()
                    screen = Screen.Reader
                },
                onOpenSettings = { screen = Screen.Settings },
            )

            Screen.Reader -> book?.let { key(it) { ReaderScreen(it.toUri(), onClose = goHome) } }

            Screen.Settings -> SettingsScreen(onBack = goHome)
        }
    }
}
