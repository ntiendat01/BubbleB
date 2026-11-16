package com.example.bubbleb.presentation

import com.example.bubbleb.capture.FakeScreenNode
import com.example.bubbleb.capture.ScreenFixtures
import com.example.bubbleb.capture.SteppingClock
import com.example.bubbleb.domain.capture.ScreenTreeReader
import com.example.bubbleb.domain.model.AiAction
import com.example.bubbleb.domain.model.CaptureFailure
import com.example.bubbleb.domain.model.CaptureResult
import com.example.bubbleb.domain.repository.ScreenCaptureSource
import com.example.bubbleb.data.reply.ReplyJsonParser
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Fake source: applies preflight to the fixture's package BEFORE "reading" any text. */
class FakeCaptureSource(
    private val root: FakeScreenNode?,
    private val pkg: String? = ScreenFixtures.PKG,
    private val gate: CompletableDeferred<Unit>? = null,
) : ScreenCaptureSource {
    var calls = 0
    var textReads = 0

    override suspend fun capture(preflight: (String?) -> CaptureFailure?): CaptureResult {
        calls++
        gate?.await()
        val r = root ?: return CaptureResult.Failure(CaptureFailure.NO_ROOT)
        preflight(pkg)?.let { return CaptureResult.Failure(it) }
        textReads++
        return ScreenTreeReader(SteppingClock(0)).read(r, pkg!!, "Chat", 1L)
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class PanelCoordinatorTest {
    private val own = "com.example.bubbleb"
    private val profiles = FakeAppProfileRepository()
    private val bubble = BubbleCoordinator(FakeSettingsRepository(), profiles, own).apply { onServiceConnected() }
    private val panel = PanelCoordinator(bubble, profiles, own, ReplyJsonParser())

    @Test fun startsCollapsed_noCaptureWithoutTap() = runTest {
        val source = FakeCaptureSource(ScreenFixtures.chat())
        assertEquals(PanelState.Collapsed, panel.state.value)
        assertEquals(0, source.calls)
    }

    @Test fun tap_chat_showsActionsWithReply() = runTest {
        panel.onBubbleTapped(FakeCaptureSource(ScreenFixtures.chat()))
        val s = panel.state.value as PanelState.Actions
        assertTrue(s.replyEligible)
        assertEquals("Chat", s.appLabel)
        assertTrue(s.visibleCodePoints > 0)
    }

    @Test fun actionLeadsToPreview_sendDisabledWithoutProvider() = runTest {
        panel.onBubbleTapped(FakeCaptureSource(ScreenFixtures.chat()))
        panel.onActionSelected(AiAction.SUMMARIZE)
        val s = panel.state.value as PanelState.PreviewConsent
        assertFalse(s.providerConfigured)
        assertFalse(s.canSend)
        assertFalse(s.preview.text.contains("0912"))
    }

    @Test fun replyIgnoredWhenNotEligible() = runTest {
        panel.onBubbleTapped(FakeCaptureSource(ScreenFixtures.noFocusedInput()))
        panel.onActionSelected(AiAction.REPLY)
        assertTrue(panel.state.value is PanelState.Actions)
    }

    @Test fun previewBackReturnsToActions_cancelPurges() = runTest {
        panel.onBubbleTapped(FakeCaptureSource(ScreenFixtures.chat()))
        panel.onActionSelected(AiAction.EXPLAIN)
        panel.onPreviewBack()
        assertTrue(panel.state.value is PanelState.Actions)
        panel.dismiss()
        assertEquals(PanelState.Collapsed, panel.state.value)
        assertNull(panel.capturedPackage)
    }

    @Test fun excludedApp_blockedBeforeAnyTextRead() = runTest {
        profiles.excluded.value = setOf(ScreenFixtures.PKG)
        val source = FakeCaptureSource(ScreenFixtures.chat())
        panel.onBubbleTapped(source)
        assertEquals(PanelState.Blocked(BlockedReason.EXCLUDED_APP, CaptureFailure.EXCLUDED_APP), panel.state.value)
        assertEquals(0, source.textReads)
    }

    @Test fun ownApp_blocked() = runTest {
        val source = FakeCaptureSource(ScreenFixtures.chat(), pkg = own)
        panel.onBubbleTapped(source)
        assertEquals(PanelState.Blocked(BlockedReason.OWN_APP, CaptureFailure.OWN_APP), panel.state.value)
        assertEquals(0, source.textReads)
    }

    @Test fun lockedScreen_blocked() = runTest {
        bubble.onScreenLockChanged(true)
        val source = FakeCaptureSource(ScreenFixtures.chat())
        panel.onBubbleTapped(source)
        assertEquals(PanelState.Blocked(BlockedReason.SCREEN_LOCKED, CaptureFailure.SCREEN_LOCKED), panel.state.value)
        assertEquals(0, source.textReads)
    }

    @Test fun passwordScreen_sensitiveBlocked() = runTest {
        panel.onBubbleTapped(FakeCaptureSource(ScreenFixtures.withPassword()))
        assertEquals(PanelState.Blocked(BlockedReason.SENSITIVE_SCREEN, CaptureFailure.SENSITIVE_SCREEN), panel.state.value)
        assertNull(panel.capturedPackage)
    }

    @Test fun emptyOrProtectedOrNullRoot_unavailable() = runTest {
        listOf(FakeCaptureSource(ScreenFixtures.emptyTree()), FakeCaptureSource(ScreenFixtures.protectedView()), FakeCaptureSource(null))
            .forEach { src ->
                panel.onBubbleTapped(src)
                assertEquals(UnavailableReason.NO_ACCESSIBLE_CONTENT, (panel.state.value as PanelState.Unavailable).reason)
                panel.dismiss()
            }
    }

    @Test fun serviceDisconnected_permissionRequired() = runTest {
        bubble.onServiceDisconnected()
        val source = FakeCaptureSource(ScreenFixtures.chat())
        panel.onBubbleTapped(source)
        assertEquals(PanelState.Unavailable(UnavailableReason.PERMISSION_REQUIRED), panel.state.value)
        assertEquals(0, source.calls)
    }

    @Test fun repeatedTapsWhileOpen_captureOnce() = runTest {
        val source = FakeCaptureSource(ScreenFixtures.chat())
        panel.onBubbleTapped(source)
        panel.onBubbleTapped(source)
        assertEquals(1, source.calls)
    }

    @Test fun rapidTapsDuringCapture_captureOnce() = runTest(StandardTestDispatcher()) {
        val gate = CompletableDeferred<Unit>()
        val source = FakeCaptureSource(ScreenFixtures.chat(), gate = gate)
        launch { panel.onBubbleTapped(source) }
        launch { panel.onBubbleTapped(source) }
        advanceUntilIdle()
        assertEquals(PanelState.Capturing, panel.state.value)
        gate.complete(Unit)
        advanceUntilIdle()
        assertEquals(1, source.calls)
        assertTrue(panel.state.value is PanelState.Actions)
    }

    @Test fun dismissDuringCapture_staleResultDropped() = runTest(StandardTestDispatcher()) {
        val gate = CompletableDeferred<Unit>()
        launch { panel.onBubbleTapped(FakeCaptureSource(ScreenFixtures.chat(), gate = gate)) }
        advanceUntilIdle()
        panel.dismiss()
        gate.complete(Unit)
        advanceUntilIdle()
        assertEquals(PanelState.Collapsed, panel.state.value)
        assertNull(panel.capturedPackage)
    }

    @Test fun appSwitchClosesAndPurges_sameAppKeepsOpen() = runTest {
        panel.onBubbleTapped(FakeCaptureSource(ScreenFixtures.chat()))
        panel.onForegroundChanged(ScreenFixtures.PKG)
        assertTrue(panel.state.value is PanelState.Actions)
        panel.onForegroundChanged("com.other.app")
        assertEquals(PanelState.Collapsed, panel.state.value)
        assertNull(panel.capturedPackage)
    }

    @Test fun captureThrows_showsErrorInsteadOfSilence() = runTest {
        val throwing = object : ScreenCaptureSource {
            override suspend fun capture(preflight: (String?) -> CaptureFailure?): CaptureResult =
                throw IllegalStateException("node api failed")
        }
        panel.onBubbleTapped(throwing)
        assertEquals(PanelState.Unavailable(UnavailableReason.CAPTURE_ERROR), panel.state.value)
    }

    @Test fun everyCaptureFailure_mapsToVisibleNonCollapsedState() = runTest {
        CaptureFailure.entries.forEach { failure ->
            val source = object : ScreenCaptureSource {
                override suspend fun capture(preflight: (String?) -> CaptureFailure?) = CaptureResult.Failure(failure)
            }
            panel.onBubbleTapped(source)
            val state = panel.state.value
            assertTrue("$failure -> $state", state is PanelState.Blocked || state is PanelState.Unavailable)
            assertTrue(panel.stageOf(state).contains(failure.name))
            panel.dismiss()
        }
    }

    @Test fun stageLogsContainNoScreenText() = runTest {
        panel.onBubbleTapped(FakeCaptureSource(ScreenFixtures.chat()))
        panel.onActionSelected(AiAction.SUMMARIZE)
        val stage = panel.stageOf(panel.state.value)
        listOf("Lan", "0912", "Tối nay", ScreenFixtures.PKG, "Chat").forEach { assertFalse(stage.contains(it)) }
    }
}
