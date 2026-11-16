package com.example.contextbubble.presentation

/** M0 dashboard state: consent only. Service status observation is added in M1. */
data class DashboardUiState(
    val consentAccepted: Boolean = false,
    val consentVersion: Int? = null,
) {
    /** Accessibility Settings are only offered after the disclosure was accepted. */
    val canOpenAccessibilitySettings: Boolean get() = consentAccepted
}
