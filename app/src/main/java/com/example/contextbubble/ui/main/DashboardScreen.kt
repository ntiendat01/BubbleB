package com.example.contextbubble.ui.main

import android.content.ActivityNotFoundException
import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.contextbubble.R
import com.example.contextbubble.presentation.DashboardUiState
import com.example.contextbubble.presentation.DashboardViewModel

@Composable
fun DashboardScreen(viewModel: DashboardViewModel, onOpenProviderSettings: () -> Unit) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    DashboardContent(
        state = state,
        onAcceptConsent = viewModel::acceptConsent,
        onRevokeConsent = viewModel::revokeConsent,
        onOpenAccessibilitySettings = {
            // Official Settings navigation only; never auto-grant or bypass restricted settings.
            try {
                context.startActivity(
                    Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            } catch (_: ActivityNotFoundException) {
                context.startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
        },
        onOpenProviderSettings = onOpenProviderSettings,
    )
}

@Composable
fun DashboardContent(
    state: DashboardUiState,
    onAcceptConsent: () -> Unit,
    onRevokeConsent: () -> Unit,
    onOpenAccessibilitySettings: () -> Unit,
    onOpenProviderSettings: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = stringResource(R.string.dashboard_title),
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.semantics { heading() },
        )
        DisclosureCard(state, onAcceptConsent, onRevokeConsent)
        PermissionCard(state, onOpenAccessibilitySettings)
        OutlinedButton(onClick = onOpenProviderSettings, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.open_provider_settings))
        }
        Text(
            text = stringResource(R.string.milestone_note),
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Composable
private fun DisclosureCard(state: DashboardUiState, onAccept: () -> Unit, onRevoke: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.disclosure_title), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.disclosure_body), style = MaterialTheme.typography.bodyMedium)
            if (state.consentAccepted) {
                Text(stringResource(R.string.consent_given, state.consentVersion ?: 0))
                TextButton(onClick = onRevoke) { Text(stringResource(R.string.consent_revoke)) }
            } else {
                Button(onClick = onAccept, modifier = Modifier.fillMaxWidth().testTag(TAG_CONSENT)) {
                    Text(stringResource(R.string.consent_button))
                }
            }
        }
    }
}

@Composable
private fun PermissionCard(state: DashboardUiState, onOpenSettings: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.accessibility_help), style = MaterialTheme.typography.bodyMedium)
            Button(
                onClick = onOpenSettings,
                enabled = state.canOpenAccessibilitySettings,
                modifier = Modifier.fillMaxWidth().testTag(TAG_OPEN_SETTINGS),
            ) { Text(stringResource(R.string.open_accessibility_settings)) }
        }
    }
}

const val TAG_CONSENT = "dashboard_consent"
const val TAG_OPEN_SETTINGS = "dashboard_open_settings"
