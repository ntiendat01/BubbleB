package com.example.bubbleb.ui.overlay

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import com.example.bubbleb.R
import com.example.bubbleb.domain.llm.AiError
import com.example.bubbleb.presentation.PanelState
import com.example.bubbleb.presentation.ReplyFeedback

@Composable
fun AiOutputContent(state: PanelState.Output, callbacks: PanelCallbacks) {
    if (state.loading) {
        CircularProgressIndicator()
        Text(stringResource(R.string.ai_loading))
    }
    if (state.jsonFallback) Text(stringResource(R.string.ai_json_fallback))
    state.error?.let { Text(stringResource(errorText(it)), color = MaterialTheme.colorScheme.error) }
    Text(state.text) // Never HTML, executable Markdown or automatically opened links.
    state.usage?.let { Text(stringResource(R.string.ai_usage, it.input?.toString() ?: "?", it.output?.toString() ?: "?")) }
    if (!state.loading) {
        if (state.copyPending) {
            Text(stringResource(R.string.reply_copy_warning))
            Button(onClick = callbacks::onOutputCopyConfirm, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.reply_confirm_copy)) }
            OutlinedButton(onClick = callbacks::onOutputCopyCancel, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.cancel)) }
        } else {
            if (state.text.isNotBlank()) OutlinedButton(onClick = callbacks::onOutputCopyRequest, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.reply_copy)) }
            OutlinedButton(onClick = callbacks::onOutputReview, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.ai_review_again)) }
        }
    } else OutlinedButton(onClick = callbacks::onClose, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.ai_cancel)) }
    state.copyFeedback?.let {
        Text(stringResource(if (it == ReplyFeedback.COPIED) R.string.reply_copied else R.string.reply_copy_failed),
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
    }
}

private fun errorText(error: AiError): Int = when (error) {
    AiError.CONFIG_CHANGED -> R.string.ai_config_changed
    AiError.KEY_REQUIRED -> R.string.provider_key_required
    AiError.AUTH -> R.string.ai_auth
    AiError.RATE_LIMIT -> R.string.ai_rate_limit
    AiError.SERVER -> R.string.ai_server
    AiError.TIMEOUT -> R.string.ai_timeout
    AiError.NETWORK -> R.string.ai_network
    AiError.PARTIAL_STREAM -> R.string.ai_partial
    AiError.OUTPUT_LIMIT -> R.string.ai_output_limit
    AiError.STREAM_UNSUPPORTED -> R.string.ai_stream_unsupported
    AiError.STORAGE -> R.string.provider_storage_failed
    AiError.HTTP, AiError.INVALID_RESPONSE -> R.string.ai_invalid_response
}
