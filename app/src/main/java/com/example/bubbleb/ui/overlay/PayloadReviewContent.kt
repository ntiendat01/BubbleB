package com.example.bubbleb.ui.overlay

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.bubbleb.R
import com.example.bubbleb.domain.llm.ActionPrompt
import com.example.bubbleb.domain.llm.TranslationLanguage
import com.example.bubbleb.domain.model.AiAction
import com.example.bubbleb.presentation.PanelState
import java.util.Locale

@Composable
fun PayloadReviewContent(state: PanelState.PreviewConsent, callbacks: PanelCallbacks) {
    val preview = state.preview
    val config = state.provider.config
    Text(stringResource(R.string.review_title), style = MaterialTheme.typography.titleSmall)
    if (config == null) Text(stringResource(R.string.review_provider_none)) else {
        Text(config.name, style = MaterialTheme.typography.titleMedium)
        Text(config.baseUrl, style = MaterialTheme.typography.bodySmall)
        Text(config.model, style = MaterialTheme.typography.bodySmall)
    }
    val summary = when (state.action) {
        AiAction.REPLY -> R.string.ai_summary_reply
        AiAction.SUMMARIZE -> R.string.ai_summary_summarize
        AiAction.EXPLAIN -> R.string.ai_summary_explain
        AiAction.TRANSLATE -> R.string.ai_summary_translate
    }
    Text(stringResource(summary))
    if (state.action == AiAction.TRANSLATE) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = state.language == TranslationLanguage.VIETNAMESE,
                onClick = { callbacks.onTranslationSelected(TranslationLanguage.VIETNAMESE) }, label = { Text(stringResource(R.string.ai_vietnamese)) })
            FilterChip(selected = state.language == TranslationLanguage.ENGLISH,
                onClick = { callbacks.onTranslationSelected(TranslationLanguage.ENGLISH) }, label = { Text(stringResource(R.string.ai_english)) })
        }
    }
    Text(stringResource(R.string.review_count, preview.codePoints))
    if (config != null) {
        Text(stringResource(R.string.ai_estimate, ActionPrompt.estimatedInputTokens(preview.codePoints), config.maxOutputTokens), style = MaterialTheme.typography.bodySmall)
        val cost = ActionPrompt.estimatedCost(config, preview.codePoints)
        Text(if (cost == null) stringResource(R.string.ai_price_unknown)
            else stringResource(R.string.ai_price_estimate, String.format(Locale.ROOT, "%.6f", cost)), style = MaterialTheme.typography.bodySmall)
    }
    if (preview.hasRedactions) Text(stringResource(R.string.review_redacted), color = MaterialTheme.colorScheme.primary)
    if (preview.mostlyRedacted) Text(stringResource(R.string.review_mostly_redacted), color = MaterialTheme.colorScheme.error)
    if (preview.truncated) Text(stringResource(R.string.panel_truncated))
    Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(8.dp)) {
        Text(preview.text.ifEmpty { stringResource(R.string.review_empty) }, modifier = Modifier.fillMaxWidth().padding(8.dp))
    }
    if (!state.providerConfigured) Text(stringResource(R.string.review_send_disabled))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = callbacks::onPreviewCancel, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.cancel)) }
        Button(onClick = callbacks::onSend, enabled = state.canSend, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.send_to_ai)) }
    }
}
