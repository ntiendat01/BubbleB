package com.example.contextbubble.presentation

import com.example.contextbubble.domain.model.UserSettings
import com.example.contextbubble.domain.repository.SettingsRepository
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
}
