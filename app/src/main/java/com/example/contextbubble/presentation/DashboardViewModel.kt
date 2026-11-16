package com.example.contextbubble.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.contextbubble.domain.Clock
import com.example.contextbubble.domain.ConsentPolicy
import com.example.contextbubble.domain.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val clock: Clock,
) : ViewModel() {

    val uiState: StateFlow<DashboardUiState> = settingsRepository.settings
        .map { DashboardUiState(it.hasCurrentConsent, it.consentVersion.takeIf { _ -> it.hasCurrentConsent }) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), DashboardUiState())

    fun acceptConsent() {
        viewModelScope.launch { settingsRepository.acceptConsent(ConsentPolicy.CURRENT_VERSION, clock.nowMs()) }
    }

    fun revokeConsent() {
        viewModelScope.launch { settingsRepository.revokeConsent() }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
