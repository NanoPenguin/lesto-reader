package io.github.nanopenguin.pace

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.nanopenguin.pace.settings.ThemeMode
import io.github.nanopenguin.pace.ui.theme.PaceTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val theme by appContainer.settings.settings.collectAsStateWithLifecycle(initialValue = null)
            val darkTheme =
                when (theme?.theme) {
                    ThemeMode.Light -> false
                    ThemeMode.Dark -> true
                    ThemeMode.System, null -> isSystemInDarkTheme()
                }
            // The status and navigation bar icons follow the app's theme, not the system's.
            LaunchedEffect(darkTheme) {
                val style = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { darkTheme }
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
            }
            PaceTheme(darkTheme = darkTheme) {
                PaceApp()
            }
        }
    }
}
