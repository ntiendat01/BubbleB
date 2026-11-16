package com.example.bubbleb.domain

import com.example.bubbleb.domain.model.BubbleVisibility
import com.example.bubbleb.domain.model.HideReason
import com.example.bubbleb.domain.policy.BubbleConditions
import com.example.bubbleb.domain.policy.BubbleVisibilityPolicy
import org.junit.Assert.assertEquals
import org.junit.Test

class BubbleVisibilityPolicyTest {
    private val own = "com.example.bubbleb"
    private val ok = BubbleConditions(
        serviceConnected = true,
        consentAccepted = true,
        bubbleEnabled = true,
        screenLocked = false,
        foregroundPackage = "com.example.notes",
        ownPackage = own,
        userExcludedPackages = emptySet(),
    )

    private fun hidden(reason: HideReason) = BubbleVisibility.Hidden(reason)

    @Test
    fun allConditionsMet_visible() {
        assertEquals(BubbleVisibility.Visible, BubbleVisibilityPolicy.evaluate(ok))
    }

    @Test
    fun serviceDisconnected_hiddenFirst() {
        val c = ok.copy(serviceConnected = false, consentAccepted = false, screenLocked = true)
        assertEquals(hidden(HideReason.SERVICE_DISCONNECTED), BubbleVisibilityPolicy.evaluate(c))
    }

    @Test
    fun noConsent_hidden() {
        assertEquals(hidden(HideReason.CONSENT_REQUIRED), BubbleVisibilityPolicy.evaluate(ok.copy(consentAccepted = false)))
    }

    @Test
    fun disabledByUser_hidden() {
        assertEquals(hidden(HideReason.DISABLED_BY_USER), BubbleVisibilityPolicy.evaluate(ok.copy(bubbleEnabled = false)))
    }

    @Test
    fun locked_hidden() {
        assertEquals(hidden(HideReason.SCREEN_LOCKED), BubbleVisibilityPolicy.evaluate(ok.copy(screenLocked = true)))
    }

    @Test
    fun unknownOrBlankPackage_failsClosed() {
        assertEquals(hidden(HideReason.UNKNOWN_FOREGROUND), BubbleVisibilityPolicy.evaluate(ok.copy(foregroundPackage = null)))
        assertEquals(hidden(HideReason.UNKNOWN_FOREGROUND), BubbleVisibilityPolicy.evaluate(ok.copy(foregroundPackage = "  ")))
    }

    @Test
    fun ownApp_hidden() {
        assertEquals(hidden(HideReason.OWN_APP), BubbleVisibilityPolicy.evaluate(ok.copy(foregroundPackage = own)))
    }

    @Test
    fun systemUiAndPermissionDialogs_hidden() {
        listOf("com.android.systemui", "com.google.android.permissioncontroller").forEach { pkg ->
            assertEquals(hidden(HideReason.SYSTEM_SURFACE), BubbleVisibilityPolicy.evaluate(ok.copy(foregroundPackage = pkg)))
        }
    }

    @Test
    fun defaultSensitiveApp_hidden() {
        val c = ok.copy(foregroundPackage = "com.google.android.apps.authenticator2")
        assertEquals(hidden(HideReason.EXCLUDED_APP), BubbleVisibilityPolicy.evaluate(c))
    }

    @Test
    fun userExcludedApp_hidden() {
        val c = ok.copy(foregroundPackage = "com.example.notes", userExcludedPackages = setOf("com.example.notes"))
        assertEquals(hidden(HideReason.EXCLUDED_APP), BubbleVisibilityPolicy.evaluate(c))
    }
}
