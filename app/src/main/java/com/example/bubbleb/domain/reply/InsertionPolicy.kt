package com.example.bubbleb.domain.reply

import com.example.bubbleb.domain.model.InputSnapshot
import com.example.bubbleb.domain.model.InputTarget

data class EditableTarget(
    val identity: InputTarget?,
    val text: String?,
    val selectionStart: Int,
    val selectionEnd: Int,
    val editable: Boolean,
    val focused: Boolean,
    val visible: Boolean,
    val enabled: Boolean,
    val password: Boolean,
    val supportsSetText: Boolean,
)

enum class InsertionResult {
    VERIFIED, UNVERIFIED, TARGET_CHANGED, UNSUPPORTED, DRAFT_PRESENT, EXPIRED, BLOCKED, FAILED,
}

/** Empty really means empty: spaces and hints are never treated as disposable drafts. */
object InsertionPolicy {
    fun canOffer(snapshot: InputSnapshot?, nowElapsedMs: Long): Boolean = snapshot != null &&
        !snapshot.isPassword && snapshot.isTextComplete && snapshot.text.isEmpty() &&
        snapshot.selectionStart == 0 && snapshot.selectionEnd == 0 &&
        snapshot.target?.let { it.packageName.isNotBlank() && it.windowId >= 0 && it.uniqueId.isNotBlank() } == true &&
        snapshot.capturedAtElapsedMs?.let { nowElapsedMs >= it && nowElapsedMs - it <= ReplyLimits.MAX_TARGET_AGE_MS } == true

    fun validate(snapshot: InputSnapshot, current: EditableTarget, nowElapsedMs: Long): InsertionResult? {
        val time = snapshot.capturedAtElapsedMs ?: return InsertionResult.UNSUPPORTED
        if (nowElapsedMs < time || nowElapsedMs - time > ReplyLimits.MAX_TARGET_AGE_MS) return InsertionResult.EXPIRED
        if (snapshot.isPassword || current.password) return InsertionResult.BLOCKED
        if (!snapshot.isTextComplete || snapshot.target == null || current.identity == null) return InsertionResult.UNSUPPORTED
        if (snapshot.target != current.identity || !current.focused || !current.visible || !current.enabled || !current.editable) {
            return InsertionResult.TARGET_CHANGED
        }
        if (current.text == null) return InsertionResult.UNSUPPORTED
        if (snapshot.text.isNotEmpty() || current.text.isNotEmpty()) return InsertionResult.DRAFT_PRESENT
        if (snapshot.selectionStart != 0 || snapshot.selectionEnd != 0 || current.selectionStart != 0 || current.selectionEnd != 0) {
            return InsertionResult.TARGET_CHANGED
        }
        if (!current.supportsSetText || !canOffer(snapshot, nowElapsedMs)) return InsertionResult.UNSUPPORTED
        return null
    }
}

/** One synchronous user-confirmed operation. Implementations must reacquire and release nodes. */
fun interface TextInserter {
    fun insert(snapshot: InputSnapshot, text: String): InsertionResult
}

/** Only invoked after the user confirms replacement of clipboard contents. Never reads the clipboard. */
fun interface ReplyClipboard {
    fun copy(text: String): Boolean
}
