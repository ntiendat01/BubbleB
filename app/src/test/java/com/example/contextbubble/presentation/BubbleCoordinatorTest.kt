package com.example.contextbubble.presentation

import com.example.contextbubble.domain.ConsentPolicy
import com.example.contextbubble.domain.model.BubbleEdge
import com.example.contextbubble.domain.model.BubblePosition
import com.example.contextbubble.domain.model.BubbleVisibility
import com.example.contextbubble.domain.model.HideReason
import com.example.contextbubble.domain.model.UserSettings
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BubbleCoordinatorTest {
    private val own = "com.example.contextbubble"
    private val settings = FakeSettingsRepository(
        UserSettings(consentVersion = ConsentPolicy.CURRENT_VERSION, consentAcceptedAtMs = 1L),
    )
    private val profiles = FakeAppProfileRepository()
    private val coordinator = BubbleCoordinator(settings, profiles, own)

    private suspend fun visibility() = coordinator.bubbleVisibility.first()

    @Test
    fun initially_hiddenBecauseServiceNotConnected() = runTest {
        assertFalse(coordinator.serviceConnected.value)
        assertEquals(BubbleVisibility.Hidden(HideReason.SERVICE_DISCONNECTED), visibility())
    }

    @Test
    fun connectedWithForegroundApp_visible() = runTest {
        coordinator.onServiceConnected()
        coordinator.onForegroundPackageChanged("com.example.notes")
        assertTrue(coordinator.serviceConnected.value)
        assertEquals(BubbleVisibility.Visible, visibility())
    }

    @Test
    fun disconnect_clearsTransientState() = runTest {
        coordinator.onServiceConnected()
        coordinator.onForegroundPackageChanged("com.example.notes")
        coordinator.onScreenLockChanged(true)
        coordinator.onServiceDisconnected()
        coordinator.onServiceConnected()
        // Foreground was purged, so we fail closed until a new window event arrives.
        assertEquals(BubbleVisibility.Hidden(HideReason.UNKNOWN_FOREGROUND), visibility())
    }

    @Test
    fun twentyServiceToggles_endInConsistentState() = runTest {
        repeat(20) {
            coordinator.onServiceConnected()
            coordinator.onForegroundPackageChanged("com.example.notes")
            assertEquals(BubbleVisibility.Visible, visibility())
            coordinator.onServiceDisconnected()
            assertEquals(BubbleVisibility.Hidden(HideReason.SERVICE_DISCONNECTED), visibility())
        }
        assertFalse(coordinator.serviceConnected.value)
    }

    @Test
    fun lock_hidesAndUnlock_restores() = runTest {
        coordinator.onServiceConnected()
        coordinator.onForegroundPackageChanged("com.example.notes")
        coordinator.onScreenLockChanged(true)
        assertEquals(BubbleVisibility.Hidden(HideReason.SCREEN_LOCKED), visibility())
        coordinator.onScreenLockChanged(false)
        assertEquals(BubbleVisibility.Visible, visibility())
    }

    @Test
    fun userExclusionFromRoom_hides() = runTest {
        coordinator.onServiceConnected()
        coordinator.onForegroundPackageChanged("com.example.notes")
        profiles.excluded.value = setOf("com.example.notes")
        assertEquals(BubbleVisibility.Hidden(HideReason.EXCLUDED_APP), visibility())
    }

    @Test
    fun revokedConsent_hides() = runTest {
        coordinator.onServiceConnected()
        coordinator.onForegroundPackageChanged("com.example.notes")
        settings.revokeConsent()
        assertEquals(BubbleVisibility.Hidden(HideReason.CONSENT_REQUIRED), visibility())
    }

    @Test
    fun blankForeground_isUnknown() = runTest {
        coordinator.onServiceConnected()
        coordinator.onForegroundPackageChanged("")
        assertEquals(BubbleVisibility.Hidden(HideReason.UNKNOWN_FOREGROUND), visibility())
    }

    @Test
    fun savePosition_persistsAndEmits() = runTest {
        val pos = BubblePosition(BubbleEdge.LEFT, 0.8f)
        coordinator.saveBubblePosition(pos)
        assertEquals(pos, coordinator.bubblePosition.first())
    }
}
