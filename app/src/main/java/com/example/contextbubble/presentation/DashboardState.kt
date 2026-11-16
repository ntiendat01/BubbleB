package com.example.contextbubble.presentation

/** Service status as shown on the dashboard (UI_UX.md Screen A). */
enum class ServiceStatus {
    /** Android Settings does not list the service as enabled. */
    NO_PERMISSION,
    /** Enabled in Settings, but the OS has not (yet) connected the service. */
    ENABLED_NOT_CONNECTED,
    CONNECTED,
    /** Connected, but the user switched the bubble off. */
    BUBBLE_OFF,
}

data class DashboardUiState(
    val status: ServiceStatus = ServiceStatus.NO_PERMISSION,
    val consentAccepted: Boolean = false,
    val consentVersion: Int? = null,
    val bubbleEnabled: Boolean = false,
    val canToggleBubble: Boolean = false,
    val canOpenAccessibilitySettings: Boolean = false,
)

/** Pure mapping so the "no fake success" rule is unit-testable. */
object DashboardStateMapper {
    fun map(
        enabledInSettings: Boolean,
        serviceConnected: Boolean,
        consentAccepted: Boolean,
        consentVersion: Int?,
        bubbleEnabled: Boolean,
    ): DashboardUiState {
        val connected = enabledInSettings && serviceConnected
        val status = when {
            !enabledInSettings -> ServiceStatus.NO_PERMISSION
            !serviceConnected -> ServiceStatus.ENABLED_NOT_CONNECTED
            !bubbleEnabled -> ServiceStatus.BUBBLE_OFF
            else -> ServiceStatus.CONNECTED
        }
        return DashboardUiState(
            status = status,
            consentAccepted = consentAccepted,
            consentVersion = consentVersion.takeIf { consentAccepted },
            bubbleEnabled = bubbleEnabled,
            canToggleBubble = connected && consentAccepted,
            canOpenAccessibilitySettings = consentAccepted,
        )
    }
}
