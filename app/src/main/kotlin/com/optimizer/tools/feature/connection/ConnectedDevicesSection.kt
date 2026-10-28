@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.optimizer.tools.feature.connection

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.optimizer.tools.R
import com.optimizer.tools.core.ui.DesignGroup
import com.optimizer.tools.core.ui.DesignRow
import com.optimizer.tools.core.ui.DesignTone
import dev.adboptimizer.core.model.AppTarget
import dev.adboptimizer.core.model.DeviceInfo

@Composable
internal fun ConnectedDevicesSection(
    devices: List<DeviceInfo>,
    activeTarget: AppTarget,
    activeRemoteId: String?,
    onSelect: (String) -> Unit,
    onDisconnect: (String) -> Unit,
) {
    var selectedDevice by remember { mutableStateOf<DeviceInfo?>(null) }
    DesignGroup {
        devices.forEachIndexed { index, device ->
            key(device.id) {
                val isActive = activeTarget == AppTarget.REMOTE && device.id == activeRemoteId
                DesignRow(
                    icon = if (device.isTv) Icons.Filled.Tv else Icons.Filled.Smartphone,
                    title = device.displayName.ifBlank { device.id },
                    subtitle =
                        "${device.id} · Android ${device.androidVersion} · " +
                            stringResource(if (isActive) R.string.devices_local_active else R.string.devices_connected),
                    tone = if (isActive) DesignTone.Green else DesignTone.Cyan,
                    isFirst = index == 0,
                    trailing = {
                        IconButton(onClick = { selectedDevice = device }) {
                            Icon(
                                Icons.Filled.MoreVert,
                                contentDescription = stringResource(R.string.known_device_actions, device.displayName),
                            )
                        }
                    },
                    onClick = { onSelect(device.id) },
                )
            }
        }
    }
    selectedDevice?.let { device ->
        ModalBottomSheet(
            onDismissRequest = { selectedDevice = null },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        ) {
            ConnectedDeviceDetailsContent(
                device = device,
                isActive = activeTarget == AppTarget.REMOTE && device.id == activeRemoteId,
                onSelect = {
                    selectedDevice = null
                    onSelect(device.id)
                },
                onDisconnect = {
                    selectedDevice = null
                    onDisconnect(device.id)
                },
            )
        }
    }
}

@Composable
private fun ConnectedDeviceIcon(device: DeviceInfo) {
    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)) {
        Icon(
            when (device.isTv) {
                true -> Icons.Filled.Tv
                false -> Icons.Filled.Smartphone
            },
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(12.dp).size(24.dp),
        )
    }
}

@Composable
internal fun ConnectedDeviceDetailsContent(
    device: DeviceInfo,
    isActive: Boolean,
    onSelect: () -> Unit,
    onDisconnect: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .navigationBarsPadding()
            .padding(horizontal = 24.dp)
            .padding(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            ConnectedDeviceIcon(device)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(device.displayName.ifBlank { device.id }, style = MaterialTheme.typography.titleLarge)
                Text(
                    stringResource(if (isActive) R.string.devices_local_active else R.string.devices_connected),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
        Text(
            stringResource(R.string.known_device_details),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            ConnectedDeviceDetail(
                stringResource(R.string.known_device_type),
                stringResource(if (device.isTv) R.string.known_device_tv else R.string.known_device_phone),
            )
            if (device.model.isNotBlank()) ConnectedDeviceDetail(stringResource(R.string.known_device_model), device.model)
            if (device.manufacturer.isNotBlank()) {
                ConnectedDeviceDetail(
                    stringResource(R.string.known_device_manufacturer),
                    device.manufacturer,
                )
            }
            ConnectedDeviceDetail(stringResource(R.string.known_device_address), device.id)
            ConnectedDeviceDetail(stringResource(R.string.known_device_android), device.androidVersion)
            ConnectedDeviceDetail(stringResource(R.string.known_device_sdk), device.sdk.toString())
            ConnectedDeviceDetail(stringResource(R.string.known_device_abi), device.abi)
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (!isActive) {
                androidx.compose.material3.Button(
                    onClick = onSelect,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                    shape = MaterialTheme.shapes.large,
                ) {
                    Text(stringResource(R.string.known_device_use))
                }
            }
            OutlinedButton(
                onClick = onDisconnect,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
            ) {
                Text(stringResource(R.string.known_device_disconnect))
            }
        }
    }
}

@Composable
private fun ConnectedDeviceDetail(
    label: String,
    value: String,
) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}
