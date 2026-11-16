package com.example.bubbleb.ui.main

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.bubbleb.R
import com.example.bubbleb.domain.llm.KeyIssue
import com.example.bubbleb.domain.llm.ProviderSource
import com.example.bubbleb.presentation.ProviderNotice
import com.example.bubbleb.presentation.ProviderViewModel

@Composable
fun ProviderSettingsScreen(viewModel: ProviderViewModel, onBack: () -> Unit) {
    val form by viewModel.form.collectAsStateWithLifecycle()
    val status by viewModel.status.collectAsStateWithLifecycle()
    val usage by viewModel.usage.collectAsStateWithLifecycle()
    DisposableEffect(viewModel) { onDispose { viewModel.clearDraft() } }
    Column(Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()).padding(16.dp)
        .testTag(TAG_PROVIDER_SCREEN), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.provider_title), style = MaterialTheme.typography.headlineMedium)
        Text(stringResource(if (status.ready) R.string.provider_ready else R.string.not_configured))
        if (status.source == ProviderSource.DEBUG) Text(stringResource(R.string.provider_debug_note))
        if (status.keyIssue == KeyIssue.INVALIDATED || status.keyIssue == KeyIssue.UNAVAILABLE) {
            Text(stringResource(R.string.provider_key_unavailable), color = MaterialTheme.colorScheme.error)
        }
        if (form.loading || form.saving || form.testing) CircularProgressIndicator()
        ProviderFields(form, viewModel::edit)
        form.notice?.let { notice ->
            val text = when (notice) {
                ProviderNotice.SAVED -> R.string.provider_saved
                ProviderNotice.REMOVED -> R.string.provider_removed
                ProviderNotice.INVALID_CONFIG -> R.string.provider_invalid
                ProviderNotice.KEY_REQUIRED -> R.string.provider_key_required
                ProviderNotice.STORAGE -> R.string.provider_storage_failed
                ProviderNotice.TEST_OK -> R.string.provider_test_ok
                ProviderNotice.TEST_FAILED -> R.string.provider_test_failed
            }
            Text(stringResource(text))
        }
        Button(onClick = viewModel::save, enabled = !form.loading && !form.saving, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.provider_save))
        }
        Text(stringResource(R.string.provider_test_disclosure), style = MaterialTheme.typography.bodySmall)
        OutlinedButton(onClick = viewModel::testConnection,
            enabled = !form.loading && !form.saving && !form.testing && status.ready && form.key.isEmpty() && form.config() == status.config,
            modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.provider_test)) }
        if (form.testing) OutlinedButton(onClick = viewModel::cancelTest, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.cancel))
        }
        Text(stringResource(R.string.provider_usage, usage.requests, usage.inputTokens, usage.outputTokens), style = MaterialTheme.typography.bodySmall)
        OutlinedButton(onClick = viewModel::remove, enabled = !form.loading && !form.saving, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.provider_remove))
        }
        OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.back)) }
    }
}

const val TAG_PROVIDER_SCREEN = "provider_settings_screen"
