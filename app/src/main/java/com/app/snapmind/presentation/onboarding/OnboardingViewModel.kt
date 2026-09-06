package com.app.snapmind.presentation.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.app.snapmind.data.prefs.SettingsDataStore
import com.app.snapmind.domain.model.PermissionState
import com.app.snapmind.domain.usecase.ResolvePermissionStateUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val resolvePermissions: ResolvePermissionStateUseCase,
    private val settings: SettingsDataStore
) : ViewModel() {

    private val _state = MutableStateFlow(resolvePermissions())
    val state: StateFlow<PermissionState> = _state.asStateFlow()

    /** Called on every onResume: the user may have changed something in Settings. */
    fun refresh() {
        _state.value = resolvePermissions()
    }

    fun finish() {
        viewModelScope.launch { settings.setOnboardingDone(true) }
    }
}
