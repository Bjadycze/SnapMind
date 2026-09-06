package com.app.snapmind.presentation.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.app.snapmind.data.prefs.SettingsDataStore
import com.app.snapmind.domain.model.CapturedItem
import com.app.snapmind.domain.model.CaptureSource
import com.app.snapmind.domain.model.PermissionState
import com.app.snapmind.domain.repository.CapturedItemRepository
import com.app.snapmind.domain.usecase.ProcessCapturedImageUseCase
import com.app.snapmind.domain.usecase.ResolvePermissionStateUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject
import com.app.snapmind.domain.usecase.EnrichSharedLinkUseCase

data class MainUiState(
    val active: List<CapturedItem> = emptyList(),
    val archived: List<CapturedItem> = emptyList()
)

@HiltViewModel
class MainViewModel @Inject constructor(
    private val repository: CapturedItemRepository,
    private val processCapture: ProcessCapturedImageUseCase,
    private val enrichSharedLink: EnrichSharedLinkUseCase,
    private val resolvePermissions: ResolvePermissionStateUseCase,
    private val settings: SettingsDataStore
) : ViewModel() {

    val items: StateFlow<MainUiState> = repository.observeAll()
        .map { all ->
            MainUiState(
                active = all.filterNot { it.isArchived },
                archived = all.filter { it.isArchived }
            )
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MainUiState())

    /** The user's own categories and the ones they removed (spec.md 11.16). */
    val customCategories: StateFlow<List<String>> = settings.customCategories
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val retiredCategories: StateFlow<List<String>> = settings.retiredCategories
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val onboardingDone: StateFlow<Boolean?> = settings.onboardingDone
        .map { it }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _permissions = MutableStateFlow(resolvePermissions())

    /**
     * Re-resolved on every onResume. Task 3 measured that a reinstall silently revokes
     * everything, so a stale snapshot would show a working app that captures nothing.
     */
    val permissions: StateFlow<PermissionState> = _permissions.asStateFlow()

    fun refreshPermissions() {
        _permissions.value = resolvePermissions()
    }

    /** Share Sheet entry point: an image or a link arrived from another app. */
    fun onShared(imageUri: String?, text: String?) {
        viewModelScope.launch {
            val id = processCapture(
                imageUri = imageUri,
                mediaStoreId = null,
                source = CaptureSource.SHARE_SHEET
            )
            if (id != null && !text.isNullOrBlank()) {
                repository.updateNote(id, text.trim())
                // Fire-and-forget: the item is already saved and visible.
                enrichSharedLink(id, text)
            }
        }
    }

    fun updateNote(itemId: Long, note: String) {
        // Only the fajfka and the koš settle an item (spec.md 7.2, corrected in v1.1). Marking
        // it done here made every noted item vanish from Aktivní the moment it was written --
        // the same failure QuickCaptureReplyReceiver had.
        viewModelScope.launch { repository.updateNote(itemId, note.trim()) }
    }

    /** Manual category from the detail dialog; null clears it back to the classifier's guess. */
    fun setCategory(itemId: Long, value: String?) {
        viewModelScope.launch { repository.updateUserCategory(itemId, value) }
    }

    /**
     * Adds a category (or revives a retired one) and puts it on the item in one step -- you
     * typed the name because you wanted it here, not to manage a list.
     */
    fun createCategory(itemId: Long, name: String) {
        viewModelScope.launch {
            settings.addCustomCategory(name)?.let { repository.updateUserCategory(itemId, it) }
        }
    }

    /** Fajfka: the user handled it. The only resolution path that earns a reward (spec.md 11.8). */
    fun markDone(itemId: Long) {
        viewModelScope.launch { repository.markDone(itemId) }
    }

    /** Koš: keep the row so the screenshot stays findable, just stop reminding. No reward. */
    fun discard(itemId: Long) {
        viewModelScope.launch { repository.markDiscarded(itemId) }
    }

    /** Undo a mistaken discard. Discarding is one tap, so undoing it has to be too. */
    fun restore(itemId: Long) {
        viewModelScope.launch { repository.markUnresolved(itemId) }
    }
}
