package com.example.bubbleb.ui.overlay

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.bubbleb.R
import com.example.bubbleb.domain.reply.ReplyOutput
import com.example.bubbleb.presentation.ReplyFeedback
import com.example.bubbleb.presentation.ReplyOperation
import com.example.bubbleb.presentation.ReplyUiState

/** Plain text only. Confirmation is inline so the non-focusable overlay retains its close button. */
@Composable
fun ReplyResultContent(state: ReplyUiState, callbacks: PanelCallbacks) {
    Text(stringResource(R.string.reply_result_title), style = MaterialTheme.typography.titleMedium)
    Text(stringResource(R.string.reply_manual_send), style = MaterialTheme.typography.bodySmall)
    if (state.pending != null) {
        val copying = state.pending.operation == ReplyOperation.COPY
        Text(stringResource(if (copying) R.string.reply_copy_warning else R.string.reply_insert_warning))
        val selected = when (val output = state.output) {
            is ReplyOutput.Valid -> output.options.getOrNull(state.pending.index)?.text
            is ReplyOutput.CopyOnly -> output.text
            ReplyOutput.Empty -> null
        }
        selected?.let { Text(it) }
        Button(onClick = callbacks::onReplyConfirm, modifier = Modifier.fillMaxWidth().testTag("reply_confirm")) {
            Text(stringResource(if (copying) R.string.reply_confirm_copy else R.string.reply_confirm_insert))
        }
        OutlinedButton(onClick = callbacks::onReplyCancel, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.cancel))
        }
    } else {
        when (val output = state.output) {
            is ReplyOutput.Valid -> output.options.forEachIndexed { index, option ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(option.label, style = MaterialTheme.typography.titleSmall)
                        Text(option.text, style = MaterialTheme.typography.bodyMedium)
                        ReplyButtons(index, state.canInsert, callbacks)
                    }
                }
            }
            is ReplyOutput.CopyOnly -> {
                Text(stringResource(R.string.reply_invalid_json), color = MaterialTheme.colorScheme.error)
                Text(output.text)
                ReplyButtons(0, canInsert = false, callbacks = callbacks)
            }
            ReplyOutput.Empty -> Text(stringResource(R.string.reply_empty))
        }
        if (!state.canInsert && state.output is ReplyOutput.Valid) {
            Text(stringResource(R.string.reply_copy_only), style = MaterialTheme.typography.bodySmall)
        }
    }
    state.feedback?.let { feedback ->
        val message = when (feedback) {
            ReplyFeedback.COPIED -> R.string.reply_copied
            ReplyFeedback.COPY_FAILED -> R.string.reply_copy_failed
            ReplyFeedback.INSERTED -> R.string.reply_inserted
            ReplyFeedback.INSERT_UNVERIFIED -> R.string.reply_insert_unverified
            ReplyFeedback.INSERT_ABORTED -> R.string.reply_insert_aborted
        }
        Text(stringResource(message), modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
    }
}

@Composable
private fun ReplyButtons(index: Int, canInsert: Boolean, callbacks: PanelCallbacks) {
    OutlinedButton(
        onClick = { callbacks.onReplyAction(ReplyOperation.COPY, index) },
        modifier = Modifier.fillMaxWidth().testTag("reply_copy_$index"),
    ) { Text(stringResource(R.string.reply_copy)) }
    if (canInsert) {
        Button(
            onClick = { callbacks.onReplyAction(ReplyOperation.INSERT, index) },
            modifier = Modifier.fillMaxWidth().testTag("reply_insert_$index"),
        ) { Text(stringResource(R.string.reply_insert)) }
    }
}
