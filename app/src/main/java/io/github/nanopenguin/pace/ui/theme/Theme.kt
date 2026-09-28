package io.github.nanopenguin.pace.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors =
    lightColorScheme(
        primary = AccentLight,
        onPrimary = Color.White,
        background = PaperLight,
        onBackground = InkLight,
        surface = PaperLight,
        onSurface = InkLight,
        onSurfaceVariant = InkMutedLight,
        // Neutral containers instead of Material's tinted defaults (used by sliders, chips, cards).
        secondary = InkMutedLight,
        secondaryContainer = LineLight,
        onSecondaryContainer = InkLight,
        surfaceVariant = LineLight,
        surfaceContainerLowest = CardLight,
        surfaceContainerLow = CardLight,
        surfaceContainer = CardLight,
        surfaceContainerHigh = CardLight,
        surfaceContainerHighest = CardLight,
        outline = LineLight,
        outlineVariant = LineLight,
    )

private val DarkColors =
    darkColorScheme(
        primary = AccentDark,
        onPrimary = Color.Black,
        background = PaperDark,
        onBackground = InkDark,
        surface = PaperDark,
        onSurface = InkDark,
        onSurfaceVariant = InkMutedDark,
        // Neutral containers instead of Material's tinted defaults (used by sliders, chips, cards).
        secondary = InkMutedDark,
        secondaryContainer = LineDark,
        onSecondaryContainer = InkDark,
        surfaceVariant = LineDark,
        surfaceContainerLowest = CardDark,
        surfaceContainerLow = CardDark,
        surfaceContainer = CardDark,
        surfaceContainerHigh = CardDark,
        surfaceContainerHighest = CardDark,
        outline = LineDark,
        outlineVariant = LineDark,
    )

@Composable
fun PaceTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = PaceTypography,
        content = content,
    )
}
