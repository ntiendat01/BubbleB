package com.example.bubbleb.presentation

import com.example.bubbleb.capture.ScreenFixtures
import com.example.bubbleb.data.reply.ReplyJsonParser
import com.example.bubbleb.domain.ConsentPolicy
import com.example.bubbleb.domain.model.AiAction
import com.example.bubbleb.domain.model.CaptureFailure
import com.example.bubbleb.domain.model.CaptureResult
import com.example.bubbleb.domain.model.CaptureStats
import com.example.bubbleb.domain.model.ElementRole
import com.example.bubbleb.domain.model.IntRect
import com.example.bubbleb.domain.model.ScreenContext
import com.example.bubbleb.domain.model.ScreenElement
import com.example.bubbleb.domain.model.UserSettings
import com.example.bubbleb.domain.reply.InsertionResult
import com.example.bubbleb.domain.reply.ReplyClipboard
import com.example.bubbleb.domain.reply.TextInserter
import com.example.bubbleb.domain.repository.ScreenCaptureSource
import com.example.bubbleb.domain.repository.AppProfileRepository
import com.example.bubbleb.reply.emptySnapshot
import com.example.bubbleb.reply.replyJson
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.advanceUntilIdle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ReplyFlowTest {
    private val own = "com.example.bubbleb"
    private val pkg = ScreenFixtures.PKG
    private val profiles = FakeAppProfileRepository()
    private val settings = FakeSettingsRepository(UserSettings(consentVersion = ConsentPolicy.CURRENT_VERSION, consentAcceptedAtMs = 1))
    private val bubble = BubbleCoordinator(settings, profiles, own).apply {
        onServiceConnected()
        onForegroundPackageChanged(pkg)
    }
    private val panel = PanelCoordinator(bubble, profiles, own, ReplyJsonParser())
    private val source = object : ScreenCaptureSource {
        override suspend fun capture(preflight: (String?) -> CaptureFailure?): CaptureResult {
            preflight(pkg)?.let { return CaptureResult.Failure(it) }
            val input = emptySnapshot().let { it.copy(target = it.target!!.copy(packageName = pkg)) }
            return CaptureResult.Success(
                ScreenContext(pkg, "Fixture", listOf(
                    ScreenElement("Tối nay bạn có muốn cùng đi ăn lúc 19 giờ không?", ElementRole.TEXT, IntRect(0, 0, 100, 10), 0, false),
                ), input, 1_000),
                CaptureStats(2, 1, false, false, false, false),
            )
        }
    }

    private suspend fun preview() {
        panel.onBubbleTapped(source)
        panel.onActionSelected(AiAction.REPLY)
    }

    @Test fun providerAndExplicitSendAreRequiredBeforeAcceptingResponse() = runTest {
        preview()
        panel.onReplyResponse(1, replyJson(), 1_100)
        assertTrue(panel.state.value is PanelState.PreviewConsent)
        assertNull(panel.onReplySendConfirmed(providerReady = false))
        val request = panel.onReplySendConfirmed(providerReady = true)
        assertNotNull(request)
        assertTrue(request!!.instruction.contains("exactly three"))
        assertTrue(request.untrustedScreenText.contains("19 giờ"))
        panel.onReplyResponse(request.id, replyJson(), 1_100)
        assertTrue((panel.state.value as PanelState.Replies).reply.canInsert)
    }

    @Test fun dismissAndNewRequestDropLateResponses() = runTest {
        preview()
        val old = panel.onReplySendConfirmed(true)!!
        panel.dismiss()
        panel.onReplyResponse(old.id, replyJson(), 1_100)
        assertEquals(PanelState.Collapsed, panel.state.value)
        assertNull(panel.capturedPackage)
        preview()
        val next = panel.onReplySendConfirmed(true)!!
        panel.onReplyResponse(old.id, replyJson(), 1_100)
        assertTrue(panel.state.value is PanelState.AwaitingReply)
        panel.onReplyResponse(next.id, replyJson(), 1_100)
        assertTrue(panel.state.value is PanelState.Replies)
    }

    @Test fun editorChangesInvalidateEvenIfTextMightLaterMatchAgain() = runTest {
        preview()
        val request = panel.onReplySendConfirmed(true)!!
        panel.onEditorChanged(pkg)
        panel.onReplyResponse(request.id, replyJson(), 1_100)
        assertEquals(PanelState.Collapsed, panel.state.value)
        assertNull(panel.capturedPackage)
    }

    @Test fun samePackageDifferentWindowInvalidatesResponse() = runTest {
        preview()
        val request = panel.onReplySendConfirmed(true)!!
        panel.onWindowChanged(pkg, 8)
        panel.onReplyResponse(request.id, replyJson(), 1_100)
        assertEquals(PanelState.Collapsed, panel.state.value)
    }

    @Test fun cancelWhilePolicyReadSuspends_preventsLateClipboardWrite() = runTest {
        var gate: CompletableDeferred<Unit>? = null
        val blockingProfiles = object : AppProfileRepository {
            override val excludedPackages: Flow<Set<String>> = flow {
                gate?.await()
                emit(emptySet())
            }
        }
        val localBubble = BubbleCoordinator(settings, blockingProfiles, own).apply {
            onServiceConnected()
            onForegroundPackageChanged(pkg)
        }
        val localPanel = PanelCoordinator(localBubble, blockingProfiles, own, ReplyJsonParser())
        localPanel.onBubbleTapped(source)
        localPanel.onActionSelected(AiAction.REPLY)
        val request = localPanel.onReplySendConfirmed(true)!!
        localPanel.onReplyResponse(request.id, replyJson(), 1_100)
        localPanel.onReplyAction(ReplyOperation.COPY, 0)
        gate = CompletableDeferred()
        var copies = 0
        launch { localPanel.onReplyActionConfirm(null, ReplyClipboard { copies++; true }) }
        runCurrent()
        localPanel.onReplyActionCancel()
        gate!!.complete(Unit)
        advanceUntilIdle()
        assertEquals(0, copies)
    }

    @Test fun switchingAppsClearsResultsAndPendingCopy() = runTest {
        preview()
        val request = panel.onReplySendConfirmed(true)!!
        panel.onReplyResponse(request.id, replyJson(), 1_100)
        panel.onReplyAction(ReplyOperation.COPY, 0)
        panel.onForegroundChanged("different.app")
        var copies = 0
        panel.onReplyActionConfirm(null, ReplyClipboard { copies++; true })
        assertEquals(0, copies)
        assertEquals(PanelState.Collapsed, panel.state.value)
    }

    @Test fun newlyExcludedPackagePreventsInsertionAtConfirmation() = runTest {
        preview()
        val request = panel.onReplySendConfirmed(true)!!
        panel.onReplyResponse(request.id, replyJson(), 1_100)
        panel.onReplyAction(ReplyOperation.INSERT, 0)
        profiles.excluded.value = setOf(pkg)
        var writes = 0
        var copies = 0
        panel.onReplyActionConfirm(TextInserter { _, _ -> writes++; InsertionResult.VERIFIED }, ReplyClipboard { copies++; true })
        assertEquals(0, writes)
        assertEquals(0, copies)
        assertEquals(PanelState.Collapsed, panel.state.value)
        assertNull(panel.capturedPackage)
        assertFalse(panel.stageOf(panel.state.value).contains("Tối nay"))
    }

    @Test fun lockOrRevokedConsentAfterResult_preventsInsertion() = runTest {
        for (lock in listOf(true, false)) {
            panel.dismiss()
            bubble.onScreenLockChanged(false)
            settings.acceptConsent(ConsentPolicy.CURRENT_VERSION, 1)
            preview()
            val request = panel.onReplySendConfirmed(true)!!
            panel.onReplyResponse(request.id, replyJson(), 1_100)
            panel.onReplyAction(ReplyOperation.INSERT, 0)
            if (lock) bubble.onScreenLockChanged(true) else settings.revokeConsent()
            var writes = 0
            panel.onReplyActionConfirm(TextInserter { _, _ -> writes++; InsertionResult.VERIFIED }, null)
            assertEquals(0, writes)
        }
    }

    @Test fun serviceOffOrConsentMissingCannotPrepareRequest() = runTest {
        preview()
        settings.revokeConsent()
        assertNull(panel.onReplySendConfirmed(true))
        settings.acceptConsent(ConsentPolicy.CURRENT_VERSION, 1)
        bubble.onServiceDisconnected()
        assertNull(panel.onReplySendConfirmed(true))
    }
}
