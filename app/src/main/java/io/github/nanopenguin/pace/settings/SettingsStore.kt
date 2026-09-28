package io.github.nanopenguin.pace.settings

import androidx.datastore.core.DataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

/** The user's preferences, shared by the settings screen and the reader. */
class SettingsStore(
    private val dataStore: DataStore<Settings>,
) {
    val settings: Flow<Settings> = dataStore.data

    suspend fun current(): Settings = dataStore.data.first()

    suspend fun update(change: (Settings) -> Settings) {
        dataStore.updateData(change)
    }
}
