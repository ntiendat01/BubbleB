package com.example.bubbleb.presentation

import com.example.bubbleb.capture.ScreenFixtures
import com.example.bubbleb.data.reply.ReplyJsonParser
import com.example.bubbleb.domain.Clock
import com.example.bubbleb.domain.ConsentPolicy
import com.example.bubbleb.domain.llm.*
import com.example.bubbleb.domain.model.AiAction
import com.example.bubbleb.domain.model.UserSettings
import com.example.bubbleb.llm.FakeAiProviders
import com.example.bubbleb.reply.replyJson
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PanelAiFlowTest {
    private val profiles = FakeAppProfileRepository()
    private val settings = FakeSettingsRepository(UserSettings(ConsentPolicy.CURRENT_VERSION, 1))
    private val bubble = BubbleCoordinator(settings, profiles, "com.example.bubbleb").apply {
        onServiceConnected(); onForegroundPackageChanged(ScreenFixtures.PKG)
    }
    private val providers = FakeAiProviders()
    private val usage = UsageCounter()

    private fun panel(gateway: LlmGateway) = PanelCoordinator(bubble, profiles, "com.example.bubbleb", ReplyJsonParser(),
        providers, PanelRequestRunner(providers, gateway, usage), Clock { 1_100 })

    @Test fun openingAndPreviewNeverCallGateway_explicitSendDoes() = runTest {
        var calls = 0
        var last: LlmRequest? = null
        val controller = panel(LlmGateway { _, request ->
            calls++; last = request
            flowOf(LlmEvent.Text("Synthetic summary"), LlmEvent.Complete(null))
        })
        controller.onBubbleTapped(FakeCaptureSource(ScreenFixtures.chat()))
        controller.onActionSelected(AiAction.SUMMARIZE)
        assertEquals(0, calls)
        controller.onSend(this)
        advanceUntilIdle()
        assertEquals(1, calls)
        assertFalse(last!!.untrustedText.contains("0912"))
        assertFalse(last!!.untrustedText.contains("Mình đang tới"))
        assertTrue(last!!.stream)
        assertEquals("Synthetic summary", (controller.state.value as PanelState.Output).text)
        assertFalse((controller.state.value as PanelState.Output).loading)
        assertEquals(1, usage.totals.value.requests)
    }

    @Test fun changedProviderRequiresNewReviewAndSendsNothing() = runTest {
        var calls = 0
        val controller = panel(LlmGateway { _, _ -> calls++; flowOf(LlmEvent.Complete(null)) })
        controller.onBubbleTapped(FakeCaptureSource(ScreenFixtures.chat()))
        controller.onActionSelected(AiAction.EXPLAIN)
        providers.status.value = providers.status.value.copy(revision = 2)
        controller.onSend(this)
        advanceUntilIdle()
        assertEquals(0, calls)
        assertEquals(AiError.CONFIG_CHANGED, (controller.state.value as PanelState.Output).error)
        controller.onOutputReview()
        assertTrue(controller.state.value is PanelState.PreviewConsent)
        assertEquals(2L, (controller.state.value as PanelState.PreviewConsent).provider.revision)
    }

    @Test fun dismissCancelsActiveFlowAndClearsPartialText() = runTest {
        var cancelled = false
        val started = CompletableDeferred<Unit>()
        val controller = panel(LlmGateway { _, _ -> flow {
            try { emit(LlmEvent.Text("Synthetic partial")); started.complete(Unit); awaitCancellation() }
            finally { cancelled = true }
        } })
        controller.onBubbleTapped(FakeCaptureSource(ScreenFixtures.chat()))
        controller.onActionSelected(AiAction.EXPLAIN)
        controller.onSend(this)
        runCurrent()
        started.await()
        controller.dismiss()
        advanceUntilIdle()
        assertTrue(cancelled)
        assertEquals(PanelState.Collapsed, controller.state.value)
        assertNull(controller.capturedPackage)
    }

    @Test fun replyIsNonstreamAndActionableOnlyAfterComplete() = runTest {
        var request: LlmRequest? = null
        val gate = CompletableDeferred<Unit>()
        val controller = panel(LlmGateway { _, input -> request = input; flow {
            emit(LlmEvent.Text(replyJson()))
            gate.await()
            emit(LlmEvent.Complete(null))
        } })
        controller.onBubbleTapped(FakeCaptureSource(ScreenFixtures.chat()))
        controller.onActionSelected(AiAction.REPLY)
        controller.onSend(this)
        runCurrent()
        assertFalse(request!!.stream)
        assertTrue(controller.state.value is PanelState.AwaitingReply)
        gate.complete(Unit)
        advanceUntilIdle()
        assertTrue(controller.state.value is PanelState.Replies)
    }

    @Test fun translateLanguageAndInputCapAreExactlyTheReviewedPayload() = runTest {
        providers.status.value = providers.status.value.copy(config = ProviderConfig(inputLimit = 100))
        var sent: LlmRequest? = null
        val controller = panel(LlmGateway { _, request -> sent = request; flowOf(LlmEvent.Text("Synthetic translation"), LlmEvent.Complete(null)) })
        controller.onBubbleTapped(FakeCaptureSource(ScreenFixtures.longDocument()))
        controller.onActionSelected(AiAction.TRANSLATE)
        controller.onTranslationSelected(TranslationLanguage.ENGLISH)
        val review = controller.state.value as PanelState.PreviewConsent
        assertTrue(review.preview.codePoints <= 100)
        controller.onSend(this)
        advanceUntilIdle()
        assertEquals(review.preview.text, sent!!.untrustedText)
        assertTrue(sent!!.instruction.contains("English"))
    }

    @Test fun repeatedSendAndRetryNeverReplayWithoutAnotherConfirmation() = runTest {
        var calls = 0
        val controller = panel(LlmGateway { _, _ -> calls++; flowOf(LlmEvent.Failed(AiError.TIMEOUT)) })
        controller.onBubbleTapped(FakeCaptureSource(ScreenFixtures.chat()))
        controller.onActionSelected(AiAction.SUMMARIZE)
        controller.onSend(this); controller.onSend(this)
        advanceUntilIdle()
        assertEquals(1, calls)
        controller.onOutputReview()
        advanceUntilIdle()
        assertEquals(1, calls)
        controller.onSend(this)
        advanceUntilIdle()
        assertEquals(2, calls)
    }

    @Test fun editorAndAppSwitchCancelPendingTransport() = runTest {
        val invalidators: List<(PanelCoordinator) -> Unit> = listOf(
            { it.onEditorChanged(ScreenFixtures.PKG) },
            { it.onForegroundChanged("different.app") },
            { it.onWindowChanged(ScreenFixtures.PKG, 99) },
        )
        for (invalidate in invalidators) {
            var cancelled = false
            val controller = panel(LlmGateway { _, _ -> flow {
                try { awaitCancellation() } finally { cancelled = true }
            } })
            controller.onBubbleTapped(FakeCaptureSource(ScreenFixtures.chat()))
            controller.onActionSelected(AiAction.SUMMARIZE)
            controller.onSend(this)
            runCurrent()
            invalidate(controller)
            advanceUntilIdle()
            assertTrue(cancelled)
            assertEquals(PanelState.Collapsed, controller.state.value)
        }
    }
}
