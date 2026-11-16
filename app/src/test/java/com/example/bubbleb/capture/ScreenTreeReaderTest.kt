package com.example.bubbleb.capture

import com.example.bubbleb.domain.CaptureLimits
import com.example.bubbleb.domain.capture.ScreenTreeReader
import com.example.bubbleb.domain.model.CaptureFailure
import com.example.bubbleb.domain.model.CaptureResult
import com.example.bubbleb.domain.model.ElementRole
import com.example.bubbleb.domain.text.TextLimits
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ScreenTreeReaderTest {
    private val reader = ScreenTreeReader(SteppingClock(0))

    private fun read(root: FakeScreenNode, r: ScreenTreeReader = reader) = r.read(root, ScreenFixtures.PKG, "Chat", 1L)
    private fun success(root: FakeScreenNode, r: ScreenTreeReader = reader) = read(root, r) as CaptureResult.Success

    @Test fun chat_keepsOrderRolesAndFocusedInput() {
        val result = success(ScreenFixtures.chat())
        val texts = result.context.elements.map { it.text }
        assertEquals(listOf("Lan", "Tối nay 19:30 đi ăn không?", "Gọi mình 0912 345 678 nhé", "Mình đang tới", "Gửi"), texts)
        assertEquals(ElementRole.INPUT, result.context.elements[3].role)
        assertEquals(ElementRole.LABEL, result.context.elements[4].role)
        assertEquals("Mình đang tới", result.context.focusedInput?.text)
        assertEquals(2, result.context.focusedInput?.selectionStart)
    }

    @Test fun hintTextIsNotTreatedAsDraft() {
        val root = FakeScreenNode(
            children = listOf(
                FakeScreenNode(text = "Xin chào"),
                FakeScreenNode(text = "Nhập tin nhắn", isEditable = true, isFocused = true, isShowingHintText = true),
            ),
        )
        assertEquals("", success(root).context.focusedInput?.text)
    }

    @Test fun emptyTree_failsClosedAsNoText() =
        assertEquals(CaptureFailure.NO_TEXT, (read(ScreenFixtures.emptyTree()) as CaptureResult.Failure).reason)

    @Test fun protectedView_failsClosedAsNoText() =
        assertEquals(CaptureFailure.NO_TEXT, (read(ScreenFixtures.protectedView()) as CaptureResult.Failure).reason)

    @Test fun missingFocusedInput_hasNoInputSnapshot() {
        val result = success(ScreenFixtures.noFocusedInput())
        assertNull(result.context.focusedInput)
    }

    @Test fun passwordField_blocksWholeScreen() =
        assertEquals(CaptureFailure.SENSITIVE_SCREEN, (read(ScreenFixtures.withPassword()) as CaptureResult.Failure).reason)

    @Test fun invisibleAndForeignNodesAreSkippedWithSubtree() {
        val root = FakeScreenNode(
            children = listOf(
                FakeScreenNode(text = "hiện"),
                FakeScreenNode(text = "ẩn", isVisibleToUser = false, children = listOf(FakeScreenNode(text = "con ẩn"))),
                FakeScreenNode(packageName = "com.other", text = "app khác"),
            ),
        )
        assertEquals(listOf("hiện"), success(root).context.elements.map { it.text })
    }

    @Test fun nodeCapEnforced() {
        val result = success(ScreenFixtures.wide())
        assertEquals(CaptureLimits.MAX_NODES, result.stats.visitedNodes)
        assertTrue(result.stats.hitNodeCap)
    }

    @Test fun depthCapEnforced() {
        val result = success(ScreenFixtures.deep())
        assertEquals(CaptureLimits.MAX_DEPTH, result.stats.maxDepthReached)
        assertTrue(result.stats.hitDepthCap)
        assertFalse(result.context.elements.any { it.text == "level 21" })
    }

    @Test fun timeBudgetEnforced() {
        val slow = ScreenTreeReader(SteppingClock(50))
        val result = success(ScreenFixtures.wide(100), slow)
        assertTrue(result.stats.hitTimeBudget)
        assertTrue(result.stats.visitedNodes < 100)
    }

    @Test fun perNodeAndTotalTextCapsEnforced() {
        val result = success(ScreenFixtures.longDocument(paragraphs = 60))
        assertTrue(result.context.elements.all { TextLimits.codePointLength(it.text) <= CaptureLimits.MAX_NODE_CODE_POINTS })
        val total = result.context.elements.sumOf { TextLimits.codePointLength(it.text) }
        assertTrue(total <= CaptureLimits.MAX_PAYLOAD_CODE_POINTS)
        assertTrue(result.stats.truncatedText)
    }

    @Test fun consecutiveDuplicatesDropped() {
        val root = FakeScreenNode(children = listOf(FakeScreenNode(text = "a"), FakeScreenNode(text = "a"), FakeScreenNode(text = "b")))
        assertEquals(listOf("a", "b"), success(root).context.elements.map { it.text })
    }

    @Test fun controlAndBidiCharactersNormalized() {
        val root = FakeScreenNode(children = listOf(FakeScreenNode(text = "a\u202Eb\u0007  c\n\nd")))
        assertEquals("ab c\n\nd", success(root).context.elements.single().text)
    }
}
