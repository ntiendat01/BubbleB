package com.example.bubbleb.domain.repository

import com.example.bubbleb.domain.model.BubblePosition
import com.example.bubbleb.domain.model.CaptureFailure
import com.example.bubbleb.domain.model.CaptureResult
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

/**
 * User-initiated, one-shot snapshot of the active window. [preflight] receives the root package
 * name and is evaluated BEFORE any node text is read; a non-null result aborts the capture.
 */
interface ScreenCaptureSource {
    suspend fun capture(
        preflight: (packageName: String?) -> CaptureFailure?,
    ): CaptureResult
}
