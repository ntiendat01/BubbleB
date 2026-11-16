package com.example.contextbubble.domain.repository

import com.example.contextbubble.domain.model.UserSettings
import kotlinx.coroutines.flow.Flow

/** Non-secret preferences (DataStore). */
interface SettingsRepository {
    val settings: Flow<UserSettings>
    suspend fun acceptConsent(version: Int, acceptedAtMs: Long)
    suspend fun revokeConsent()
    suspend fun setBubbleEnabled(enabled: Boolean)
}

/** Per-app profiles (Room). M0 skeleton only exposes user exclusions; personas arrive in M5. */
interface AppProfileRepository {
    val excludedPackages: Flow<Set<String>>
}
