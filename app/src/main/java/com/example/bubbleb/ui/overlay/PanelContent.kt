package com.example.bubbleb.ui.overlay

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.bubbleb.R
import com.example.bubbleb.domain.model.AiAction
import com.example.bubbleb.presentation.BlockedReason
import com.example.bubbleb.presentation.PanelState
import com.example.bubbleb.presentation.ReplyOperation
import com.example.bubbleb.presentation.UnavailableReason

/** Every write requires a separate user confirmation; no callback sends a message. */
interface PanelCallbacks {
    fun onClose()
    fun onAction(action: AiAction)
    fun onPreviewCancel()
    fun onReplyAction(operation: ReplyOperation, index: Int)
    fun onReplyConfirm()
    fun onReplyCancel()
}

/** Non-focusable overlay panel (no text input). Scrolls within [maxHeight]. */
@Composable
fun PanelContent(state: PanelState, appLabelFallback: String, maxHeight: Dp, callbacks: PanelCallbacks) {
    Surface(
        modifier = Modifier.fillMaxWidth().heightIn(max = maxHeight),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 8.dp,
    ) {
        Column(Modifier.padding(16.dp)) {
            val label = (state as? PanelState.Actions)?.appLabel
                ?: (state as? PanelState.PreviewConsent)?.preview?.appLabel
                ?: (state as? PanelState.AwaitingReply)?.appLabel
                ?: (state as? PanelState.Replies)?.appLabel
                ?: appLabelFallback
            PanelHeader(label, callbacks::onClose)
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                when (state) {
                    PanelState.Collapsed -> Unit
                    PanelState.Capturing -> Capturing()
                    is PanelState.Actions -> ActionList(state, callbacks)
                    is PanelState.PreviewConsent -> Review(state, callbacks)
                    is PanelState.AwaitingReply -> Text(stringResource(R.string.reply_waiting))
                    is PanelState.Replies -> ReplyResultContent(state.reply, callbacks)
                    is PanelState.Unavailable -> Unavailable(state.reason)
                    is PanelState.Blocked -> Message(stringResource(blockedText(state.reason)))
                }
            }
        }
    }
}

@Composable
private fun PanelHeader(appLabel: String, onClose: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            stringResource(R.string.panel_header, appLabel),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.weight(1f),
            maxLines = 1,
        )
        IconButton(onClick = onClose) {
            Icon(painterResource(R.drawable.ic_close), contentDescription = stringResource(R.string.panel_close))
        }
    }
}

@Composable
private fun Capturing() {
    Row(verticalAlignment = Alignment.CenterVertically) {
        CircularProgressIndicator(Modifier.padding(4.dp).width(24.dp))
        Spacer(Modifier.width(12.dp))
        Text(stringResource(R.string.panel_capturing))
    }
}

@Composable
private fun ActionList(state: PanelState.Actions, callbacks: PanelCallbacks) {
    Text(stringResource(R.string.panel_read_count, state.visibleCodePoints), style = MaterialTheme.typography.bodyMedium)
    if (state.truncated) Text(stringResource(R.string.panel_truncated), style = MaterialTheme.typography.bodySmall)
    ActionButton(R.string.action_reply, enabled = state.replyEligible) { callbacks.onAction(AiAction.REPLY) }
    if (!state.replyEligible) {
        Text(stringResource(R.string.action_reply_unavailable), style = MaterialTheme.typography.bodySmall)
    }
    ActionButton(R.string.action_summarize) { callbacks.onAction(AiAction.SUMMARIZE) }
    ActionButton(R.string.action_explain) { callbacks.onAction(AiAction.EXPLAIN) }
    ActionButton(R.string.action_translate) { callbacks.onAction(AiAction.TRANSLATE) }
}

@Composable
private fun ActionButton(textRes: Int, enabled: Boolean = true, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, enabled = enabled, modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(textRes))
    }
}

@Composable
private fun Review(state: PanelState.PreviewConsent, callbacks: PanelCallbacks) {
    val preview = state.preview
    Text(stringResource(R.string.review_title), style = MaterialTheme.typography.titleSmall)
    Text(stringResource(R.string.review_provider_none), style = MaterialTheme.typography.bodySmall)
    Text(stringResource(R.string.review_count, preview.codePoints), style = MaterialTheme.typography.bodySmall)
    if (preview.hasRedactions) Text(stringResource(R.string.review_redacted), color = MaterialTheme.colorScheme.primary)
    if (preview.mostlyRedacted) Text(stringResource(R.string.review_mostly_redacted), color = MaterialTheme.colorScheme.error)
    Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(8.dp)) {
        Text(
            preview.text.ifEmpty { stringResource(R.string.review_empty) },
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.fillMaxWidth().padding(8.dp),
        )
    }
    if (!state.providerConfigured) Text(stringResource(R.string.review_send_disabled), style = MaterialTheme.typography.bodySmall)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = callbacks::onPreviewCancel, modifier = Modifier.weight(1f)) {
            Text(stringResource(R.string.cancel))
        }
        // M2 has no network path: this button is always disabled until a provider exists (M3).
        Button(onClick = {}, enabled = state.canSend, modifier = Modifier.weight(1f)) {
            Text(stringResource(R.string.send_to_ai))
        }
    }
}

@Composable
private fun Unavailable(reason: UnavailableReason) {
    when (reason) {
        UnavailableReason.PERMISSION_REQUIRED -> Message(stringResource(R.string.error_no_permission))
        UnavailableReason.CAPTURE_ERROR -> Message(stringResource(R.string.error_capture_failed))
        UnavailableReason.NO_ACCESSIBLE_CONTENT -> {
            Message(stringResource(R.string.error_no_content))
            Text(stringResource(R.string.error_share_hint), style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun Message(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium)
}

private fun blockedText(reason: BlockedReason): Int = when (reason) {
    BlockedReason.EXCLUDED_APP -> R.string.error_excluded
    BlockedReason.SENSITIVE_SCREEN -> R.string.error_sensitive
    BlockedReason.OWN_APP -> R.string.error_own_app
    BlockedReason.SCREEN_LOCKED -> R.string.error_locked
}
