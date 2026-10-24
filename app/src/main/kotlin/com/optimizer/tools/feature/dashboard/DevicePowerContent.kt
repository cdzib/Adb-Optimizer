package com.optimizer.tools.feature.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.optimizer.tools.R
import com.optimizer.tools.core.ui.AdbOptimizerTheme
import com.optimizer.tools.core.ui.resolve
import com.optimizer.tools.domain.model.DevicePowerAction
import com.optimizer.tools.domain.model.ThemeMode
import dev.adboptimizer.core.model.AppTarget

internal fun DevicePowerAction.labelResource(): Int =
    when (this) {
        DevicePowerAction.REBOOT -> R.string.power_reboot
        DevicePowerAction.SHUTDOWN -> R.string.power_shutdown
        DevicePowerAction.RECOVERY -> R.string.power_recovery
    }

@Composable
internal fun DevicePowerContent(
    state: DashboardState,
    enabled: Boolean,
    onRequest: (DevicePowerAction) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = MaterialTheme.shapes.large,
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.power_title), style = MaterialTheme.typography.titleMedium)
            state.device?.let {
                Text(stringResource(R.string.power_device, "${it.displayName} · ${it.id}"), style = MaterialTheme.typography.bodySmall)
            }
            if (!enabled && state.target == AppTarget.LOCAL) {
                Text(stringResource(R.string.power_shizuku_required), style = MaterialTheme.typography.bodySmall)
            }
            DevicePowerAction.entries.forEach { action ->
                OutlinedButton(
                    onClick = { onRequest(action) },
                    enabled = enabled && !state.executingPowerAction,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                ) { Text(stringResource(action.labelResource())) }
            }
            if (state.executingPowerAction) Text(stringResource(R.string.power_sending))
            state.powerMessage?.let { Text(it.resolve(), style = MaterialTheme.typography.bodyMedium) }
        }
    }
}

@Composable
internal fun DevicePowerConfirmation(
    request: PowerActionRequest,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(request.action.labelResource())) },
        text = { Text(stringResource(R.string.power_confirm_body, request.deviceName, request.deviceId)) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(stringResource(request.action.labelResource())) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.power_cancel)) } },
    )
}

@Preview(showBackground = true, widthDp = 360, fontScale = 1.3f)
@Composable
private fun DevicePowerUnavailablePreview() {
    AdbOptimizerTheme(ThemeMode.LIGHT) { DevicePowerContent(DashboardState(), false, {}) }
}

@Preview(showBackground = true, widthDp = 400)
@Composable
private fun DevicePowerConfirmationPreview() {
    AdbOptimizerTheme(ThemeMode.DARK) {
        DevicePowerConfirmation(PowerActionRequest(DevicePowerAction.RECOVERY, AppTarget.REMOTE, "192.0.2.1:5555", "TV de ejemplo"), {}, {})
    }
}
