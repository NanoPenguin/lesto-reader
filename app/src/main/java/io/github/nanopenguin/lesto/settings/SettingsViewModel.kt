package io.github.nanopenguin.lesto.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SettingsUiState(
    /** False until the settings have been read, so defaults are not flashed on start. */
    val isLoaded: Boolean,
    val settings: Settings,
)

class SettingsViewModel(
    private val store: SettingsStore,
) : ViewModel() {
    val uiState: StateFlow<SettingsUiState> =
        store.settings
            .map { SettingsUiState(isLoaded = true, settings = it) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState(isLoaded = false, settings = Settings()))

    fun setTheme(theme: ThemeMode) = update { it.copy(theme = theme) }

    fun setTextSize(textSize: TextSize) = update { it.copy(textSize = textSize) }

    fun setShowContext(show: Boolean) = update { it.copy(showContext = show) }

    fun slower() = update { it.copy(wordsPerMinute = (it.wordsPerMinute - SPEED_STEP).coerceIn(SpeedRange)) }

    fun faster() = update { it.copy(wordsPerMinute = (it.wordsPerMinute + SPEED_STEP).coerceIn(SpeedRange)) }

    private fun update(change: (Settings) -> Settings) {
        viewModelScope.launch { store.update(change) }
    }
}
