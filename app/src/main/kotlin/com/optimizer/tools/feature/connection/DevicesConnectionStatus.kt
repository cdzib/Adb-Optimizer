package com.optimizer.tools.feature.connection

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.optimizer.tools.R
import com.optimizer.tools.core.ui.AdbOptimizerTheme
import com.optimizer.tools.domain.model.ThemeMode

@Composable
internal fun DevicesConnectionStatus(
    target: String?,
    reconnecting: Boolean,
    onCancel: (() -> Unit)?,
) {
    Card(
        modifier = Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)),
        shape = MaterialTheme.shapes.large,
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        stringResource(if (reconnecting) R.string.devices_reconnecting else R.string.devices_connecting),
                        style = MaterialTheme.typography.titleSmall,
                    )
                    target?.takeIf { it.isNotBlank() }?.let {
                        Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            Text(
                stringResource(R.string.devices_connection_progress),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (onCancel != null) {
                TextButton(onClick = onCancel, modifier = Modifier.align(Alignment.End)) {
                    Text(stringResource(R.string.devices_connection_cancel))
                }
            }
        }
    }
}

@Preview(name = "Reconectando oscuro", showBackground = true, widthDp = 360)
@Composable
private fun DevicesReconnectingPreview() {
    AdbOptimizerTheme(ThemeMode.DARK) {
        Surface { Column(Modifier.padding(24.dp)) { DevicesConnectionStatus("Samsung Galaxy A35", true, {}) } }
    }
}

@Preview(name = "Conectando texto grande", showBackground = true, widthDp = 320, fontScale = 1.3f)
@Composable
private fun DevicesConnectingPreview() {
    AdbOptimizerTheme(ThemeMode.LIGHT) {
        Surface { Column(Modifier.padding(24.dp)) { DevicesConnectionStatus("192.0.2.10:41000", false, {}) } }
    }
}
