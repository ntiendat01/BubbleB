package com.example.contextbubble.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.contextbubble.domain.Clock
import com.example.contextbubble.domain.ConsentPolicy
import com.example.contextbubble.domain.repository.AccessibilityStatusSource
import com.example.contextbubble.domain.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.ExperimentalCoroutinesApi

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    accessibilityStatus: AccessibilityStatusSource,
    coordinator: BubbleCoordinator,
    private val clock: Clock,
) : ViewModel() {

    /** Bumped by "Kiểm tra lại" to re-read the OS state. */
    private val recheck = MutableStateFlow(0)

    val uiState: StateFlow<DashboardUiState> = combine(
        recheck.flatMapLatest { accessibilityStatus.isEnabledInSettings },
        coordinator.serviceConnected,
        settingsRepository.settings,
    ) { enabled, connected, settings ->
        DashboardStateMapper.map(
            enabledInSettings = enabled,
            serviceConnected = connected,
            consentAccepted = settings.hasCurrentConsent,
            consentVersion = settings.consentVersion,
            bubbleEnabled = settings.bubbleEnabled,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), DashboardUiState())

    fun acceptConsent() {
        viewModelScope.launch { settingsRepository.acceptConsent(ConsentPolicy.CURRENT_VERSION, clock.nowMs()) }
    }

    fun revokeConsent() {
        viewModelScope.launch { settingsRepository.revokeConsent() }
    }

    fun setBubbleEnabled(enabled: Boolean) {
        if (enabled && !uiState.value.canToggleBubble) return
        viewModelScope.launch { settingsRepository.setBubbleEnabled(enabled) }
    }

    fun recheckStatus() {
        recheck.value += 1
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
