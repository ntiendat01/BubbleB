package com.example.bubbleb.presentation

import com.example.bubbleb.domain.model.InputSnapshot
import com.example.bubbleb.domain.reply.InsertionPolicy
import com.example.bubbleb.domain.reply.InsertionResult
import com.example.bubbleb.domain.reply.ReplyClipboard
import com.example.bubbleb.domain.reply.ReplyOutput
import com.example.bubbleb.domain.reply.ReplyParser
import com.example.bubbleb.domain.reply.TextInserter

enum class ReplyOperation { COPY, INSERT }
enum class ReplyFeedback { COPIED, COPY_FAILED, INSERTED, INSERT_UNVERIFIED, INSERT_ABORTED }
data class PendingReplyAction(val operation: ReplyOperation, val index: Int)
data class ReplyUiState(
    val output: ReplyOutput,
    val canInsert: Boolean,
    val pending: PendingReplyAction? = null,
    val feedback: ReplyFeedback? = null,
)

/** Ephemeral result actions; a request step never touches clipboard or editor. */
class ReplySession(private val input: InputSnapshot?, private val parser: ReplyParser) {
    var state = ReplyUiState(ReplyOutput.Empty, canInsert = false)
        private set

    fun accept(response: String, nowElapsedMs: Long) {
        val output = parser.parse(response)
        state = ReplyUiState(output, output is ReplyOutput.Valid && InsertionPolicy.canOffer(input, nowElapsedMs))
    }

    fun request(operation: ReplyOperation, index: Int) {
        if (textAt(index) == null || (operation == ReplyOperation.INSERT && !state.canInsert)) return
        state = state.copy(pending = PendingReplyAction(operation, index), feedback = null)
    }

    fun cancel() { state = state.copy(pending = null) }

    fun confirm(inserter: TextInserter?, clipboard: ReplyClipboard?, allowedToInsert: Boolean) {
        val pending = state.pending ?: return
        val text = textAt(pending.index) ?: return cancel()
        state = state.copy(pending = null) // Confirmation is consumed once, including on failure.
        when (pending.operation) {
            ReplyOperation.COPY -> {
                val copied = try { clipboard?.copy(text) == true } catch (_: RuntimeException) { false }
                state = state.copy(feedback = if (copied) ReplyFeedback.COPIED else ReplyFeedback.COPY_FAILED)
            }
            ReplyOperation.INSERT -> {
                val result = if (allowedToInsert && state.canInsert && input != null && inserter != null) {
                    try { inserter.insert(input, text) } catch (_: RuntimeException) { InsertionResult.UNVERIFIED }
                } else InsertionResult.BLOCKED
                state = state.copy(canInsert = false, feedback = when (result) {
                    InsertionResult.VERIFIED -> ReplyFeedback.INSERTED
                    InsertionResult.UNVERIFIED -> ReplyFeedback.INSERT_UNVERIFIED
                    else -> ReplyFeedback.INSERT_ABORTED
                })
            }
        }
    }

    private fun textAt(index: Int): String? = when (val output = state.output) {
        is ReplyOutput.Valid -> output.options.getOrNull(index)?.text
        is ReplyOutput.CopyOnly -> output.text.takeIf { index == 0 }
        ReplyOutput.Empty -> null
    }
}
