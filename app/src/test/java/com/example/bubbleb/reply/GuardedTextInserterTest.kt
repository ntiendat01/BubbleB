package com.example.bubbleb.reply

import com.example.bubbleb.domain.Clock
import com.example.bubbleb.domain.reply.EditableTarget
import com.example.bubbleb.domain.reply.EditorSession
import com.example.bubbleb.domain.reply.EditorSessionSource
import com.example.bubbleb.domain.reply.GuardedTextInserter
import com.example.bubbleb.domain.reply.InsertionResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Fake editor drives the same orchestration used by the Android adapter, including postconditions. */
private class FakeEditor : EditorSession {
    var current: EditableTarget? = emptyTarget()
    var afterWrite: EditableTarget? = null
    var locked = false
    var writes = 0
    var reads = 0
    var closed = false
    var acceptsWrite = true
    var throwsOnWrite = false
    var locksOnSecondCheck = false
    private var lockChecks = 0

    override fun isLocked(): Boolean = locked || (locksOnSecondCheck && ++lockChecks >= 2)
    override fun readFocused(): EditableTarget? { reads++; return current }
    override fun setText(text: String): Boolean {
        writes++
        if (throwsOnWrite) error("synthetic platform error")
        current = afterWrite ?: current?.copy(text = text)
        return acceptsWrite
    }
    override fun close() { closed = true }
}

class GuardedTextInserterTest {
    private fun inserter(editor: FakeEditor) = GuardedTextInserter(EditorSessionSource { editor }, Clock { 1_100 })

    @Test fun successfulWriteIsReacquiredAndVerifiedThenReleased() {
        val editor = FakeEditor()
        assertEquals(InsertionResult.VERIFIED, inserter(editor).insert(emptySnapshot(), "Hello"))
        assertEquals(1, editor.writes)
        assertEquals(3, editor.reads)
        assertEquals("Hello", editor.current!!.text)
        assertTrue(editor.closed)
    }

    @Test fun changedDraftOrTarget_neverWrites() {
        listOf(emptyTarget().copy(text = "preserve me"), emptyTarget().copy(focused = false), null).forEach { target ->
            val editor = FakeEditor().apply { current = target }
            inserter(editor).insert(emptySnapshot(), "Hello")
            assertEquals(0, editor.writes)
            assertEquals(target, editor.current)
            assertTrue(editor.closed)
        }
    }

    @Test fun lockBeforeOrImmediatelyBeforeWrite_aborts() {
        listOf(FakeEditor().apply { locked = true }, FakeEditor().apply { locksOnSecondCheck = true }).forEach { editor ->
            assertEquals(InsertionResult.BLOCKED, inserter(editor).insert(emptySnapshot(), "Hello"))
            assertEquals(0, editor.writes)
            assertTrue(editor.closed)
        }
    }

    @Test fun falseReturnOrExceptionAfterAttempt_isUnverifiedAndNotRetried() {
        listOf(FakeEditor().apply { acceptsWrite = false }, FakeEditor().apply { throwsOnWrite = true }).forEach { editor ->
            assertEquals(InsertionResult.UNVERIFIED, inserter(editor).insert(emptySnapshot(), "Hello"))
            assertEquals(1, editor.writes)
            assertTrue(editor.closed)
        }
    }

    @Test fun trueReturnWithDifferentTargetOrContent_doesNotClaimSuccess() {
        listOf(emptyTarget().copy(text = "different"), emptyTarget().copy(identity = null)).forEach { after ->
            val editor = FakeEditor().apply { afterWrite = after }
            assertEquals(InsertionResult.UNVERIFIED, inserter(editor).insert(emptySnapshot(), "Hello"))
            assertEquals(1, editor.writes)
        }
    }

    @Test fun oldOrNonemptySnapshot_doesNotEvenOpenEditor() {
        var opens = 0
        val guard = GuardedTextInserter(EditorSessionSource { opens++; FakeEditor() }, Clock { 1_100 })
        guard.insert(emptySnapshot().copy(text = "draft"), "Hello")
        guard.insert(emptySnapshot().copy(target = null), "Hello")
        assertEquals(0, opens)
    }
}
