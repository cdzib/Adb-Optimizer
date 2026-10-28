package com.optimizer.tools.feature.connection

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.optimizer.tools.R
import com.optimizer.tools.core.ui.AdbOptimizerTheme
import com.optimizer.tools.domain.model.ThemeMode
import dev.adboptimizer.core.model.AppTarget
import dev.adboptimizer.core.model.DeviceInfo

@Composable
internal fun ConnectionSelectionContent(
    onMode: (ConnectMode) -> Unit,
    onExisting: (ConnectMode) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                stringResource(R.string.connection_select_title),
                style =
                    MaterialTheme.typography.headlineSmall.copy(
                        fontSize = 22.sp,
                        lineHeight = 27.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = (-0.2).sp,
                    ),
            )
            Text(
                stringResource(R.string.connection_select_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            ConnectionDeviceOption(Icons.Filled.Tv, stringResource(R.string.connection_tv), stringResource(R.string.connection_tv_detail)) {
                onMode(ConnectMode.CODE)
            }
            ConnectionDeviceOption(
                Icons.Filled.Smartphone,
                stringResource(R.string.connection_phone),
                stringResource(R.string.connection_phone_detail),
            ) {
                onMode(ConnectMode.QR)
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Column {
            Text(stringResource(R.string.connection_already_paired), style = MaterialTheme.typography.titleSmall)
            TextButton(onClick = { onExisting(ConnectMode.NETWORK) }) { Text(stringResource(R.string.connection_search)) }
            TextButton(onClick = { onExisting(ConnectMode.MANUAL) }) { Text(stringResource(R.string.connection_manual)) }
        }
    }
}

@Composable
private fun ConnectionDeviceOption(
    icon: ImageVector,
    title: String,
    detail: String,
    onSelect: () -> Unit,
) {
    Card(
        onClick = onSelect,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        shape = MaterialTheme.shapes.large,
    ) {
        Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(16.dp).size(24.dp),
                )
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        }
    }
}

@Preview(name = "Asistente oscuro", showBackground = true, widthDp = 400)
@Composable
private fun ConnectionSelectionDarkPreview() {
    AdbOptimizerTheme(ThemeMode.DARK) {
        Surface {
            Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
                ConnectionSelectionContent({}, {})
            }
        }
    }
}

@Preview(name = "Selección clara", showBackground = true, widthDp = 360, fontScale = 1.3f)
@Composable
private fun ConnectionSelectionLightPreview() {
    AdbOptimizerTheme(ThemeMode.LIGHT) {
        Surface {
            Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
                ConnectionSelectionContent({}, {})
            }
        }
    }
}

@Preview(name = "QR esperando", showBackground = true, widthDp = 400, heightDp = 900)
@Composable
private fun ConnectionQrWaitingPreview() {
    AdbOptimizerTheme(ThemeMode.DARK) {
        Surface {
            Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
                QrPanel(ConnectionState(qrText = "DEMO-NO-ES-UNA-VINCULACION", qrStatus = QrStatus.WAITING), {}, {}, {})
            }
        }
    }
}

@Preview(name = "QR vencido", showBackground = true, widthDp = 360, fontScale = 1.3f)
@Composable
private fun ConnectionQrExpiredPreview() {
    AdbOptimizerTheme(ThemeMode.LIGHT) {
        Surface {
            Column(Modifier.padding(24.dp)) {
                QrPanel(ConnectionState(qrStatus = QrStatus.TIMEOUT), {}, {}, {})
            }
        }
    }
}

@Preview(name = "Dispositivos vacío", showBackground = true, widthDp = 400, heightDp = 800)
@Composable
private fun DevicesEmptyPreview() {
    AdbOptimizerTheme(ThemeMode.DARK) {
        Surface { DevicesScreen(ConnectionState(), AppTarget.LOCAL, {}, {}, {}, {}, {}, {}) }
    }
}

@Preview(name = "Remoto conectado", showBackground = true, widthDp = 360, heightDp = 800)
@Composable
private fun DevicesConnectedPreview() {
    val device = DeviceInfo("192.0.2.10:5555", "TV de sala", "Android", 34, "14", true, false, "arm64-v8a")
    AdbOptimizerTheme(ThemeMode.LIGHT) {
        Surface {
            DevicesScreen(
                ConnectionState(connected = device, connectedDevices = listOf(device)),
                AppTarget.REMOTE,
                {},
                {},
                {},
                {},
                {},
                {},
            )
        }
    }
}

@Preview(name = "Dispositivos reconectando", showBackground = true, widthDp = 400, heightDp = 900)
@Composable
private fun DevicesReconnectingScreenPreview() {
    val device =
        com.optimizer.tools.domain.model
            .KnownDevice("192.0.2.10", 41000, true, "Samsung Galaxy A35", isTv = false)
    AdbOptimizerTheme(ThemeMode.DARK) {
        Surface {
            DevicesScreen(ConnectionState(connecting = true, connectTarget = device.address, known = listOf(device)), AppTarget.LOCAL, {
            }, {}, {}, {}, {}, {}, {})
        }
    }
}
