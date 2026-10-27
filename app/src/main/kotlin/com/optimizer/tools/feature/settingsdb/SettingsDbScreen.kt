package com.optimizer.tools.feature.settingsdb

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
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
import com.optimizer.tools.data.SettingsChange
import kotlin.math.roundToInt

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
    onNightMode: (NightMode) -> Unit,
    onScreenTimeout: (Int) -> Unit,
    onBrightness: (Int) -> Unit,
    onBatterySaver: (Boolean) -> Unit,
    onUndo: (String) -> Unit,
) {
    var editing by remember { mutableStateOf<SettingEntry?>(null) }
    var showHistory by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize()) {
        ToolTopBar(
            title = stringResource(R.string.settings_db_title),
            subtitle = stringResource(R.string.settings_db_subtitle, state.visible.size),
            onBack = onBack,
        ) {
            IconButton(onClick = { showHistory = true }, modifier = Modifier.size(48.dp)) {
                Icon(
                    Icons.Filled.History,
                    contentDescription = stringResource(R.string.settings_db_history),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        QuickSettingsCard(
            state = state,
            onNightMode = onNightMode,
            onScreenTimeout = onScreenTimeout,
            onBrightness = onBrightness,
            onBatterySaver = onBatterySaver,
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

    if (showHistory) {
        HistoryDialog(
            history = state.history,
            undoing = state.undoing,
            onUndo = onUndo,
            onDismiss = { showHistory = false },
        )
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
    SettingsDbScreen(
        state = state,
        onBack = {},
        onNamespace = {},
        onQuery = {},
        onSave = { _, _ -> },
        onRetry = {},
        onNightMode = {},
        onScreenTimeout = {},
        onBrightness = {},
        onBatterySaver = {},
        onUndo = {},
    )
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

@Composable
private fun QuickSettingsCard(
    state: SettingsDbState,
    onNightMode: (NightMode) -> Unit,
    onScreenTimeout: (Int) -> Unit,
    onBrightness: (Int) -> Unit,
    onBatterySaver: (Boolean) -> Unit,
) {
    val enabled = state.connected && !state.savingPreset
    var expanded by rememberSaveable { mutableStateOf(false) }
    Card(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(16.dp),
    ) {
        Column {
            Row(
                Modifier.fillMaxWidth().clickable { expanded = !expanded }.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.settings_db_quick_title), style = MaterialTheme.typography.titleSmall)
                    Text(
                        if (state.connected) quickSummary(state.quick) else stringResource(R.string.settings_db_quick_no_device),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (state.savingPreset || state.quickLoading) {
                    CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                }
                Icon(
                    if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                    contentDescription =
                        stringResource(
                            if (expanded) R.string.settings_db_quick_collapse else R.string.settings_db_quick_expand,
                        ),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            AnimatedVisibility(
                visible = expanded,
                enter = fadeIn(tween(180)) + expandVertically(tween(220)),
                exit =
                    fadeOut(tween(150)) + shrinkVertically(tween(180)),
            ) {
                QuickSettingsControls(state, enabled, onNightMode, onScreenTimeout, onBrightness, onBatterySaver)
            }
        }
    }
}

@Composable
private fun QuickSettingsControls(
    state: SettingsDbState,
    enabled: Boolean,
    onNightMode: (NightMode) -> Unit,
    onScreenTimeout: (Int) -> Unit,
    onBrightness: (Int) -> Unit,
    onBatterySaver: (Boolean) -> Unit,
) {
    Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionLabel(stringResource(R.string.settings_db_quick_night))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NightMode.entries.forEach { mode ->
                FilterChip(
                    selected = state.quick.nightMode == mode,
                    onClick = { onNightMode(mode) },
                    enabled = enabled,
                    label = { Text(stringResource(mode.label())) },
                )
            }
        }

        SectionLabel(stringResource(R.string.settings_db_quick_timeout))
        Row(
            Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SCREEN_TIMEOUT_PRESETS.forEach { preset ->
                FilterChip(
                    selected = state.quick.timeoutMs == preset,
                    onClick = { onScreenTimeout(preset) },
                    enabled = enabled,
                    label = { Text(stringResource(timeoutLabel(preset))) },
                )
            }
        }

        SectionLabel(stringResource(R.string.settings_db_quick_brightness))
        BrightnessControl(state.quick.brightness, enabled, onBrightness)

        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.settings_db_quick_battery_saver), style = MaterialTheme.typography.bodyMedium)
                Text(
                    stringResource(R.string.settings_db_quick_battery_saver_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(
                checked = state.quick.batterySaver == true,
                onCheckedChange = onBatterySaver,
                enabled = enabled,
            )
        }
    }
}

@Composable
private fun quickSummary(quick: QuickSettings): String {
    val night = quick.nightMode?.let { stringResource(it.label()) } ?: "—"
    val timeout = quick.timeoutMs?.let { stringResource(timeoutLabel(it)) } ?: "—"
    val brightness = quick.brightness?.toString() ?: "—"
    return "$night · $timeout · $brightness"
}

@Composable
private fun BrightnessControl(
    current: Int?,
    enabled: Boolean,
    onBrightness: (Int) -> Unit,
) {
    var value by remember(current) { mutableStateOf((current ?: 128).toFloat()) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Slider(
            value = value,
            onValueChange = { value = it },
            onValueChangeFinished = { onBrightness(value.roundToInt()) },
            valueRange = 0f..255f,
            enabled = enabled,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(12.dp))
        Text(
            "${value.roundToInt()}",
            style = MaterialTheme.typography.labelLarge,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.width(40.dp),
        )
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(text, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

private fun NightMode.label(): Int =
    when (this) {
        NightMode.YES -> R.string.settings_db_quick_night_yes
        NightMode.NO -> R.string.settings_db_quick_night_no
        NightMode.AUTO -> R.string.settings_db_quick_night_auto
    }

private fun timeoutLabel(milliseconds: Int): Int =
    when (milliseconds) {
        15_000 -> R.string.settings_db_timeout_15s
        30_000 -> R.string.settings_db_timeout_30s
        60_000 -> R.string.settings_db_timeout_1m
        300_000 -> R.string.settings_db_timeout_5m
        else -> R.string.settings_db_timeout_10m
    }

@Composable
private fun HistoryDialog(
    history: List<SettingsChange>,
    undoing: String?,
    onUndo: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_db_history_title)) },
        text = {
            if (history.isEmpty()) {
                Text(
                    stringResource(R.string.settings_db_history_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                LazyColumn(Modifier.heightIn(max = 360.dp)) {
                    items(history, key = { it.id }) { change ->
                        Row(
                            Modifier.fillMaxWidth().padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(change.key, style = MaterialTheme.typography.bodyMedium, fontFamily = FontFamily.Monospace)
                                Text(
                                    stringResource(
                                        R.string.settings_db_history_values,
                                        change.previousValue.ifEmpty { "—" },
                                        change.newValue.ifEmpty { "—" },
                                    ),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            TextButton(onClick = { onUndo(change.id) }, enabled = undoing == null) {
                                if (undoing == change.id) {
                                    CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                                } else {
                                    Text(stringResource(R.string.settings_db_history_undo))
                                }
                            }
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_close)) }
        },
        shape = RoundedCornerShape(24.dp),
    )
}
