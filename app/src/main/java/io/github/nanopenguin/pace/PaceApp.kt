package io.github.nanopenguin.pace

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
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
    val goHome = { screen = Screen.Home }

    BackHandler(enabled = screen != Screen.Home, onBack = goHome)

    Surface(modifier = Modifier.fillMaxSize()) {
        when (screen) {
            Screen.Home -> {
                HomeScreen(
                    onOpenBook = { screen = Screen.Reader },
                    onOpenSettings = { screen = Screen.Settings },
                )
            }

            Screen.Reader -> {
                ReaderScreen(onClose = goHome)
            }

            Screen.Settings -> {
                SettingsScreen(onBack = goHome)
            }
        }
    }
}
