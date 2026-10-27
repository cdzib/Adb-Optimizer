@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.optimizer.tools.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.optimizer.tools.R
import com.optimizer.tools.domain.model.AppSettings
import com.optimizer.tools.domain.model.ThemeMode

@Composable
internal fun SettingsScreen(
    settings: AppSettings,
    onThemeMode: (ThemeMode) -> Unit,
    onShowSystemApps: (Boolean) -> Unit,
    onKeepScreenOn: (Boolean) -> Unit,
    onAutoRefreshMetrics: (Boolean) -> Unit,
    onMetricsRefreshSeconds: (Int) -> Unit,
    onAutoReconnect: (Boolean) -> Unit,
    remoteRole: Boolean,
    onOpenDpc: () -> Unit,
    onOpenLogcat: () -> Unit,
    onOpenSettingsDb: () -> Unit,
) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Spacer(Modifier.padding(8.dp))
        SettingsSection(title = stringResource(R.string.settings_section_appearance)) {
            SettingsDropdown(
                label = stringResource(R.string.settings_theme_label),
                selectedLabel = themeModeLabel(settings.themeMode),
                options = ThemeMode.entries.toList(),
                optionLabel = { themeModeLabel(it) },
                onSelect = onThemeMode,
            )
        }

        SettingsSection(title = stringResource(R.string.settings_section_apps)) {
            SettingsSwitchRow(
                title = stringResource(R.string.settings_show_system_apps),
                description = stringResource(R.string.settings_show_system_apps_description),
                checked = settings.showSystemApps,
                onCheckedChange = onShowSystemApps,
            )
        }

        SettingsSection(title = stringResource(R.string.settings_section_connection)) {
            if (remoteRole) {
                SettingsSwitchRow(
                    title = stringResource(R.string.settings_auto_reconnect),
                    description = stringResource(R.string.settings_auto_reconnect_description),
                    checked = settings.autoReconnect,
                    onCheckedChange = onAutoReconnect,
                )
            }
            SettingsSwitchRow(
                title = stringResource(R.string.settings_keep_screen_on),
                description = stringResource(R.string.settings_keep_screen_on_description),
                checked = settings.keepScreenOn,
                onCheckedChange = onKeepScreenOn,
            )
            SettingsSwitchRow(
                title = stringResource(R.string.settings_auto_refresh_metrics),
                description = stringResource(R.string.settings_auto_refresh_metrics_description),
                checked = settings.autoRefreshMetrics,
                onCheckedChange = onAutoRefreshMetrics,
            )
            if (settings.autoRefreshMetrics) {
                SettingsDropdown(
                    label = stringResource(R.string.settings_refresh_frequency),
                    selectedLabel = stringResource(R.string.settings_seconds, settings.metricsRefreshSeconds),
                    options = listOf(15, 30, 60, 120),
                    optionLabel = { stringResource(R.string.settings_seconds, it) },
                    onSelect = onMetricsRefreshSeconds,
                )
                Text(
                    stringResource(R.string.settings_refresh_frequency_hint, settings.metricsRefreshSeconds),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        SettingsSection(title = stringResource(R.string.settings_section_tools)) {
            OutlinedButton(
                onClick = onOpenLogcat,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
            ) { Text(stringResource(R.string.settings_open_logcat)) }
            OutlinedButton(
                onClick = onOpenSettingsDb,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
            ) { Text(stringResource(R.string.settings_open_settings_db)) }
        }

        if (remoteRole) {
            SettingsSection(title = stringResource(R.string.settings_section_dpc)) {
                Text(
                    stringResource(R.string.settings_dpc_description),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedButton(
                    onClick = onOpenDpc,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                ) { Text(stringResource(R.string.settings_open_dpc)) }
            }
        }

        if (remoteRole) {
            Text(
                stringResource(R.string.settings_wireless_debug_required),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 24.dp),
            )
        }
    }
}

@Composable
private fun SettingsSection(
    title: String,
    content: @Composable () -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            content()
        }
    }
}

@Composable
private fun <T> SettingsDropdown(
    label: String,
    selectedLabel: String,
    options: List<T>,
    optionLabel: @Composable (T) -> String,
    onSelect: (T) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
    ) {
        OutlinedTextField(
            value = selectedLabel,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable),
            singleLine = true,
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(optionLabel(option)) },
                    onClick = {
                        onSelect(option)
                        expanded = false
                    },
                )
            }
        }
    }
}

@Composable
private fun SettingsSwitchRow(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.bodyMedium)
            Text(
                description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.width(16.dp))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun themeModeLabel(mode: ThemeMode): String =
    when (mode) {
        ThemeMode.SYSTEM -> stringResource(R.string.settings_theme_system)
        ThemeMode.LIGHT -> stringResource(R.string.settings_theme_light)
        ThemeMode.DARK -> stringResource(R.string.settings_theme_dark)
    }
