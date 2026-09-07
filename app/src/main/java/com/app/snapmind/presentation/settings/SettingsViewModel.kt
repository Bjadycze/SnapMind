package com.app.snapmind.presentation.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import android.app.Activity
import com.app.snapmind.data.billing.BillingManager
import com.app.snapmind.data.prefs.SettingsDataStore
import com.app.snapmind.domain.billing.EntitlementProvider
import com.app.snapmind.domain.repository.CapturedItemRepository
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

/** One of the user's categories, plus whether anything unresolved still uses it. */
data class CustomCategory(val name: String, val inUse: Boolean)

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
    private val repository: CapturedItemRepository,
    private val scheduler: ReminderScheduler,
    private val billing: BillingManager,
    entitlement: EntitlementProvider
) : ViewModel() {

    /**
     * Whether SnapMind Pro is active. Nothing is gated by it yet (Task 8 ships the billing
     * path on its own, before anything depends on it) -- this is where it becomes visible.
     */
    val isPro: StateFlow<Boolean> = entitlement.isPro()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    /** Play draws its sheet on top of the calling Activity, so an app context will not do. */
    fun purchase(activity: Activity) = billing.launchPurchase(activity)

    /**
     * The user's own categories, each with whether an unresolved item still uses it
     * (spec.md 11.16). A category in use cannot be removed -- the row would keep showing a
     * name that is no longer offered, which reads as a bug rather than a choice.
     */
    val categories: StateFlow<List<CustomCategory>> = combine(
        settings.customCategories,
        repository.observeAll()
    ) { names, items ->
        val inUse = items
            .filter { it.resolvedAt == null }
            .mapNotNull { it.userCategory?.lowercase() }
            .toSet()
        names.map { CustomCategory(it, inUse.contains(it.lowercase())) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Stops offering a category. The name is kept and offered back when adding (spec.md 7.2). */
    fun retireCategory(name: String) {
        viewModelScope.launch { settings.retireCustomCategory(name) }
    }

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
