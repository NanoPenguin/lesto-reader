package io.github.nanopenguin.lesto.settings

import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import io.github.nanopenguin.lesto.JsonSerializer
import kotlinx.serialization.Serializable

enum class ThemeMode { System, Light, Dark }

/** Size of the word being read. */
enum class TextSize(
    val wordSize: TextUnit,
) {
    Small(32.sp),
    Medium(40.sp),
    Large(48.sp),
}

@Serializable
data class Settings(
    val theme: ThemeMode = ThemeMode.System,
    val textSize: TextSize = TextSize.Medium,
    val showContext: Boolean = false,
    val wordsPerMinute: Int = DEFAULT_WORDS_PER_MINUTE,
)

const val DEFAULT_WORDS_PER_MINUTE = 300

/** Speed changes in steps of this many words per minute. */
const val SPEED_STEP = 25

val SpeedRange = 100..1000

val SettingsSerializer = JsonSerializer(Settings.serializer(), Settings())
