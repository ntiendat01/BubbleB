package com.example.bubbleb.presentation

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.bubbleb.ui.main.DashboardContent
import com.example.bubbleb.ui.main.TAG_BUBBLE_SWITCH
import com.example.bubbleb.ui.main.TAG_CONSENT
import com.example.bubbleb.ui.main.TAG_OPEN_SETTINGS
import com.example.bubbleb.ui.theme.BubbleBTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** Robolectric Compose test of the dashboard (JVM, not a device test). */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class DashboardContentTest {
    @get:Rule val rule = createComposeRule()

    private var consentClicks = 0
    private var providerClicks = 0

    private fun show(state: DashboardUiState) = rule.setContent {
        BubbleBTheme {
            DashboardContent(
                state = state,
                onAcceptConsent = { consentClicks++ },
                onRevokeConsent = {},
                onOpenAccessibilitySettings = {},
                onRecheck = {},
                onBubbleToggle = {},
                onOpenProviderSettings = { providerClicks++ },
            )
        }
    }

    @Test
    fun beforeConsent_settingsAndToggleDisabled() {
        show(DashboardStateMapper.map(false, false, false, null, true))
        rule.onNodeWithTag(TAG_OPEN_SETTINGS).performScrollTo().assertIsNotEnabled()
        rule.onNodeWithTag(TAG_BUBBLE_SWITCH).performScrollTo().assertIsNotEnabled()
        rule.onNodeWithTag(TAG_CONSENT).performScrollTo().performClick()
        assertEquals(1, consentClicks)
    }

    @Test
    fun consentedAndConnected_toggleEnabled() {
        show(DashboardStateMapper.map(true, true, true, 1, true))
        rule.onNodeWithText("Đã kết nối").assertExists()
        rule.onNodeWithTag(TAG_OPEN_SETTINGS).performScrollTo().assertIsEnabled()
        rule.onNodeWithTag(TAG_BUBBLE_SWITCH).performScrollTo().assertIsEnabled()
    }

    @Test
    fun providerSettingsRoute_reachable() {
        show(DashboardUiState())
        rule.onNodeWithText("Nhà cung cấp AI").performScrollTo().performClick()
        assertEquals(1, providerClicks)
    }
}
