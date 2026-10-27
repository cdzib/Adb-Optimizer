package com.optimizer.tools.feature.settingsdb

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.optimizer.tools.R
import com.optimizer.tools.core.ui.SkeletonLine
import com.optimizer.tools.core.ui.ToolEmptyState
import com.optimizer.tools.core.ui.ToolSearchField
import com.optimizer.tools.core.ui.ToolTopBar
import com.optimizer.tools.core.ui.resolve
import com.optimizer.tools.data.SettingEntry

private val namespaces = listOf("global", "secure", "system")
private val namespaceLabels =
    mapOf(
        "global" to R.string.settings_db_namespace_global,
        "secure" to R.string.settings_db_namespace_secure,
        "system" to R.string.settings_db_namespace_system,
    )

@Composable
internal fun SettingsDbScreen(
    state: SettingsDbState,
    onBack: () -> Unit,
    onNamespace: (String) -> Unit,
    onQuery: (String) -> Unit,
    onSave: (String, String) -> Unit,
    onRetry: () -> Unit,
) {
    var editing by remember { mutableStateOf<SettingEntry?>(null) }
    Column(Modifier.fillMaxSize()) {
        ToolTopBar(
            title = stringResource(R.string.settings_db_title),
            subtitle = stringResource(R.string.settings_db_subtitle, state.visible.size),
            onBack = onBack,
        )
        NamespaceSelector(state.namespace, onNamespace)
        ToolSearchField(
            value = state.query,
            onValueChange = onQuery,
            placeholder = stringResource(R.string.settings_db_search),
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        AnimatedVisibility(
            visible = state.message != null,
            enter = fadeIn(tween(220)) + slideInVertically(tween(220)) { -it / 3 },
            exit = fadeOut(tween(180)),
        ) {
            state.message?.let { banner(it.resolve()) }
        }
        Spacer(Modifier.height(8.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Box(Modifier.weight(1f)) {
            SettingsDbContent(state, onEdit = { editing = it }, onRetry = onRetry)
        }
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
private fun NamespaceSelector(
    selected: String,
    onNamespace: (String) -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        namespaces.forEach { namespace ->
            val label = namespaceLabels.getValue(namespace)
            FilterChip(
                selected = selected == namespace,
                onClick = { onNamespace(namespace) },
                label = { Text(stringResource(label), style = MaterialTheme.typography.labelLarge) },
                colors =
                    FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f),
                    ),
            )
        }
    }
}

@Composable
private fun SettingsDbContent(
    state: SettingsDbState,
    onEdit: (SettingEntry) -> Unit,
    onRetry: () -> Unit,
) {
    val error = state.error
    when {
        !state.connected -> {
            ToolEmptyState(
                icon = Icons.Filled.PhoneAndroid,
                title = stringResource(R.string.settings_db_no_device_title),
                description = stringResource(R.string.settings_db_no_device),
            )
        }

        error != null -> {
            ToolEmptyState(
                icon = Icons.Filled.WarningAmber,
                title = stringResource(R.string.settings_db_error_title),
                description = error.resolve(),
                action = {
                    OutlinedButton(onClick = onRetry) { Text(stringResource(R.string.action_retry)) }
                },
            )
        }

        state.loading && state.entries.isEmpty() -> {
            Column(Modifier.padding(vertical = 8.dp)) {
                repeat(8) { SkeletonSettingRow() }
            }
        }

        state.visible.isEmpty() -> {
            ToolEmptyState(
                icon = Icons.Filled.Search,
                title = stringResource(R.string.settings_db_empty_title),
                description = stringResource(R.string.settings_db_empty_description),
            )
        }

        else -> {
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(state.visible) { entry ->
                    SettingRow(
                        entry = entry,
                        saving = state.saving == entry.key,
                        onClick = { onEdit(entry) },
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingRow(
    entry: SettingEntry,
    saving: Boolean,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surface)
            .clickable(enabled = !saving, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                entry.key,
                style = MaterialTheme.typography.bodyMedium,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                entry.value.ifEmpty { stringResource(R.string.settings_db_empty_value) },
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(12.dp))
        if (saving) {
            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
        } else {
            Icon(
                Icons.Filled.Edit,
                contentDescription = stringResource(R.string.settings_db_edit),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@Composable
private fun SkeletonSettingRow() {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            SkeletonLine(0.55f)
            SkeletonLine(0.3f)
        }
    }
}

@Composable
private fun banner(text: String) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Filled.CheckCircle,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(8.dp))
        Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
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
        title = {
            Text(
                entry.key,
                style = MaterialTheme.typography.titleSmall,
                fontFamily = FontFamily.Monospace,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = value,
                    onValueChange = { value = it },
                    label = { Text(stringResource(R.string.settings_db_value)) },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    stringResource(R.string.settings_db_edit_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(value) }, enabled = !saving) {
                if (saving) {
                    CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                } else {
                    Text(stringResource(R.string.settings_db_save))
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !saving) { Text(stringResource(R.string.settings_db_cancel)) }
        },
        shape = RoundedCornerShape(24.dp),
    )
}

@Composable
private fun PreviewShell(state: SettingsDbState) {
    SettingsDbScreen(state = state, onBack = {}, onNamespace = {}, onQuery = {}, onSave = { _, _ -> }, onRetry = {})
}

private val sample =
    listOf(
        SettingEntry("android_id", "9f2c1a77b4e1d0c3"),
        SettingEntry("screen_brightness", "128"),
        SettingEntry("accessibility_display_inversion_enabled", "null"),
        SettingEntry("wifi_scan_always_enabled", "0"),
    )

@Preview(name = "Ajustes · contenido", heightDp = 720)
@Composable
private fun SettingsDbPreview() {
    PreviewShell(SettingsDbState(entries = sample, visible = sample, connected = true))
}

@Preview(name = "Ajustes · cargando", heightDp = 720)
@Composable
private fun SettingsDbLoadingPreview() {
    PreviewShell(SettingsDbState(connected = true, loading = true))
}

@Preview(name = "Ajustes · vacio", heightDp = 720)
@Composable
private fun SettingsDbEmptyPreview() {
    PreviewShell(SettingsDbState(connected = true, query = "zzz"))
}

@Preview(name = "Ajustes · sin dispositivo", heightDp = 720)
@Composable
private fun SettingsDbDisconnectedPreview() {
    PreviewShell(SettingsDbState(connected = false))
}
