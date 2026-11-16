package com.example.contextbubble.ui.main

import android.content.ActivityNotFoundException
import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.contextbubble.R
import com.example.contextbubble.presentation.DashboardUiState
import com.example.contextbubble.presentation.DashboardViewModel
import com.example.contextbubble.presentation.ServiceStatus

@Composable
fun DashboardScreen(viewModel: DashboardViewModel, onOpenProviderSettings: () -> Unit) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    LifecycleResumeEffect(Unit) {
        viewModel.recheckStatus()
        onPauseOrDispose { }
    }
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
        onRecheck = viewModel::recheckStatus,
        onBubbleToggle = viewModel::setBubbleEnabled,
        onOpenProviderSettings = onOpenProviderSettings,
    )
}

@Composable
fun DashboardContent(
    state: DashboardUiState,
    onAcceptConsent: () -> Unit,
    onRevokeConsent: () -> Unit,
    onOpenAccessibilitySettings: () -> Unit,
    onRecheck: () -> Unit,
    onBubbleToggle: (Boolean) -> Unit,
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
        StatusCard(state)
        DisclosureCard(state, onAcceptConsent, onRevokeConsent)
        PermissionCard(state, onOpenAccessibilitySettings, onRecheck)
        BubbleToggleRow(state, onBubbleToggle)
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
private fun StatusCard(state: DashboardUiState) {
    val statusText = when {
        !state.consentAccepted -> stringResource(R.string.status_consent_required)
        else -> stringResource(statusLabel(state.status))
    }
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(stringResource(R.string.status_label), style = MaterialTheme.typography.labelLarge)
            Text(statusText, style = MaterialTheme.typography.titleMedium, modifier = Modifier.testTag(TAG_STATUS))
        }
    }
}

private fun statusLabel(status: ServiceStatus): Int = when (status) {
    ServiceStatus.NO_PERMISSION -> R.string.status_no_permission
    ServiceStatus.ENABLED_NOT_CONNECTED -> R.string.status_enabled_not_connected
    ServiceStatus.CONNECTED -> R.string.status_connected
    ServiceStatus.BUBBLE_OFF -> R.string.status_bubble_off
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
private fun PermissionCard(state: DashboardUiState, onOpenSettings: () -> Unit, onRecheck: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.accessibility_help), style = MaterialTheme.typography.bodyMedium)
            Button(
                onClick = onOpenSettings,
                enabled = state.canOpenAccessibilitySettings,
                modifier = Modifier.fillMaxWidth().testTag(TAG_OPEN_SETTINGS),
            ) { Text(stringResource(R.string.open_accessibility_settings)) }
            OutlinedButton(onClick = onRecheck, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.recheck_status))
            }
        }
    }
}

@Composable
private fun BubbleToggleRow(state: DashboardUiState, onToggle: (Boolean) -> Unit) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.bubble_toggle), modifier = Modifier.weight(1f))
            Switch(
                checked = state.bubbleEnabled && state.canToggleBubble,
                onCheckedChange = onToggle,
                enabled = state.canToggleBubble,
                modifier = Modifier.testTag(TAG_BUBBLE_SWITCH),
            )
        }
        if (!state.canToggleBubble) {
            Text(stringResource(R.string.bubble_toggle_disabled_hint), style = MaterialTheme.typography.bodySmall)
        }
    }
}

const val TAG_STATUS = "dashboard_status"
const val TAG_CONSENT = "dashboard_consent"
const val TAG_OPEN_SETTINGS = "dashboard_open_settings"
const val TAG_BUBBLE_SWITCH = "dashboard_bubble_switch"
