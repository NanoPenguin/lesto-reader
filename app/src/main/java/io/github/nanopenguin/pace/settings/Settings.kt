package io.github.nanopenguin.pace.settings

import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import androidx.datastore.core.CorruptionException
import androidx.datastore.core.Serializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.io.InputStream
import java.io.OutputStream

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

private val SettingsJson = Json { ignoreUnknownKeys = true }

object SettingsSerializer : Serializer<Settings> {
    override val defaultValue = Settings()

    override suspend fun readFrom(input: InputStream): Settings = try {
        SettingsJson.decodeFromString(input.readBytes().decodeToString())
    } catch (exception: SerializationException) {
        throw CorruptionException("Settings file is unreadable", exception)
    } catch (exception: IllegalArgumentException) {
        // An unknown enum value, e.g. written by a newer version.
        throw CorruptionException("Settings file is unreadable", exception)
    }

    override suspend fun writeTo(
        t: Settings,
        output: OutputStream,
    ) = output.write(SettingsJson.encodeToString(t).encodeToByteArray())
}
