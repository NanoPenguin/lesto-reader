package io.github.nanopenguin.lesto

import androidx.datastore.core.CorruptionException
import androidx.datastore.core.Serializer
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.io.InputStream
import java.io.OutputStream

private val StoreJson = Json { ignoreUnknownKeys = true }

/** Stores a value as JSON in DataStore. Unreadable files are reported as corrupt, so they can be replaced. */
class JsonSerializer<T>(
    private val serializer: KSerializer<T>,
    override val defaultValue: T,
) : Serializer<T> {
    override suspend fun readFrom(input: InputStream): T = try {
        StoreJson.decodeFromString(serializer, input.readBytes().decodeToString())
    } catch (exception: SerializationException) {
        throw CorruptionException("Unreadable data file", exception)
    } catch (exception: IllegalArgumentException) {
        // An unknown enum value, e.g. written by a newer version.
        throw CorruptionException("Unreadable data file", exception)
    }

    override suspend fun writeTo(
        t: T,
        output: OutputStream,
    ) = output.write(StoreJson.encodeToString(serializer, t).encodeToByteArray())
}
