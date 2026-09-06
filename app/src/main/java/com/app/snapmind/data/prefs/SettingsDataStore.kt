package com.app.snapmind.data.prefs

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.app.snapmind.domain.model.ReminderPolicy
import com.app.snapmind.presentation.theme.PaletteChoice
import com.app.snapmind.presentation.theme.ThemeMode
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore by preferencesDataStore(name = "snapmind_settings")

@Singleton
class SettingsDataStore @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private object Keys {
        val REMINDER_HOUR = intPreferencesKey("reminder_hour")
        val QUIET_START_HOUR = intPreferencesKey("quiet_start_hour")
        val QUIET_END_HOUR = intPreferencesKey("quiet_end_hour")
        val ONBOARDING_DONE = booleanPreferencesKey("onboarding_done")
        val PALETTE = stringPreferencesKey("palette")
        val THEME_MODE = stringPreferencesKey("theme_mode")
    }

    val reminderHour: Flow<Int> = read(Keys.REMINDER_HOUR, ReminderPolicy.DEFAULT_REMINDER_HOUR)
    val quietStartHour: Flow<Int> = read(Keys.QUIET_START_HOUR, 22)
    val quietEndHour: Flow<Int> = read(Keys.QUIET_END_HOUR, 8)
    val onboardingDone: Flow<Boolean> = read(Keys.ONBOARDING_DONE, false)

    // A value from a future app version (or a raw string edited by hand) must never crash a
    // returning user -- fall back to the documented default instead.
    val palette: Flow<PaletteChoice> = context.dataStore.data.map { prefs ->
        runCatching { enumValueOf<PaletteChoice>(prefs[Keys.PALETTE] ?: "") }
            .getOrDefault(PaletteChoice.DEFAULT)
    }
    val themeMode: Flow<ThemeMode> = context.dataStore.data.map { prefs ->
        runCatching { enumValueOf<ThemeMode>(prefs[Keys.THEME_MODE] ?: "") }
            .getOrDefault(ThemeMode.DARK)
    }

    suspend fun setReminderHour(hour: Int) = write(Keys.REMINDER_HOUR, hour.coerceIn(0, 23))
    suspend fun setQuietHours(start: Int, end: Int) {
        write(Keys.QUIET_START_HOUR, start.coerceIn(0, 23))
        write(Keys.QUIET_END_HOUR, end.coerceIn(0, 23))
    }
    suspend fun setOnboardingDone(done: Boolean) = write(Keys.ONBOARDING_DONE, done)
    suspend fun setPalette(choice: PaletteChoice) = write(Keys.PALETTE, choice.name)
    suspend fun setThemeMode(mode: ThemeMode) = write(Keys.THEME_MODE, mode.name)

    private fun <T> read(key: Preferences.Key<T>, default: T): Flow<T> =
        context.dataStore.data.map { it[key] ?: default }

    private suspend fun <T> write(key: Preferences.Key<T>, value: T) {
        context.dataStore.edit { it[key] = value }
    }
}
