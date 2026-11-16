package com.example.bubbleb.presentation

import com.example.bubbleb.di.OwnPackageName
import com.example.bubbleb.domain.model.BubblePosition
import com.example.bubbleb.domain.model.BubbleVisibility
import com.example.bubbleb.domain.policy.BubbleConditions
import com.example.bubbleb.domain.policy.BubbleVisibilityPolicy
import com.example.bubbleb.domain.repository.AppProfileRepository
import com.example.bubbleb.domain.repository.SettingsRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/**
 * Application-scoped controller between the accessibility service (Android adapter) and the rest
 * of the app. It holds only minimal window state (connection, foreground package, lock flag) in
 * memory — never screen text — and derives bubble visibility via [BubbleVisibilityPolicy].
 */
@Singleton
class BubbleCoordinator @Inject constructor(
    private val settingsRepository: SettingsRepository,
    appProfileRepository: AppProfileRepository,
    @param:OwnPackageName private val ownPackage: String,
) {
    private val serviceConnectedState = MutableStateFlow(false)
    private val foregroundPackage = MutableStateFlow<String?>(null)
    private val screenLocked = MutableStateFlow(false)

    /** Latest lock state as observed by the service (policy, not a guarantee). */
    val isScreenLocked: StateFlow<Boolean> = screenLocked.asStateFlow()

    /** True only between `onServiceConnected` and unbind/destroy of the real service. */
    val serviceConnected: StateFlow<Boolean> = serviceConnectedState.asStateFlow()

    val bubbleVisibility: Flow<BubbleVisibility> = combine(
        serviceConnectedState,
        foregroundPackage,
        screenLocked,
        settingsRepository.settings,
        appProfileRepository.excludedPackages,
    ) { connected, pkg, locked, settings, excluded ->
        BubbleVisibilityPolicy.evaluate(
            BubbleConditions(
                serviceConnected = connected,
                consentAccepted = settings.hasCurrentConsent,
                bubbleEnabled = settings.bubbleEnabled,
                screenLocked = locked,
                foregroundPackage = pkg,
                ownPackage = ownPackage,
                userExcludedPackages = excluded,
            ),
        )
    }.distinctUntilChanged()

    val bubblePosition: Flow<BubblePosition> =
        settingsRepository.settings.map { it.bubblePosition }.distinctUntilChanged()

    fun onServiceConnected() {
        serviceConnectedState.value = true
    }

    /** Service unbound/destroyed/revoked: drop all transient state. */
    fun onServiceDisconnected() {
        serviceConnectedState.value = false
        foregroundPackage.value = null
        screenLocked.value = false
    }

    fun onForegroundPackageChanged(packageName: String?) {
        foregroundPackage.value = packageName?.takeIf { it.isNotBlank() }
    }

    fun onScreenLockChanged(locked: Boolean) {
        screenLocked.value = locked
    }

    suspend fun saveBubblePosition(position: BubblePosition) {
        settingsRepository.setBubblePosition(position)
    }
}
