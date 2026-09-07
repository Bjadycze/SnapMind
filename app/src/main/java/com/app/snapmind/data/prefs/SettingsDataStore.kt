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

/** Long enough for "Zahrada a balkon", short enough to stay readable on a card's edge strip. */
const val MaxCategoryNameLength = 20

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

        // Two lists, newline-separated. A Set would lose the order the user added them in,
        // and order is the only sensible way to show them (spec.md 11.16).
        // Last answer Play gave. Read while Play is unreachable, so a paid user is never
        // locked out by a bad connection (spec.md Task 8).
        val PRO_ENTITLED = booleanPreferencesKey("pro_entitled")

        val CUSTOM_CATEGORIES = stringPreferencesKey("custom_categories")
        val RETIRED_CATEGORIES = stringPreferencesKey("retired_categories")
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

    /** Custom categories currently offered when picking one. */
    val proEntitled: Flow<Boolean> = read(Keys.PRO_ENTITLED, false)

    val customCategories: Flow<List<String>> = readList(Keys.CUSTOM_CATEGORIES)

    /**
     * Names the user removed from the list. Nothing is thrown away (spec.md 7.2): they are
     * offered back the next time a category is added, so a name never has to be retyped
     * exactly.
     */
    val retiredCategories: Flow<List<String>> = readList(Keys.RETIRED_CATEGORIES)

    /**
     * Adds a category, or revives a retired one. Blank names and case-insensitive duplicates
     * are ignored rather than reported: there is nothing for the user to fix.
     *
     * Returns the canonical name to select -- the existing spelling wins over the typed one.
     */
    suspend fun addCustomCategory(rawName: String): String? {
        val name = rawName.trim().replace("\n", " ").take(MaxCategoryNameLength)
        if (name.isEmpty()) return null

        var canonical = name
        context.dataStore.edit { prefs ->
            val current = parseList(prefs[Keys.CUSTOM_CATEGORIES])
            val retired = parseList(prefs[Keys.RETIRED_CATEGORIES])

            canonical = current.firstOrNull { it.equals(name, ignoreCase = true) }
                ?: retired.firstOrNull { it.equals(name, ignoreCase = true) }
                ?: name

            if (current.none { it.equals(canonical, ignoreCase = true) }) {
                prefs[Keys.CUSTOM_CATEGORIES] = (current + canonical).joinToString("\n")
            }
            prefs[Keys.RETIRED_CATEGORIES] =
                retired.filterNot { it.equals(canonical, ignoreCase = true) }.joinToString("\n")
        }
        return canonical
    }

    /** Stops offering a category. The name moves to the retired list, it is not destroyed. */
    suspend fun retireCustomCategory(name: String) {
        context.dataStore.edit { prefs ->
            val current = parseList(prefs[Keys.CUSTOM_CATEGORIES])
            val retired = parseList(prefs[Keys.RETIRED_CATEGORIES])

            prefs[Keys.CUSTOM_CATEGORIES] =
                current.filterNot { it.equals(name, ignoreCase = true) }.joinToString("\n")
            if (retired.none { it.equals(name, ignoreCase = true) }) {
                prefs[Keys.RETIRED_CATEGORIES] = (retired + name).joinToString("\n")
            }
        }
    }

    suspend fun setReminderHour(hour: Int) = write(Keys.REMINDER_HOUR, hour.coerceIn(0, 23))
    suspend fun setQuietHours(start: Int, end: Int) {
        write(Keys.QUIET_START_HOUR, start.coerceIn(0, 23))
        write(Keys.QUIET_END_HOUR, end.coerceIn(0, 23))
    }
    suspend fun setOnboardingDone(done: Boolean) = write(Keys.ONBOARDING_DONE, done)
    suspend fun setProEntitled(entitled: Boolean) = write(Keys.PRO_ENTITLED, entitled)
    suspend fun setPalette(choice: PaletteChoice) = write(Keys.PALETTE, choice.name)
    suspend fun setThemeMode(mode: ThemeMode) = write(Keys.THEME_MODE, mode.name)

    private fun readList(key: Preferences.Key<String>): Flow<List<String>> =
        context.dataStore.data.map { parseList(it[key]) }

    private fun parseList(raw: String?): List<String> =
        raw?.split("\n")?.map { it.trim() }?.filter { it.isNotEmpty() } ?: emptyList()

    private fun <T> read(key: Preferences.Key<T>, default: T): Flow<T> =
        context.dataStore.data.map { it[key] ?: default }

    private suspend fun <T> write(key: Preferences.Key<T>, value: T) {
        context.dataStore.edit { it[key] = value }
    }
}
