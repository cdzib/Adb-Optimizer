package com.optimizer.tools.feature.connection

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.optimizer.tools.R
import com.optimizer.tools.app.tv.TvActionRow
import com.optimizer.tools.app.tv.TvSectionLabel
import com.optimizer.tools.app.tv.TvTile
import com.optimizer.tools.core.ui.QrImage
import com.optimizer.tools.domain.model.KnownDevice
import dev.adboptimizer.core.adb.DiscoveredService

/** Contenido de cada modo del asistente en TV: red, codigo, QR y manual. */
@Composable
internal fun TvConnectModeContent(
    state: ConnectionState,
    ip: String,
    pairPort: String,
    code: String,
    connectPort: String,
    manualIp: String,
    manualPort: String,
    onIp: (String) -> Unit,
    onPairPort: (String) -> Unit,
    onCode: (String) -> Unit,
    onConnectPort: (String) -> Unit,
    onManualIp: (String) -> Unit,
    onManualPort: (String) -> Unit,
    onDiscover: () -> Unit,
    onConnectDiscovered: (DiscoveredService) -> Unit,
    onConnectKnown: (KnownDevice) -> Unit,
    onPair: () -> Unit,
    onConnectManual: () -> Unit,
    onConnectSecure: () -> Unit,
    onStartQr: () -> Unit,
    onCancelQr: () -> Unit,
    onUseCode: () -> Unit,
) {
    when (state.connectMode) {
        ConnectMode.NETWORK -> {
            NetworkModePanel(state, onDiscover, onConnectDiscovered, onConnectKnown)
        }

        ConnectMode.CODE -> {
            CodeModePanel(
                state = state,
                ip = ip,
                pairPort = pairPort,
                code = code,
                connectPort = connectPort,
                onIp = onIp,
                onPairPort = onPairPort,
                onCode = onCode,
                onConnectPort = onConnectPort,
                onPair = onPair,
            )
        }

        ConnectMode.QR -> {
            QrModePanel(state, onStartQr, onCancelQr, onUseCode)
        }

        ConnectMode.MANUAL -> {
            ManualModePanel(
                state = state,
                manualIp = manualIp,
                manualPort = manualPort,
                onManualIp = onManualIp,
                onManualPort = onManualPort,
                onConnectManual = onConnectManual,
                onConnectSecure = onConnectSecure,
            )
        }
    }
}

@Composable
private fun NetworkModePanel(
    state: ConnectionState,
    onDiscover: () -> Unit,
    onConnectDiscovered: (DiscoveredService) -> Unit,
    onConnectKnown: (KnownDevice) -> Unit,
) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            stringResource(R.string.connection_network_title),
            style = MaterialTheme.typography.titleMedium,
        )
        Text(
            stringResource(R.string.connection_network_body),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        TvActionRow(
            primaryLabel =
                stringResource(
                    if (state.discoveryRunning) {
                        R.string.connection_searching
                    } else {
                        R.string.connection_search_devices
                    },
                ),
            primaryEnabled = !state.discoveryRunning,
            onPrimary = onDiscover,
        )
        if (state.discovered.isEmpty()) {
            Text(
                stringResource(
                    if (state.discoveryRunning) {
                        R.string.connection_listening
                    } else {
                        R.string.connection_no_results
                    },
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            state.discovered.forEach { service ->
                TvTile(
                    icon = Icons.Filled.Devices,
                    title = service.host,
                    subtitle = stringResource(R.string.connection_tls_port, service.port),
                    enabled = !state.connecting,
                    onClick = { onConnectDiscovered(service) },
                )
            }
        }
        if (state.known.isNotEmpty()) {
            TvSectionLabel(stringResource(R.string.connection_recent_short))
            state.known.forEach { device ->
                TvTile(
                    icon = Icons.Filled.Devices,
                    title = device.model.ifBlank { device.host },
                    subtitle =
                        device.address + " · " +
                            stringResource(
                                if (device.secure) R.string.known_device_tls else R.string.connection_transport_classic,
                            ),
                    enabled = !state.connecting,
                    onClick = { onConnectKnown(device) },
                )
            }
        }
        Text(
            stringResource(R.string.connection_network_hint),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun CodeModePanel(
    state: ConnectionState,
    ip: String,
    pairPort: String,
    code: String,
    connectPort: String,
    onIp: (String) -> Unit,
    onPairPort: (String) -> Unit,
    onCode: (String) -> Unit,
    onConnectPort: (String) -> Unit,
    onPair: () -> Unit,
) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            stringResource(R.string.connection_code_title),
            style = MaterialTheme.typography.titleMedium,
        )
        Text(
            stringResource(R.string.connection_code_inline),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        TvTextField(
            value = ip,
            onValueChange = onIp,
            label = stringResource(R.string.connection_ip_label),
            placeholder = stringResource(R.string.connection_ip_placeholder),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            TvTextField(
                value = pairPort,
                onValueChange = onPairPort,
                label = stringResource(R.string.connection_pair_port),
                placeholder = stringResource(R.string.connection_pair_port_placeholder),
                modifier = Modifier.weight(1f),
            )
            TvTextField(
                value = connectPort,
                onValueChange = onConnectPort,
                label = stringResource(R.string.connection_connect_port),
                placeholder = stringResource(R.string.connection_connect_port_placeholder),
                modifier = Modifier.weight(1f),
            )
        }
        PairingCodeField(
            value = code,
            onValueChange = onCode,
            enabled = !state.pairing && !state.connecting,
        )
        TvActionRow(
            primaryLabel =
                stringResource(
                    if (state.pairing) R.string.connection_pairing else R.string.connection_pair_action,
                ),
            primaryEnabled = !state.pairing && !state.connecting,
            onPrimary = onPair,
        )
    }
}

@Composable
private fun QrModePanel(
    state: ConnectionState,
    onStartQr: () -> Unit,
    onCancelQr: () -> Unit,
    onUseCode: () -> Unit,
) {
    val failed = state.connectTimedOut || state.qrStatus == QrStatus.ERROR || state.qrStatus == QrStatus.TIMEOUT
    Column(
        Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            stringResource(R.string.connection_qr_title),
            style = MaterialTheme.typography.titleMedium,
        )
        Text(
            stringResource(R.string.connection_qr_description),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (!failed && state.qrText != null) {
            QrImage(
                state.qrText,
                Modifier.widthIn(max = 220.dp).aspectRatio(1f).padding(vertical = 4.dp),
            )
            Text(qrStatusText(state.qrStatus), style = MaterialTheme.typography.bodyMedium)
            Text(
                stringResource(R.string.connection_qr_keep_open),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            Text(
                stringResource(
                    if (state.qrStatus == QrStatus.TIMEOUT) {
                        R.string.connection_qr_expired
                    } else {
                        R.string.connection_qr_unavailable
                    },
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            TvActionRow(
                primaryLabel = stringResource(R.string.connection_qr_retry),
                primaryEnabled = state.wifiEnabled != false && !state.connecting,
                onPrimary = onStartQr,
            )
        }
        TvActionRow(
            primaryLabel = stringResource(R.string.connection_code_alternative),
            primaryEnabled = !state.connecting,
            onPrimary = onUseCode,
            secondaryLabel = stringResource(R.string.connection_qr_cancel),
            onSecondary = onCancelQr,
        )
    }
}

@Composable
private fun ManualModePanel(
    state: ConnectionState,
    manualIp: String,
    manualPort: String,
    onManualIp: (String) -> Unit,
    onManualPort: (String) -> Unit,
    onConnectManual: () -> Unit,
    onConnectSecure: () -> Unit,
) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            stringResource(R.string.connection_manual_title),
            style = MaterialTheme.typography.titleMedium,
        )
        Text(
            stringResource(R.string.connection_manual_body),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            TvTextField(
                value = manualIp,
                onValueChange = onManualIp,
                label = stringResource(R.string.connection_ip_short),
                placeholder = stringResource(R.string.connection_ip_placeholder),
                modifier = Modifier.weight(1f),
            )
            TvTextField(
                value = manualPort,
                onValueChange = onManualPort,
                label = stringResource(R.string.connection_port_label),
                placeholder = MANUAL_PORT_PLACEHOLDER,
                modifier = Modifier.weight(0.6f),
            )
        }
        TvActionRow(
            primaryLabel = stringResource(R.string.connection_classic),
            primaryEnabled = !state.connecting,
            onPrimary = onConnectManual,
            secondaryLabel = stringResource(R.string.connection_tls),
            onSecondary = onConnectSecure,
        )
    }
}

@Composable
private fun TvTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    placeholder: String,
    modifier: Modifier = Modifier.fillMaxWidth(),
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        placeholder = { Text(placeholder) },
        singleLine = true,
        modifier = modifier,
    )
}

private const val MANUAL_PORT_PLACEHOLDER = "5555"
