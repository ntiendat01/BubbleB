package com.example.bubbleb.capture

import com.example.bubbleb.domain.CaptureLimits
import com.example.bubbleb.domain.capture.ScreenTreeReader
import com.example.bubbleb.domain.model.CaptureResult
import com.example.bubbleb.domain.model.InputSnapshot
import com.example.bubbleb.domain.reply.InsertionPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class InputSnapshotTest {
    private fun editor(text: String? = "") = FakeScreenNode(
        text = text, isEditable = true, isFocused = true,
        selectionStart = 0, selectionEnd = 0, windowId = 7, uniqueId = "stable-editor",
    )

    private fun capture(vararg inputs: FakeScreenNode): InputSnapshot? {
        val root = FakeScreenNode(children = listOf(FakeScreenNode(text = "Visible conversation")) + inputs)
        val result = ScreenTreeReader(SteppingClock(0)).read(root, ScreenFixtures.PKG, "Fixture", 1_000) as CaptureResult.Success
        return result.context.focusedInput
    }

    @Test fun capturesImmutableIdentityAndMonotonicTimestamp() {
        val input = capture(editor())!!
        assertEquals(ScreenFixtures.PKG, input.target!!.packageName)
        assertEquals(7, input.target!!.windowId)
        assertEquals("stable-editor", input.target!!.uniqueId)
        assertEquals(0L, input.capturedAtElapsedMs)
        assertTrue(InsertionPolicy.canOffer(input, 100))
    }

    @Test fun longDraftIsBoundedAndMarkedIncomplete() {
        val input = capture(editor("x".repeat(CaptureLimits.MAX_NODE_CODE_POINTS + 1)))!!
        assertEquals(CaptureLimits.MAX_NODE_CODE_POINTS, input.text.length)
        assertFalse(input.isTextComplete)
        assertFalse(InsertionPolicy.canOffer(input, 100))
    }

    @Test fun unknownTextDoesNotBecomeSafeEmptyDraft() {
        val input = capture(editor(null))!!
        assertFalse(input.isTextComplete)
        assertFalse(InsertionPolicy.canOffer(input, 100))
    }

    @Test fun multipleFocusedEditorsAreAmbiguous() {
        assertNull(capture(editor(), editor()))
    }

    @Test fun missingStableIdentityIsCopyOnly() {
        val input = capture(FakeScreenNode(text = "", isEditable = true, isFocused = true, selectionStart = 0, selectionEnd = 0))!!
        assertNull(input.target)
        assertFalse(InsertionPolicy.canOffer(input, 100))
    }
}
