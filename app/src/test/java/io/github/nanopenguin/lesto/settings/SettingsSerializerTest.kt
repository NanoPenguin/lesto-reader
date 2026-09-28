package io.github.nanopenguin.lesto.settings

import androidx.datastore.core.CorruptionException
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.ByteArrayOutputStream

class SettingsSerializerTest {
    @Test
    fun `settings survive a round trip`() = runTest {
        val settings = Settings(ThemeMode.Dark, TextSize.Large, showContext = true, wordsPerMinute = 450)
        val output = ByteArrayOutputStream()
        SettingsSerializer.writeTo(settings, output)

        assertEquals(settings, SettingsSerializer.readFrom(output.toByteArray().inputStream()))
    }

    @Test
    fun `missing and unknown fields fall back to defaults`() = runTest {
        val read = SettingsSerializer.readFrom("""{"wordsPerMinute":500,"fontFamily":"serif"}""".byteInputStream())

        assertEquals(Settings(wordsPerMinute = 500), read)
    }

    @Test
    fun `damaged files are reported as corrupt`() {
        for (damaged in listOf("""{"theme":"Sepia"}""", """{"theme":""", "")) {
            assertThrows(CorruptionException::class.java) {
                runBlocking { SettingsSerializer.readFrom(damaged.byteInputStream()) }
            }
        }
    }
}
