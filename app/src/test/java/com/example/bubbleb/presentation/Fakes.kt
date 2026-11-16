package com.example.bubbleb.presentation

import com.example.bubbleb.domain.model.BubblePosition
import com.example.bubbleb.domain.model.UserSettings
import com.example.bubbleb.domain.repository.AccessibilityStatusSource
import com.example.bubbleb.domain.repository.AppProfileRepository
import com.example.bubbleb.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

class FakeSettingsRepository(initial: UserSettings = UserSettings()) : SettingsRepository {
    val state = MutableStateFlow(initial)
    override val settings: Flow<UserSettings> = state

    override suspend fun acceptConsent(version: Int, acceptedAtMs: Long) =
        state.update { it.copy(consentVersion = version, consentAcceptedAtMs = acceptedAtMs) }

    override suspend fun revokeConsent() =
        state.update { it.copy(consentVersion = null, consentAcceptedAtMs = null) }

    override suspend fun setBubbleEnabled(enabled: Boolean) = state.update { it.copy(bubbleEnabled = enabled) }

    override suspend fun setBubblePosition(position: BubblePosition) =
        state.update { it.copy(bubblePosition = position) }
}

class FakeAppProfileRepository : AppProfileRepository {
    val excluded = MutableStateFlow<Set<String>>(emptySet())
    override val excludedPackages: Flow<Set<String>> = excluded
}

class FakeAccessibilityStatus(initial: Boolean = false) : AccessibilityStatusSource {
    val enabled = MutableStateFlow(initial)
    override val isEnabledInSettings: Flow<Boolean> = enabled
}
