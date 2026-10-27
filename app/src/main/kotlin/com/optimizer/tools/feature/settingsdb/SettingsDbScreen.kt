package com.optimizer.tools.feature.settingsdb

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.optimizer.tools.R
import com.optimizer.tools.core.ui.UiText
import com.optimizer.tools.core.ui.resolve
import com.optimizer.tools.data.SettingEntry

private val namespaces = listOf("global", "secure", "system")

@Composable
internal fun SettingsDbScreen(
    state: SettingsDbState,
    onBack: () -> Unit,
    onNamespace: (String) -> Unit,
    onQuery: (String) -> Unit,
    onSave: (String, String) -> Unit,
) {
    var editing by remember { mutableStateOf<SettingEntry?>(null) }
    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.settings_db_back))
            }
            Text(stringResource(R.string.settings_db_title), style = MaterialTheme.typography.titleLarge)
        }
        HorizontalDivider()
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            namespaces.forEach { namespace ->
                FilterChip(
                    selected = state.namespace == namespace,
                    onClick = { onNamespace(namespace) },
                    label = { Text(namespaceLabel(namespace)) },
                )
            }
        }
        OutlinedTextField(
            value = state.query,
            onValueChange = onQuery,
            label = { Text(stringResource(R.string.settings_db_search)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        )
        state.message?.let {
            Text(
                it.resolve(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
        }
        if (state.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
        SettingsDbBody(state, onEdit = { editing = it })
    }

    val entry = editing
    if (entry != null) {
        EditSettingDialog(
            entry = entry,
            saving = state.saving == entry.key,
            onDismiss = { editing = null },
            onSave = { value ->
                onSave(entry.key, value)
                editing = null
            },
        )
    }
}

@Composable
private fun SettingsDbBody(
    state: SettingsDbState,
    onEdit: (SettingEntry) -> Unit,
) {
    val error = state.error
    when {
        !state.connected -> {
            SettingsDbMessage(stringResource(R.string.settings_db_no_device))
        }

        error != null -> {
            SettingsDbMessage(error.resolve())
        }

        state.visible.isEmpty() && !state.loading -> {
            SettingsDbMessage(stringResource(R.string.settings_db_empty))
        }

        else -> {
            LazyColumn(Modifier.fillMaxSize()) {
                items(state.visible) { entry ->
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .clickable { onEdit(entry) }
                            .padding(horizontal = 16.dp, vertical = 6.dp),
                    ) {
                        Text(entry.key, style = MaterialTheme.typography.bodyMedium, fontFamily = FontFamily.Monospace)
                        Text(
                            entry.value,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    HorizontalDivider()
                }
            }
        }
    }
}

@Composable
private fun EditSettingDialog(
    entry: SettingEntry,
    saving: Boolean,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
) {
    var value by remember(entry.key) { mutableStateOf(entry.value) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(entry.key, fontFamily = FontFamily.Monospace) },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = { value = it },
                singleLine = false,
                label = { Text(stringResource(R.string.settings_db_value)) },
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(onClick = { onSave(value) }, enabled = !saving) {
                Text(stringResource(R.string.settings_db_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.settings_db_cancel)) }
        },
    )
}

@Composable
private fun SettingsDbMessage(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(24.dp),
    )
}

@Composable
private fun namespaceLabel(namespace: String): String =
    when (namespace) {
        "global" -> stringResource(R.string.settings_db_namespace_global)
        "secure" -> stringResource(R.string.settings_db_namespace_secure)
        else -> stringResource(R.string.settings_db_namespace_system)
    }

private val sample =
    listOf(
        SettingEntry("android_id", "9f2c1a77b4e1d0c3"),
        SettingEntry("screen_brightness", "128"),
        SettingEntry("wifi_scan_always_enabled", "0"),
    )

@Preview(name = "Ajustes con entradas")
@Composable
private fun SettingsDbPreview() {
    SettingsDbScreen(
        state = SettingsDbState(entries = sample, visible = sample, connected = true),
        onBack = {},
        onNamespace = {},
        onQuery = {},
        onSave = { _, _ -> },
    )
}

@Preview(name = "Ajustes vacio")
@Composable
private fun SettingsDbEmptyPreview() {
    SettingsDbScreen(
        state = SettingsDbState(connected = true, query = "zzz"),
        onBack = {},
        onNamespace = {},
        onQuery = {},
        onSave = { _, _ -> },
    )
}

@Preview(name = "Ajustes sin dispositivo")
@Composable
private fun SettingsDbDisconnectedPreview() {
    SettingsDbScreen(
        state = SettingsDbState(connected = false, error = UiText.Resource(R.string.settings_db_no_device)),
        onBack = {},
        onNamespace = {},
        onQuery = {},
        onSave = { _, _ -> },
    )
}
