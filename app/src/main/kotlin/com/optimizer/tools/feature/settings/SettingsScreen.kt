@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.optimizer.tools.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.optimizer.tools.R
import com.optimizer.tools.app.tv.LocalTelevisionUi
import com.optimizer.tools.app.tv.TvFocusCard
import com.optimizer.tools.app.tv.TvIconBadge
import com.optimizer.tools.app.tv.TvScreenHeader
import com.optimizer.tools.app.tv.TvSectionLabel
import com.optimizer.tools.app.tv.TvSwitchRow
import com.optimizer.tools.app.tv.TvTile
import com.optimizer.tools.core.ui.AdbOptimizerTheme
import com.optimizer.tools.core.ui.DesignGroup
import com.optimizer.tools.core.ui.DesignRow
import com.optimizer.tools.core.ui.DesignSectionLabel
import com.optimizer.tools.core.ui.DesignSwitchRow
import com.optimizer.tools.core.ui.DesignTone
import com.optimizer.tools.core.ui.DesignValueRow
import com.optimizer.tools.core.ui.ScreenHeader
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
    onOpenDoze: () -> Unit,
    onOpenProfiles: () -> Unit,
    onOpenDebloat: () -> Unit,
    onOpenFiles: () -> Unit,
    onOpenConsole: () -> Unit,
    onOpenTools: (() -> Unit)? = null,
) {
    if (LocalTelevisionUi.current) {
        TelevisionSettingsContent(
            settings = settings,
            onThemeMode = onThemeMode,
            onShowSystemApps = onShowSystemApps,
            onKeepScreenOn = onKeepScreenOn,
            onAutoRefreshMetrics = onAutoRefreshMetrics,
            onMetricsRefreshSeconds = onMetricsRefreshSeconds,
            onAutoReconnect = onAutoReconnect,
            remoteRole = remoteRole,
            onOpenTools = onOpenTools,
            onOpenDpc = onOpenDpc,
        )
        return
    }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        ScreenHeader(
            title = stringResource(R.string.nav_settings),
            subtitle = stringResource(R.string.tools_preferences_description),
        )

        DesignSectionLabel(stringResource(R.string.settings_section_appearance))
        DesignGroup {
            SettingsSelectRow(
                icon = Icons.Filled.WbSunny,
                title = stringResource(R.string.settings_theme_label),
                value = themeModeLabel(settings.themeMode),
                options = ThemeMode.entries.toList(),
                optionLabel = { themeModeLabel(it) },
                onSelect = onThemeMode,
                isFirst = true,
            )
        }
        Spacer(Modifier.height(12.dp))

        DesignSectionLabel(stringResource(R.string.settings_section_apps))
        DesignGroup {
            DesignSwitchRow(
                icon = Icons.Filled.GridView,
                title = stringResource(R.string.settings_show_system_apps),
                subtitle = stringResource(R.string.settings_show_system_apps_description),
                checked = settings.showSystemApps,
                isFirst = true,
                onCheckedChange = onShowSystemApps,
            )
        }
        Spacer(Modifier.height(12.dp))

        DesignSectionLabel(stringResource(R.string.settings_section_connection))
        DesignGroup {
            DesignSwitchRow(
                icon = Icons.Filled.PhoneAndroid,
                title = stringResource(R.string.settings_keep_screen_on),
                subtitle = stringResource(R.string.settings_keep_screen_on_description),
                checked = settings.keepScreenOn,
                isFirst = true,
                onCheckedChange = onKeepScreenOn,
            )
            DesignSwitchRow(
                icon = Icons.Filled.Refresh,
                title = stringResource(R.string.settings_auto_refresh_metrics),
                subtitle =
                    if (settings.autoRefreshMetrics) {
                        stringResource(R.string.settings_seconds, settings.metricsRefreshSeconds)
                    } else {
                        stringResource(R.string.settings_auto_refresh_metrics_description)
                    },
                checked = settings.autoRefreshMetrics,
                onCheckedChange = onAutoRefreshMetrics,
            )
            if (settings.autoRefreshMetrics) {
                SettingsSelectRow(
                    icon = Icons.Filled.Bolt,
                    title = stringResource(R.string.settings_refresh_frequency),
                    value = stringResource(R.string.settings_seconds, settings.metricsRefreshSeconds),
                    options = listOf(15, 30, 60, 120),
                    optionLabel = { stringResource(R.string.settings_seconds, it) },
                    onSelect = onMetricsRefreshSeconds,
                )
            }
            if (remoteRole) {
                DesignSwitchRow(
                    icon = Icons.Filled.Devices,
                    title = stringResource(R.string.settings_auto_reconnect),
                    subtitle = stringResource(R.string.settings_auto_reconnect_description),
                    checked = settings.autoReconnect,
                    onCheckedChange = onAutoReconnect,
                )
            }
        }
        Spacer(Modifier.height(12.dp))

        if (onOpenTools != null) {
            DesignSectionLabel(stringResource(R.string.settings_section_tools))
            DesignGroup {
                DesignRow(
                    icon = Icons.Filled.Tune,
                    title = stringResource(R.string.tools_title),
                    subtitle = stringResource(R.string.tools_description),
                    tone = DesignTone.Cyan,
                    isFirst = true,
                    onClick = onOpenTools,
                )
            }
            Spacer(Modifier.height(12.dp))
        }

        if (remoteRole) {
            DesignSectionLabel(stringResource(R.string.settings_section_dpc))
            DesignGroup {
                DesignRow(
                    icon = Icons.Filled.Security,
                    title = stringResource(R.string.settings_open_dpc),
                    subtitle = stringResource(R.string.settings_dpc_description),
                    isFirst = true,
                    onClick = onOpenDpc,
                )
            }
            Spacer(Modifier.height(12.dp))
            Text(
                stringResource(R.string.settings_wireless_debug_required),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
            )
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun TelevisionSettingsContent(
    settings: AppSettings,
    onThemeMode: (ThemeMode) -> Unit,
    onShowSystemApps: (Boolean) -> Unit,
    onKeepScreenOn: (Boolean) -> Unit,
    onAutoRefreshMetrics: (Boolean) -> Unit,
    onMetricsRefreshSeconds: (Int) -> Unit,
    onAutoReconnect: (Boolean) -> Unit,
    remoteRole: Boolean,
    onOpenTools: (() -> Unit)?,
    onOpenDpc: () -> Unit,
) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        TvScreenHeader(
            title = stringResource(R.string.nav_settings),
            subtitle = stringResource(R.string.tv_settings_subtitle),
        )
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            TvSectionLabel(stringResource(R.string.settings_section_appearance))
            TvSelectRow(
                icon = Icons.Filled.WbSunny,
                title = stringResource(R.string.settings_theme_label),
                value = themeModeLabel(settings.themeMode),
                options = ThemeMode.entries.toList(),
                optionLabel = { themeModeLabel(it) },
                onSelect = onThemeMode,
            )
            TvSectionLabel(stringResource(R.string.settings_section_apps))
            TvSwitchRow(
                icon = Icons.Filled.GridView,
                title = stringResource(R.string.settings_show_system_apps),
                subtitle = stringResource(R.string.settings_show_system_apps_description),
                checked = settings.showSystemApps,
                onCheckedChange = onShowSystemApps,
            )
            TvSectionLabel(stringResource(R.string.settings_section_connection))
            TvSwitchRow(
                icon = Icons.Filled.PhoneAndroid,
                title = stringResource(R.string.settings_keep_screen_on),
                subtitle = stringResource(R.string.settings_keep_screen_on_description),
                checked = settings.keepScreenOn,
                onCheckedChange = onKeepScreenOn,
            )
            TvSwitchRow(
                icon = Icons.Filled.Refresh,
                title = stringResource(R.string.settings_auto_refresh_metrics),
                subtitle =
                    if (settings.autoRefreshMetrics) {
                        stringResource(R.string.settings_seconds, settings.metricsRefreshSeconds)
                    } else {
                        stringResource(R.string.settings_auto_refresh_metrics_description)
                    },
                checked = settings.autoRefreshMetrics,
                onCheckedChange = onAutoRefreshMetrics,
            )
            if (settings.autoRefreshMetrics) {
                TvSelectRow(
                    icon = Icons.Filled.Bolt,
                    title = stringResource(R.string.settings_refresh_frequency),
                    value = stringResource(R.string.settings_seconds, settings.metricsRefreshSeconds),
                    options = listOf(15, 30, 60, 120),
                    optionLabel = { stringResource(R.string.settings_seconds, it) },
                    onSelect = onMetricsRefreshSeconds,
                )
            }
            if (remoteRole) {
                TvSwitchRow(
                    icon = Icons.Filled.Devices,
                    title = stringResource(R.string.settings_auto_reconnect),
                    subtitle = stringResource(R.string.settings_auto_reconnect_description),
                    checked = settings.autoReconnect,
                    onCheckedChange = onAutoReconnect,
                )
            }
            if (onOpenTools != null || remoteRole) {
                TvSectionLabel(stringResource(R.string.settings_section_tools))
            }
            if (onOpenTools != null) {
                TvTile(
                    icon = Icons.Filled.Tune,
                    title = stringResource(R.string.tools_title),
                    subtitle = stringResource(R.string.tv_dashboard_tools_subtitle),
                    onClick = onOpenTools,
                )
            }
            if (remoteRole) {
                TvTile(
                    icon = Icons.Filled.Security,
                    title = stringResource(R.string.settings_open_dpc),
                    subtitle = stringResource(R.string.settings_dpc_description),
                    onClick = onOpenDpc,
                )
            }
        }
    }
}

@Composable
private fun <T> TvSelectRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    value: String,
    options: List<T>,
    optionLabel: @Composable (T) -> String,
    onSelect: (T) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        TvFocusCard(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                TvIconBadge(icon)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(title, style = MaterialTheme.typography.titleMedium)
                    Text(
                        value,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Icon(
                    Icons.Filled.KeyboardArrowDown,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
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
private fun <T> SettingsSelectRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    value: String,
    options: List<T>,
    optionLabel: @Composable (T) -> String,
    onSelect: (T) -> Unit,
    isFirst: Boolean = false,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        DesignValueRow(
            icon = icon,
            title = title,
            value = value,
            isFirst = isFirst,
            onClick = { expanded = true },
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
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
private fun themeModeLabel(mode: ThemeMode): String =
    when (mode) {
        ThemeMode.SYSTEM -> stringResource(R.string.settings_theme_system)
        ThemeMode.LIGHT -> stringResource(R.string.settings_theme_light)
        ThemeMode.DARK -> stringResource(R.string.settings_theme_dark)
    }

@Preview(showBackground = true, heightDp = 900)
@Composable
private fun SettingsScreenPreview() {
    AdbOptimizerTheme {
        SettingsScreen(
            settings = AppSettings(autoRefreshMetrics = true),
            onThemeMode = {},
            onShowSystemApps = {},
            onKeepScreenOn = {},
            onAutoRefreshMetrics = {},
            onMetricsRefreshSeconds = {},
            onAutoReconnect = {},
            remoteRole = true,
            onOpenDpc = {},
            onOpenLogcat = {},
            onOpenSettingsDb = {},
            onOpenDoze = {},
            onOpenProfiles = {},
            onOpenDebloat = {},
            onOpenFiles = {},
            onOpenConsole = {},
            onOpenTools = {},
        )
    }
}
