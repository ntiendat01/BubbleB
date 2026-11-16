package com.example.contextbubble.presentation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DashboardStateMapperTest {
    private fun map(enabled: Boolean, connected: Boolean, consent: Boolean = true, bubble: Boolean = true) =
        DashboardStateMapper.map(enabled, connected, consent, if (consent) 1 else null, bubble)

    @Test
    fun notEnabled_noPermission_evenIfStaleConnectedFlag() {
        val s = map(enabled = false, connected = true)
        assertEquals(ServiceStatus.NO_PERMISSION, s.status)
        assertFalse(s.canToggleBubble)
    }

    @Test
    fun enabledButNotConnected_noFakeSuccess() {
        val s = map(enabled = true, connected = false)
        assertEquals(ServiceStatus.ENABLED_NOT_CONNECTED, s.status)
        assertFalse(s.canToggleBubble)
    }

    @Test
    fun connected_canToggle() {
        val s = map(enabled = true, connected = true)
        assertEquals(ServiceStatus.CONNECTED, s.status)
        assertTrue(s.canToggleBubble)
    }

    @Test
    fun connectedBubbleOff_status() {
        assertEquals(ServiceStatus.BUBBLE_OFF, map(enabled = true, connected = true, bubble = false).status)
    }

    @Test
    fun noConsent_blocksSettingsAndToggle() {
        val s = map(enabled = true, connected = true, consent = false)
        assertFalse(s.canOpenAccessibilitySettings)
        assertFalse(s.canToggleBubble)
        assertNull(s.consentVersion)
    }
}
