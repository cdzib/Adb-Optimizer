@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.optimizer.tools.feature.connection

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.optimizer.tools.R
import com.optimizer.tools.core.ui.AdbOptimizerTheme
import com.optimizer.tools.core.ui.DesignGroup
import com.optimizer.tools.core.ui.DesignRow
import com.optimizer.tools.core.ui.DesignTone
import com.optimizer.tools.domain.model.KnownDevice
import com.optimizer.tools.domain.model.ThemeMode
import com.optimizer.tools.domain.model.deduplicateKnownDevices
import dev.adboptimizer.core.model.DeviceInfo

@Composable
internal fun KnownDevicesSection(
    devices: List<KnownDevice>,
    connectedDevices: List<DeviceInfo>,
    connecting: Boolean,
    onConnect: (KnownDevice) -> Unit,
    onForget: (KnownDevice) -> Unit,
    onSelect: (String) -> Unit,
    onDisconnect: (String) -> Unit,
    connectTarget: String? = null,
) {
    val uniqueDevices = remember(devices) { deduplicateKnownDevices(devices) }
    var selectedDevice by remember { mutableStateOf<KnownDevice?>(null) }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (uniqueDevices.isEmpty()) {
            Text(
                stringResource(R.string.devices_recent_empty),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
        }
        if (uniqueDevices.isNotEmpty()) {
            DesignGroup {
                uniqueDevices.forEachIndexed { index, device ->
                    key(device.identityKey ?: device.address) {
                        DesignRow(
                            icon =
                                when (device.isTv) {
                                    true -> Icons.Filled.Tv
                                    false -> Icons.Filled.Smartphone
                                    null -> Icons.Filled.Devices
                                },
                            title = device.displayName,
                            subtitle =
                                buildString {
                                    append(stringResource(device.typeLabel()))
                                    append(" · ").append(device.address)
                                    if (connecting && connectTarget?.removeSuffix(" (TLS)") == device.address) {
                                        append(" · ").append(stringResource(R.string.devices_reconnecting))
                                    } else if (connectedDevices.any { it.id == device.address }) {
                                        append(" · ").append(stringResource(R.string.devices_connected))
                                    }
                                },
                            tone =
                                when {
                                    connectedDevices.any { it.id == device.address } -> DesignTone.Green
                                    device.isTv == true -> DesignTone.Amber
                                    else -> DesignTone.Cyan
                                },
                            isFirst = index == 0,
                            trailing = {
                                IconButton(onClick = { selectedDevice = device }) {
                                    Icon(
                                        Icons.Filled.MoreVert,
                                        contentDescription = stringResource(R.string.known_device_actions, device.displayName),
                                    )
                                }
                            },
                            onClick = { selectedDevice = device },
                        )
                    }
                }
            }
        }
    }
    selectedDevice?.let { selected ->
        val current = uniqueDevices.firstOrNull { it.isSameDevice(selected) } ?: selected
        val connected = connectedDevices.firstOrNull { it.id == current.address }
        ModalBottomSheet(
            onDismissRequest = { selectedDevice = null },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        ) {
            KnownDeviceDetailsContent(
                device = current,
                connected = connected != null,
                connecting = connecting,
                onConnect = {
                    selectedDevice = null
                    if (connected != null) onSelect(connected.id) else onConnect(current)
                },
                onDisconnect = {
                    selectedDevice = null
                    connected?.let { onDisconnect(it.id) }
                },
                onForget = {
                    selectedDevice = null
                    onForget(current)
                },
            )
        }
    }
}

@Composable
private fun KnownDeviceIcon(device: KnownDevice) {
    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)) {
        Icon(
            when (device.isTv) {
                true -> Icons.Filled.Tv
                false -> Icons.Filled.Smartphone
                null -> Icons.Filled.Devices
            },
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(12.dp).size(24.dp),
        )
    }
}

private fun KnownDevice.typeLabel(): Int =
    when (isTv) {
        true -> R.string.known_device_tv
        false -> R.string.known_device_phone
        null -> R.string.known_device_unknown
    }

@Composable
internal fun KnownDeviceDetailsContent(
    device: KnownDevice,
    connected: Boolean,
    connecting: Boolean,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
    onForget: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .verticalScroll(
                rememberScrollState(),
            ).navigationBarsPadding()
            .padding(horizontal = 24.dp)
            .padding(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            KnownDeviceIcon(device)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(device.displayName, style = MaterialTheme.typography.titleLarge)
                Text(
                    stringResource(if (connected) R.string.devices_connected else R.string.known_device_saved),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Text(
            stringResource(R.string.known_device_details),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            KnownDeviceDetail(stringResource(R.string.known_device_type), stringResource(device.typeLabel()))
            if (device.model.isNotBlank()) KnownDeviceDetail(stringResource(R.string.known_device_model), device.model)
            device.manufacturer?.takeIf { it.isNotBlank() }?.let {
                KnownDeviceDetail(
                    stringResource(R.string.known_device_manufacturer),
                    it,
                )
            }
            KnownDeviceDetail(stringResource(R.string.known_device_address), device.host)
            KnownDeviceDetail(stringResource(R.string.known_device_port), device.port.toString())
            KnownDeviceDetail(
                stringResource(R.string.known_device_transport),
                stringResource(if (device.secure) R.string.known_device_tls else R.string.known_device_classic),
            )
            device.androidVersion?.let { KnownDeviceDetail(stringResource(R.string.known_device_android), it) }
            device.sdk?.let { KnownDeviceDetail(stringResource(R.string.known_device_sdk), it.toString()) }
            device.abi?.let { KnownDeviceDetail(stringResource(R.string.known_device_abi), it) }
        }
        if (device.isTv == null) {
            Text(
                stringResource(R.string.known_device_missing_details),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = onConnect,
                enabled = connected || !connecting,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                shape = MaterialTheme.shapes.large,
            ) {
                Text(stringResource(if (connected) R.string.known_device_use else R.string.devices_connect))
            }
            if (connected) {
                OutlinedButton(onClick = onDisconnect, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                    Text(stringResource(R.string.known_device_disconnect))
                }
            }
            TextButton(
                onClick = onForget,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
            ) {
                Text(stringResource(R.string.devices_forget))
            }
            Text(
                stringResource(R.string.known_device_forget_info),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun KnownDeviceDetail(
    label: String,
    value: String,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

@Preview(name = "Equipo vinculado", showBackground = true, widthDp = 400)
@Composable
private fun KnownDevicesSectionPreview() {
    AdbOptimizerTheme(ThemeMode.DARK) {
        Surface {
            Column(Modifier.padding(24.dp)) {
                KnownDevicesSection(
                    listOf(KnownDevice("192.0.2.10", 41000, true, "Samsung Galaxy A35", isTv = false)),
                    emptyList(),
                    false,
                    {},
                    {},
                    {},
                    {},
                )
            }
        }
    }
}

@Preview(name = "Detalles TV", showBackground = true, widthDp = 360, fontScale = 1.3f)
@Composable
private fun KnownDeviceDetailsPreview() {
    AdbOptimizerTheme(ThemeMode.LIGHT) {
        Surface {
            KnownDeviceDetailsContent(
                KnownDevice(
                    "192.0.2.20",
                    5555,
                    false,
                    "TV de sala",
                    isTv = true,
                    androidVersion = "14",
                    sdk = 34,
                    abi = "arm64-v8a",
                ),
                true,
                false,
                {
                },
                {},
                {},
            )
        }
    }
}
