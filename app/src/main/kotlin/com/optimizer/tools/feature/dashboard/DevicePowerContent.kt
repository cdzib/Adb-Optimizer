package com.optimizer.tools.feature.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeveloperBoard
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.SettingsBackupRestore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.optimizer.tools.R
import com.optimizer.tools.core.ui.AdbOptimizerTheme
import com.optimizer.tools.core.ui.DesignGroup
import com.optimizer.tools.core.ui.DesignRow
import com.optimizer.tools.core.ui.DesignTone
import com.optimizer.tools.core.ui.resolve
import com.optimizer.tools.domain.model.DevicePowerAction
import com.optimizer.tools.domain.model.ThemeMode
import dev.adboptimizer.core.model.AppTarget

internal fun DevicePowerAction.labelResource(): Int =
    when (this) {
        DevicePowerAction.REBOOT -> R.string.power_reboot
        DevicePowerAction.SHUTDOWN -> R.string.power_shutdown
        DevicePowerAction.RECOVERY -> R.string.power_recovery
        DevicePowerAction.BOOTLOADER -> R.string.power_bootloader
        DevicePowerAction.RESTART_SYSTEM_UI -> R.string.power_restart_system_ui
    }

/** Advertencia propia de las acciones que no son un reinicio normal, o `null`. */
internal fun DevicePowerAction.warningResource(): Int? =
    when (this) {
        DevicePowerAction.BOOTLOADER -> R.string.power_confirm_bootloader
        DevicePowerAction.RESTART_SYSTEM_UI -> R.string.power_confirm_system_ui
        else -> null
    }

@Composable
internal fun DevicePowerContent(
    state: DashboardState,
    enabled: Boolean,
    onRequest: (DevicePowerAction) -> Unit,
    showTitle: Boolean = true,
    showDevice: Boolean = true,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (showTitle) {
            Text(stringResource(R.string.power_title), style = MaterialTheme.typography.titleSmall)
        }
        if (showDevice) {
            state.device?.let {
                Text(
                    stringResource(R.string.power_device, "${it.displayName} · ${it.id}"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (!enabled && state.target == AppTarget.LOCAL) {
            Text(
                stringResource(R.string.power_shizuku_required),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.tertiary,
            )
        }
        DesignGroup {
            DevicePowerAction.entries.forEachIndexed { index, action ->
                DesignRow(
                    icon = action.icon(),
                    title = stringResource(action.labelResource()),
                    subtitle = stringResource(action.descriptionResource()),
                    tone = action.tone(),
                    isFirst = index == 0,
                    showChevron = false,
                    enabled = enabled && !state.executingPowerAction,
                    onClick = { onRequest(action) },
                )
            }
        }
        if (state.executingPowerAction) {
            Text(stringResource(R.string.power_sending), style = MaterialTheme.typography.bodySmall)
        }
        state.powerMessage?.let { Text(it.resolve(), style = MaterialTheme.typography.bodyMedium) }
    }
}

private fun DevicePowerAction.icon(): ImageVector =
    when (this) {
        DevicePowerAction.REBOOT -> Icons.Filled.RestartAlt
        DevicePowerAction.SHUTDOWN -> Icons.Filled.PowerSettingsNew
        DevicePowerAction.RECOVERY -> Icons.Filled.SettingsBackupRestore
        DevicePowerAction.BOOTLOADER -> Icons.Filled.DeveloperBoard
        DevicePowerAction.RESTART_SYSTEM_UI -> Icons.Filled.Refresh
    }

private fun DevicePowerAction.tone(): DesignTone =
    when (this) {
        DevicePowerAction.REBOOT -> DesignTone.Amber
        DevicePowerAction.SHUTDOWN -> DesignTone.Red
        DevicePowerAction.RECOVERY -> DesignTone.Amber
        DevicePowerAction.BOOTLOADER -> DesignTone.Red
        DevicePowerAction.RESTART_SYSTEM_UI -> DesignTone.Neutral
    }

private fun DevicePowerAction.descriptionResource(): Int =
    when (this) {
        DevicePowerAction.REBOOT -> R.string.power_reboot_hint
        DevicePowerAction.SHUTDOWN -> R.string.power_shutdown_hint
        DevicePowerAction.RECOVERY -> R.string.power_recovery_hint
        DevicePowerAction.BOOTLOADER -> R.string.power_bootloader_hint
        DevicePowerAction.RESTART_SYSTEM_UI -> R.string.power_restart_system_ui_hint
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
        text = {
            val warning = request.action.warningResource()
            Text(
                if (warning == null) {
                    stringResource(R.string.power_confirm_body, request.deviceName, request.deviceId)
                } else {
                    stringResource(R.string.power_confirm_body_with_warning, request.deviceName, request.deviceId, stringResource(warning))
                },
            )
        },
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
