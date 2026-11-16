package com.example.contextbubble.domain.model

import com.example.contextbubble.domain.ConsentPolicy

/** Non-secret preferences only. Never holds screen text or credentials. */
data class UserSettings(
    val consentVersion: Int? = null,
    val consentAcceptedAtMs: Long? = null,
    val bubbleEnabled: Boolean = true,
) {
    val hasCurrentConsent: Boolean
        get() = consentVersion == ConsentPolicy.CURRENT_VERSION && consentAcceptedAtMs != null
}
