package com.optimizer.tools.feature.connection

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddLink
import androidx.compose.material.icons.filled.Cable
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.optimizer.tools.R
import com.optimizer.tools.app.tv.LocalTelevisionUi
import com.optimizer.tools.app.tv.TvActionRow
import com.optimizer.tools.app.tv.TvFocusCard
import com.optimizer.tools.app.tv.TvScreenHeader
import com.optimizer.tools.app.tv.TvSectionLabel
import com.optimizer.tools.app.tv.TvTile
import com.optimizer.tools.core.ui.AdbOptimizerTheme
import com.optimizer.tools.domain.model.KnownDevice
import com.optimizer.tools.domain.model.ThemeMode
import com.optimizer.tools.domain.model.deduplicateKnownDevices
import dev.adboptimizer.core.model.AppTarget
import dev.adboptimizer.core.model.DeviceInfo

/**
 * Destino en televisor: una cuadricula de tarjetas enfocables sustituye a los chips, el FAB y
 * las hojas inferiores del movil. Cada accion secundaria vive en un dialogo para que el D-pad
 * solo tenga que recorrer un control por equipo.
 */
@Composable
internal fun TelevisionDevicesContent(
    state: ConnectionState,
    activeTarget: AppTarget,
    onAddDevice: () -> Unit,
    onSelectTarget: (AppTarget) -> Unit,
    onSelectDevice: (String) -> Unit,
    onDisconnectDevice: (String) -> Unit,
    onConnectKnown: (KnownDevice) -> Unit,
    onForgetKnown: (KnownDevice) -> Unit,
    onCancelConnect: (() -> Unit)? = null,
    onOpenLocalAdb: (() -> Unit)? = null,
    onOpenRemote: (() -> Unit)? = null,
    onOpenTools: (() -> Unit)? = null,
) {
    var connectedActions by remember { mutableStateOf<DeviceInfo?>(null) }
    var knownActions by remember { mutableStateOf<KnownDevice?>(null) }
    val known = remember(state.known) { deduplicateKnownDevices(state.known) }
    val device = state.connected

    Column(Modifier.fillMaxSize()) {
        TvScreenHeader(
            title = stringResource(R.string.nav_destination),
            subtitle = stringResource(R.string.tv_devices_subtitle),
        )
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(bottom = 12.dp),
            modifier = Modifier.weight(1f),
        ) {
            item(key = "local-header", span = { GridItemSpan(3) }) {
                TvSectionLabel(stringResource(R.string.devices_local))
            }
            item(key = "local") {
                TvTile(
                    icon = Icons.Filled.Smartphone,
                    title = stringResource(R.string.devices_local),
                    subtitle =
                        stringResource(
                            if (activeTarget == AppTarget.LOCAL) {
                                R.string.tv_devices_active
                            } else {
                                R.string.devices_local_available
                            },
                        ),
                    selected = activeTarget == AppTarget.LOCAL,
                    onClick = { onSelectTarget(AppTarget.LOCAL) },
                )
            }
            if (onOpenLocalAdb != null) {
                item(key = "local-adb") {
                    TvTile(
                        icon = Icons.Filled.Cable,
                        title = stringResource(R.string.devices_local_adb),
                        subtitle = stringResource(R.string.tv_devices_local_adb_subtitle),
                        onClick = onOpenLocalAdb,
                    )
                }
            }
            item(key = "add-device") {
                TvTile(
                    icon = Icons.Filled.AddLink,
                    title = stringResource(R.string.devices_add),
                    subtitle = stringResource(R.string.tv_devices_add_subtitle),
                    onClick = onAddDevice,
                )
            }

            if (device != null && (onOpenRemote != null || onOpenTools != null)) {
                item(key = "active-header", span = { GridItemSpan(3) }) {
                    TvSectionLabel(stringResource(R.string.devices_local_active))
                }
                if (onOpenRemote != null) {
                    item(key = "active-remote") {
                        TvTile(
                            icon = Icons.Filled.Devices,
                            title = stringResource(R.string.devices_remote_control),
                            subtitle = device.displayName.ifBlank { device.id },
                            onClick = onOpenRemote,
                        )
                    }
                }
                if (onOpenTools != null) {
                    item(key = "active-tools") {
                        TvTile(
                            icon = Icons.Filled.Tune,
                            title = stringResource(R.string.devices_tools_for_device),
                            subtitle = stringResource(R.string.tools_description),
                            onClick = onOpenTools,
                        )
                    }
                }
            }

            if (state.connecting) {
                item(key = "connecting", span = { GridItemSpan(3) }) {
                    ConnectingCard(
                        target = state.connectTarget.orEmpty(),
                        onCancel = onCancelConnect,
                    )
                }
            }

            if (state.connectedDevices.isNotEmpty()) {
                item(key = "remote-header", span = { GridItemSpan(3) }) {
                    TvSectionLabel(stringResource(R.string.devices_remote_heading))
                }
                items(state.connectedDevices, key = { "remote-${it.id}" }) { connected ->
                    val isActive = activeTarget == AppTarget.REMOTE && connected.id == device?.id
                    TvTile(
                        icon = if (connected.isTv) Icons.Filled.Tv else Icons.Filled.Smartphone,
                        title = connected.displayName.ifBlank { connected.id },
                        subtitle =
                            buildString {
                                append(connected.id)
                                append(" · ")
                                append(
                                    stringResource(
                                        if (isActive) R.string.tv_devices_active else R.string.devices_connected,
                                    ),
                                )
                            },
                        selected = isActive,
                        onClick = { connectedActions = connected },
                    )
                }
            }

            item(key = "known-header", span = { GridItemSpan(3) }) {
                TvSectionLabel(stringResource(R.string.devices_recent))
            }
            if (known.isEmpty()) {
                item(key = "known-empty", span = { GridItemSpan(3) }) {
                    Text(
                        stringResource(R.string.devices_recent_empty),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 4.dp),
                    )
                }
            } else {
                items(known, key = { "known-${it.identityKey ?: it.address}" }) { knownDevice ->
                    TvTile(
                        icon = knownDevice.tvIcon(),
                        title = knownDevice.displayName,
                        subtitle = "${stringResource(knownDevice.typeLabel())} · ${knownDevice.address}",
                        onClick = { knownActions = knownDevice },
                    )
                }
            }
        }
    }

    connectedActions?.let { selected ->
        val isActive = activeTarget == AppTarget.REMOTE && selected.id == device?.id
        DeviceActionsDialog(
            title = selected.displayName.ifBlank { selected.id },
            subtitle = stringResource(R.string.tv_devices_actions_for, selected.id),
            primaryLabel = stringResource(R.string.known_device_use),
            primaryEnabled = !isActive,
            onPrimary = {
                connectedActions = null
                onSelectDevice(selected.id)
            },
            secondaryLabel = stringResource(R.string.tv_devices_disconnect),
            onSecondary = {
                connectedActions = null
                onDisconnectDevice(selected.id)
            },
            onDismiss = { connectedActions = null },
        )
    }

    knownActions?.let { selected ->
        val connected = state.connectedDevices.firstOrNull { it.id == selected.address }
        DeviceActionsDialog(
            title = selected.displayName,
            subtitle = selected.address,
            primaryLabel =
                stringResource(
                    if (connected != null) R.string.known_device_use else R.string.devices_connect,
                ),
            primaryEnabled = !state.connecting || connected != null,
            onPrimary = {
                knownActions = null
                if (connected != null) onSelectDevice(connected.id) else onConnectKnown(selected)
            },
            secondaryLabel = stringResource(R.string.devices_forget),
            onSecondary = {
                knownActions = null
                onForgetKnown(selected)
            },
            onDismiss = { knownActions = null },
        )
    }
}

@Composable
private fun ConnectingCard(
    target: String,
    onCancel: (() -> Unit)?,
) {
    TvFocusCard(onClick = null) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                stringResource(R.string.connection_connecting),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                target,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (onCancel != null) {
                TvActionRow(
                    primaryLabel = stringResource(R.string.connection_cancel_attempt),
                    primaryEnabled = true,
                    onPrimary = onCancel,
                )
            }
        }
    }
}

@Composable
private fun DeviceActionsDialog(
    title: String,
    subtitle: String,
    primaryLabel: String,
    primaryEnabled: Boolean,
    onPrimary: () -> Unit,
    secondaryLabel: String,
    onSecondary: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(subtitle) },
        confirmButton = {
            TextButton(onClick = onPrimary, enabled = primaryEnabled) { Text(primaryLabel) }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onSecondary) { Text(secondaryLabel) }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.tv_devices_close)) }
            }
        },
    )
}

private fun KnownDevice.tvIcon() =
    when (isTv) {
        true -> Icons.Filled.Tv
        false -> Icons.Filled.Smartphone
        null -> Icons.Filled.Devices
    }

private fun KnownDevice.typeLabel(): Int =
    when (isTv) {
        true -> R.string.known_device_tv
        false -> R.string.known_device_phone
        null -> R.string.known_device_unknown
    }

@Preview(widthDp = 960, heightDp = 540, showBackground = true)
@Composable
private fun TelevisionDevicesContentPreview() {
    val device = DeviceInfo("192.0.2.10:5555", "TV de sala", "Android", 34, "14", true, false, "arm64-v8a")
    AdbOptimizerTheme(ThemeMode.DARK) {
        androidx.compose.runtime.CompositionLocalProvider(LocalTelevisionUi provides true) {
            TelevisionDevicesContent(
                state =
                    ConnectionState(
                        connected = device,
                        connectedDevices = listOf(device),
                        known = listOf(KnownDevice("192.0.2.10", 5555, false, "TV de sala", isTv = true)),
                    ),
                activeTarget = AppTarget.REMOTE,
                onAddDevice = {},
                onSelectTarget = {},
                onSelectDevice = {},
                onDisconnectDevice = {},
                onConnectKnown = {},
                onForgetKnown = {},
                onOpenLocalAdb = {},
                onOpenRemote = {},
                onOpenTools = {},
            )
        }
    }
}
