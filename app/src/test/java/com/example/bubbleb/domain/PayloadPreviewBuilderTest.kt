package com.example.bubbleb.domain

import com.example.bubbleb.capture.ScreenFixtures
import com.example.bubbleb.capture.SteppingClock
import com.example.bubbleb.domain.capture.ScreenTreeReader
import com.example.bubbleb.domain.model.AiAction
import com.example.bubbleb.domain.model.CaptureResult
import com.example.bubbleb.domain.model.ElementRole
import com.example.bubbleb.domain.model.IntRect
import com.example.bubbleb.domain.model.ScreenContext
import com.example.bubbleb.domain.model.ScreenElement
import com.example.bubbleb.domain.model.ScreenType
import com.example.bubbleb.domain.privacy.Redactor
import com.example.bubbleb.domain.text.TextLimits
import com.example.bubbleb.domain.usecase.PayloadPreviewBuilder
import com.example.bubbleb.domain.usecase.ScreenHeuristics
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PayloadPreviewBuilderTest {
    private fun capture(root: com.example.bubbleb.capture.FakeScreenNode) =
        (ScreenTreeReader(SteppingClock(0)).read(root, ScreenFixtures.PKG, "Chat", 1L) as CaptureResult.Success).context

    private fun ctx(vararg texts: String) = ScreenContext(
        "p", "App",
        texts.mapIndexed { i, t -> ScreenElement(t, ElementRole.TEXT, IntRect(0, i, 1, i + 1), i, false) },
        null, 0L,
    )

    @Test fun chatPreview_redactsPhone_excludesDraft() {
        val preview = PayloadPreviewBuilder.build(capture(ScreenFixtures.chat(draft = "bí mật nháp")), AiAction.SUMMARIZE, false)
        assertFalse(preview.text.contains("0912"))
        assertTrue(preview.text.contains("[REDACTED_PHONE]"))
        assertFalse(preview.text.contains("bí mật nháp"))
        assertTrue(preview.hasRedactions)
        assertEquals(TextLimits.codePointLength(preview.text), preview.codePoints)
    }

    @Test fun replyCapIsSmallerThanOtherActions() {
        val doc = capture(ScreenFixtures.longDocument(60))
        val reply = PayloadPreviewBuilder.build(doc, AiAction.REPLY, false)
        val summary = PayloadPreviewBuilder.build(doc, AiAction.SUMMARIZE, false)
        assertTrue(reply.codePoints <= CaptureLimits.MAX_REPLY_PAYLOAD_CODE_POINTS)
        assertTrue(summary.codePoints <= CaptureLimits.MAX_PAYLOAD_CODE_POINTS)
        assertTrue(reply.truncated)
        assertTrue(summary.codePoints > reply.codePoints)
    }

    @Test fun mostlyRedactedDetected() {
        val preview = PayloadPreviewBuilder.build(ctx("a@b.com", "0912345678", "ok"), AiAction.EXPLAIN, false)
        assertTrue(preview.mostlyRedacted)
        assertEquals(1, preview.redactionCounts[Redactor.Category.EMAIL])
    }

    @Test fun normalTextNotMostlyRedacted() =
        assertFalse(PayloadPreviewBuilder.build(ctx("Đây là một đoạn văn bản bình thường đủ dài"), AiAction.TRANSLATE, false).mostlyRedacted)

    @Test fun replyEligibility() {
        assertTrue(ScreenHeuristics.isReplyEligible(capture(ScreenFixtures.chat())))
        assertFalse(ScreenHeuristics.isReplyEligible(capture(ScreenFixtures.noFocusedInput())))
    }

    @Test fun classification() {
        assertEquals(ScreenType.CHAT_LIKE, ScreenHeuristics.classify(capture(ScreenFixtures.chat())))
        assertEquals(ScreenType.DOCUMENT_LIKE, ScreenHeuristics.classify(capture(ScreenFixtures.longDocument())))
        assertEquals(ScreenType.UNKNOWN, ScreenHeuristics.classify(ctx("ngắn")))
    }
}
