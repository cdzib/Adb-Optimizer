@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.optimizer.tools.feature.connection

import androidx.compose.foundation.clickable
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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.optimizer.tools.R
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
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.devices_remote_heading), style = MaterialTheme.typography.titleSmall)
        devices.forEach { device ->
            key(device.id) {
                val isActive = activeTarget == AppTarget.REMOTE && device.id == activeRemoteId
                Card(
                    colors =
                        CardDefaults.cardColors(
                            containerColor =
                                if (isActive) {
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
                                } else {
                                    MaterialTheme.colorScheme.surface
                                },
                        ),
                    shape = MaterialTheme.shapes.large,
                    modifier =
                        Modifier.fillMaxWidth().clickable {
                            onSelect(device.id)
                        },
                ) {
                    Row(
                        Modifier.fillMaxWidth().padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        ConnectedDeviceIcon(device)
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                device.displayName.ifBlank { device.id },
                                style = MaterialTheme.typography.titleSmall,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                "${device.id} · Android ${device.androidVersion}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(
                                    stringResource(if (isActive) R.string.devices_local_active else R.string.devices_connected),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                        }
                        IconButton(onClick = { selectedDevice = device }) {
                            Icon(
                                Icons.Filled.MoreVert,
                                contentDescription = stringResource(R.string.known_device_actions, device.displayName),
                            )
                        }
                    }
                }
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
