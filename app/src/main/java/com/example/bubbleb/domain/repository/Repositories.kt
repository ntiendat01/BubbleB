package com.example.bubbleb.domain.repository

import com.example.bubbleb.domain.model.BubblePosition
import com.example.bubbleb.domain.model.UserSettings
import kotlinx.coroutines.flow.Flow

/** Non-secret preferences (DataStore). */
interface SettingsRepository {
    val settings: Flow<UserSettings>
    suspend fun acceptConsent(version: Int, acceptedAtMs: Long)
    suspend fun revokeConsent()
    suspend fun setBubbleEnabled(enabled: Boolean)
    suspend fun setBubblePosition(position: BubblePosition)
}

/** Per-app profiles (Room). M0/M1 only expose user exclusions; personas arrive in M5. */
interface AppProfileRepository {
    val excludedPackages: Flow<Set<String>>
}

/** Whether Android Settings lists our accessibility service as enabled. */
interface AccessibilityStatusSource {
    val isEnabledInSettings: Flow<Boolean>
}
