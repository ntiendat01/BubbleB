package com.example.bubbleb.reply

import com.example.bubbleb.domain.model.InputSnapshot
import com.example.bubbleb.domain.model.InputTarget
import com.example.bubbleb.domain.reply.EditableTarget
import com.example.bubbleb.domain.reply.InsertionPolicy
import com.example.bubbleb.domain.reply.InsertionResult
import com.example.bubbleb.domain.reply.ReplyLimits
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

fun emptySnapshot() = InputSnapshot(
    text = "", selectionStart = 0, selectionEnd = 0, isPassword = false,
    target = InputTarget("com.example.fixture", 7, "editor-one"),
    capturedAtElapsedMs = 1_000, isTextComplete = true,
)

fun emptyTarget() = EditableTarget(
    identity = emptySnapshot().target, text = "", selectionStart = 0, selectionEnd = 0,
    editable = true, focused = true, visible = true, enabled = true, password = false, supportsSetText = true,
)

class InsertionPolicyTest {
    private val original = emptySnapshot()
    private val current = emptyTarget()

    @Test fun unchangedEmptySupportedInput_isAllowed() {
        assertTrue(InsertionPolicy.canOffer(original, 1_100))
        assertNull(InsertionPolicy.validate(original, current, 1_100))
    }

    @Test fun draftWhitespaceAndNewTextArePreserved() {
        listOf("draft", " ", "\n").forEach { text ->
            assertFalse(InsertionPolicy.canOffer(original.copy(text = text), 1_100))
            assertEquals(InsertionResult.DRAFT_PRESENT, InsertionPolicy.validate(original, current.copy(text = text), 1_100))
        }
    }

    @Test fun packageWindowAndFocusIdentityChangesAbort() {
        val id = current.identity!!
        listOf(
            current.copy(identity = id.copy(packageName = "other")),
            current.copy(identity = id.copy(windowId = 8)),
            current.copy(identity = id.copy(uniqueId = "other-editor")),
            current.copy(focused = false), current.copy(visible = false),
            current.copy(enabled = false), current.copy(editable = false),
            current.copy(selectionStart = -1), current.copy(selectionEnd = 1),
        ).forEach { assertEquals(InsertionResult.TARGET_CHANGED, InsertionPolicy.validate(original, it, 1_100)) }
    }

    @Test fun missingIdentityOrUnknownTextIsUnsupported() {
        assertFalse(InsertionPolicy.canOffer(original.copy(target = null), 1_100))
        assertEquals(InsertionResult.UNSUPPORTED, InsertionPolicy.validate(original, current.copy(identity = null), 1_100))
        assertEquals(InsertionResult.UNSUPPORTED, InsertionPolicy.validate(original, current.copy(text = null), 1_100))
        assertEquals(InsertionResult.UNSUPPORTED, InsertionPolicy.validate(original.copy(isTextComplete = false), current, 1_100))
        assertEquals(InsertionResult.UNSUPPORTED, InsertionPolicy.validate(original, current.copy(supportsSetText = false), 1_100))
    }

    @Test fun passwordIsBlockedAndUnknownCursorCannotBeOffered() {
        assertEquals(InsertionResult.BLOCKED, InsertionPolicy.validate(original, current.copy(password = true), 1_100))
        assertFalse(InsertionPolicy.canOffer(original.copy(isPassword = true), 1_100))
        assertFalse(InsertionPolicy.canOffer(original.copy(selectionStart = null), 1_100))
    }

    @Test fun expiredOrBackwardsClockCannotWrite() {
        listOf(999L, 1_001 + ReplyLimits.MAX_TARGET_AGE_MS).forEach { now ->
            assertFalse(InsertionPolicy.canOffer(original, now))
            assertEquals(InsertionResult.EXPIRED, InsertionPolicy.validate(original, current, now))
        }
    }
}
