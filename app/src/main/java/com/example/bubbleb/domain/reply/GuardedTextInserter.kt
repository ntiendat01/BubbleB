package com.example.bubbleb.domain.reply

import com.example.bubbleb.domain.Clock
import com.example.bubbleb.domain.model.InputSnapshot
import com.example.bubbleb.domain.text.TextLimits

/** A short-lived synchronous platform session. No implementation may retain it for an AI call. */
interface EditorSession : AutoCloseable {
    fun isLocked(): Boolean
    fun readFocused(): EditableTarget?
    fun setText(text: String): Boolean
}

fun interface EditorSessionSource {
    fun open(): EditorSession
}

/** One attempt, empty drafts only, and no automatic clipboard fallback or message sending. */
class GuardedTextInserter(private val source: EditorSessionSource, private val elapsedClock: Clock) : TextInserter {
    override fun insert(snapshot: InputSnapshot, text: String): InsertionResult {
        if (text.isBlank() || TextLimits.codePointLength(text) > ReplyLimits.MAX_OPTION_CODE_POINTS ||
            !InsertionPolicy.canOffer(snapshot, elapsedClock.nowMs())) return InsertionResult.UNSUPPORTED
        var writeAttempted = false
        return try {
            source.open().use { editor ->
                if (editor.isLocked()) return InsertionResult.BLOCKED
                val before = editor.readFocused() ?: return InsertionResult.TARGET_CHANGED
                InsertionPolicy.validate(snapshot, before, elapsedClock.nowMs())?.let { return it }
                if (editor.isLocked()) return InsertionResult.BLOCKED
                val immediate = editor.readFocused() ?: return InsertionResult.TARGET_CHANGED
                InsertionPolicy.validate(snapshot, immediate, elapsedClock.nowMs())?.let { return it }
                writeAttempted = true
                if (!editor.setText(text)) return InsertionResult.UNVERIFIED
                val after = editor.readFocused() ?: return InsertionResult.UNVERIFIED
                if (after.identity == snapshot.target && after.focused && after.visible && after.enabled &&
                    after.editable && !after.password && after.text == text && !editor.isLocked()) {
                    InsertionResult.VERIFIED
                } else InsertionResult.UNVERIFIED
            }
        } catch (_: RuntimeException) {
            if (writeAttempted) InsertionResult.UNVERIFIED else InsertionResult.FAILED
        }
    }
}
