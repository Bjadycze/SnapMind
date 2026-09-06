package com.app.snapmind.presentation.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.app.snapmind.data.prefs.SettingsDataStore
import com.app.snapmind.presentation.theme.PaletteChoice
import com.app.snapmind.presentation.theme.ThemeMode
import com.app.snapmind.service.worker.ReminderScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    val reminderHour: Int = 18,
    val quietStartHour: Int = 22,
    val quietEndHour: Int = 8,
    val palette: PaletteChoice = PaletteChoice.DEFAULT,
    val themeMode: ThemeMode = ThemeMode.DARK
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settings: SettingsDataStore,
    private val scheduler: ReminderScheduler
) : ViewModel() {

    val state: StateFlow<SettingsUiState> = combine(
        settings.reminderHour,
        settings.quietStartHour,
        settings.quietEndHour,
        settings.palette,
        settings.themeMode
    ) { reminder, quietStart, quietEnd, palette, themeMode ->
        SettingsUiState(reminder, quietStart, quietEnd, palette, themeMode)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState())

    fun setReminderHour(hour: Int) {
        viewModelScope.launch {
            settings.setReminderHour(hour)
            // REPLACE here: the user just changed the time and expects it to take effect.
            scheduler.schedule(hour, replaceExisting = true)
        }
    }

    fun setQuietHours(start: Int, end: Int) {
        viewModelScope.launch { settings.setQuietHours(start, end) }
    }

    fun setPalette(choice: PaletteChoice) {
        viewModelScope.launch { settings.setPalette(choice) }
    }

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { settings.setThemeMode(mode) }
    }

    /**
     * Called on app start so the daily job exists after an update or a reboot.
     *
     * Deliberately does NOT replace an existing schedule. v1 did, and because this runs on
     * every app start it silently moved the reminder to whenever the app was last opened --
     * an 18:00 reminder arrived at 15:00.
     */
    fun ensureScheduled() {
        viewModelScope.launch {
            scheduler.schedule(settings.reminderHour.first(), replaceExisting = false)
        }
    }
}
