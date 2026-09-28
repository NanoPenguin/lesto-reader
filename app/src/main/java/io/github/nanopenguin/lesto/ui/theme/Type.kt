package io.github.nanopenguin.lesto.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import io.github.nanopenguin.lesto.R

/** Atkinson Hyperlegible Next, a variable font: each weight is an instance of the same file. */
val AtkinsonHyperlegibleNext =
    FontFamily(
        listOf(FontWeight.Normal, FontWeight.Medium, FontWeight.SemiBold, FontWeight.Bold).map { weight ->
            Font(
                resId = R.font.atkinson_hyperlegible_next,
                weight = weight,
                variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight)),
            )
        },
    )

private fun TextStyle.withAppFont() = copy(fontFamily = AtkinsonHyperlegibleNext)

internal val LestoTypography =
    Typography().run {
        copy(
            displayLarge = displayLarge.withAppFont(),
            displayMedium = displayMedium.withAppFont(),
            displaySmall = displaySmall.withAppFont(),
            headlineLarge = headlineLarge.withAppFont(),
            headlineMedium = headlineMedium.withAppFont(),
            headlineSmall = headlineSmall.withAppFont(),
            titleLarge = titleLarge.withAppFont(),
            titleMedium = titleMedium.withAppFont(),
            titleSmall = titleSmall.withAppFont(),
            bodyLarge = bodyLarge.withAppFont(),
            bodyMedium = bodyMedium.withAppFont(),
            bodySmall = bodySmall.withAppFont(),
            labelLarge = labelLarge.withAppFont(),
            labelMedium = labelMedium.withAppFont(),
            labelSmall = labelSmall.withAppFont(),
        )
    }
