package com.example.bubbleb.domain.policy

import com.example.bubbleb.domain.ExclusionDefaults
import com.example.bubbleb.domain.model.BubbleVisibility
import com.example.bubbleb.domain.model.HideReason

/** Inputs for deciding whether the collapsed bubble may be shown. Contains no screen text. */
data class BubbleConditions(
    val serviceConnected: Boolean,
    val consentAccepted: Boolean,
    val bubbleEnabled: Boolean,
    val screenLocked: Boolean,
    val foregroundPackage: String?,
    val ownPackage: String,
    val userExcludedPackages: Set<String>,
)

/**
 * Pure fail-closed policy: the bubble is visible only when every condition is positively known
 * to be safe. Order matters: the first failing check is reported as the reason.
 */
object BubbleVisibilityPolicy {
    fun evaluate(c: BubbleConditions): BubbleVisibility {
        val pkg = c.foregroundPackage?.trim().orEmpty()
        val reason = when {
            !c.serviceConnected -> HideReason.SERVICE_DISCONNECTED
            !c.consentAccepted -> HideReason.CONSENT_REQUIRED
            !c.bubbleEnabled -> HideReason.DISABLED_BY_USER
            c.screenLocked -> HideReason.SCREEN_LOCKED
            pkg.isEmpty() -> HideReason.UNKNOWN_FOREGROUND
            pkg == c.ownPackage -> HideReason.OWN_APP
            pkg in ExclusionDefaults.SYSTEM_HIDDEN_PACKAGES -> HideReason.SYSTEM_SURFACE
            pkg in ExclusionDefaults.DEFAULT_SENSITIVE_PACKAGES -> HideReason.EXCLUDED_APP
            pkg in c.userExcludedPackages -> HideReason.EXCLUDED_APP
            else -> null
        }
        return if (reason == null) BubbleVisibility.Visible else BubbleVisibility.Hidden(reason)
    }
}
