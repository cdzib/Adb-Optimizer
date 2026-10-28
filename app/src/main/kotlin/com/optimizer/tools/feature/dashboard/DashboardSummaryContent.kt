@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.optimizer.tools.feature.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.optimizer.tools.R
import com.optimizer.tools.core.ui.AdbOptimizerTheme
import com.optimizer.tools.core.ui.DesignGroup
import com.optimizer.tools.core.ui.DesignRow
import com.optimizer.tools.core.ui.DesignScreenPadding
import com.optimizer.tools.core.ui.DesignSectionLabel
import com.optimizer.tools.core.ui.DesignStatTile
import com.optimizer.tools.core.ui.DesignTone
import com.optimizer.tools.core.ui.PillButton
import com.optimizer.tools.core.ui.PillStyle
import com.optimizer.tools.core.ui.ScreenHeader
import com.optimizer.tools.core.ui.ScreenIconButton
import com.optimizer.tools.core.ui.UiText
import com.optimizer.tools.core.ui.formatKb
import com.optimizer.tools.core.ui.formatUptime
import com.optimizer.tools.core.ui.resolve
import com.optimizer.tools.domain.model.ShizukuLocalStatus
import com.optimizer.tools.domain.model.ThemeMode
import dev.adboptimizer.core.model.AppTarget
import dev.adboptimizer.core.model.DeviceInfo
import dev.adboptimizer.core.model.DeviceMetrics

internal enum class DashboardCategory(
    val label: Int,
    val hint: Int,
    val icon: ImageVector,
) {
    DEVICE(R.string.dashboard_device, R.string.dashboard_device_hint, Icons.Filled.Smartphone),
    DISPLAY(R.string.dashboard_display, R.string.dashboard_display_hint, Icons.Filled.AspectRatio),
    SENSORS(R.string.dashboard_sensors, R.string.dashboard_sensors_hint, Icons.Filled.Sensors),
    NETWORK(R.string.dashboard_network, R.string.dashboard_network_hint, Icons.Filled.Wifi),
    SYSTEM(R.string.dashboard_system, R.string.dashboard_system_hint, Icons.Filled.Layers),
    CPU(R.string.dashboard_cpu, R.string.dashboard_cpu_hint, Icons.Filled.Memory),
    BATTERY(R.string.dashboard_battery, R.string.dashboard_battery_hint, Icons.Filled.BatteryFull),
    MEMORY(R.string.dashboard_memory, R.string.dashboard_memory_hint, Icons.Filled.Storage),
}

@Composable
internal fun DashboardSummaryContent(
    device: DeviceInfo,
    metrics: DeviceMetrics?,
    target: AppTarget,
    loading: Boolean,
    error: UiText?,
    shizukuStatus: ShizukuLocalStatus,
    onRefresh: () -> Unit,
    onRequestShizuku: () -> Unit,
    onRefreshShizuku: () -> Unit,
    onOpenDetail: (DashboardCategory) -> Unit,
    footer: @Composable () -> Unit,
    health: (@Composable () -> Unit)? = null,
    quickAccess: (@Composable () -> Unit)? = null,
    localAdbReady: Boolean = false,
) {
    var showPermissions by rememberSaveable { mutableStateOf(false) }

    key(device.id, target) {
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = DesignScreenPadding,
        ) {
            item {
                ScreenHeader(
                    title = stringResource(R.string.nav_home),
                    subtitle = device.displayName,
                    actions = {
                        ScreenIconButton(Icons.Filled.Refresh, stringResource(R.string.dashboard_refresh), onRefresh)
                    },
                )
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    DesignStatTile(
                        label = stringResource(R.string.dashboard_battery),
                        value = batteryText(metrics),
                        hint = stringResource(R.string.dashboard_current_level),
                        modifier = Modifier.weight(1f),
                    )
                    DesignStatTile(
                        label = stringResource(R.string.dashboard_ram_used),
                        value =
                            metrics?.takeIf { it.totalRamKb > 0 }?.let { formatKb(it.usedRamKb) }
                                ?: stringResource(R.string.dashboard_unavailable),
                        hint =
                            metrics?.takeIf { it.totalRamKb > 0 }?.let {
                                stringResource(R.string.dashboard_of_total, formatKb(it.totalRamKb))
                            } ?: "—",
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            health?.let { banner ->
                item { Column(Modifier.padding(top = 12.dp)) { banner() } }
            }
            if (quickAccess != null) {
                item {
                    DesignSectionLabel(stringResource(R.string.dashboard_quick_access))
                    quickAccess()
                }
            }
            item {
                DesignSectionLabel(stringResource(R.string.dashboard_about_device))
                DeviceSummaryCard(device, target)
            }
            if (target == AppTarget.LOCAL) {
                item {
                    if (localAdbReady) {
                        DesignGroup(Modifier.padding(top = 8.dp)) {
                            DesignRow(
                                icon = Icons.Filled.Cable,
                                title = stringResource(R.string.dashboard_local_adb_ready),
                                subtitle = stringResource(R.string.dashboard_local_adb_ready_body),
                                tone = DesignTone.Green,
                                isFirst = true,
                                showChevron = false,
                                onClick = {},
                            )
                        }
                        return@item
                    }
                    DesignGroup(Modifier.padding(top = 8.dp)) {
                        DesignRow(
                            icon = Icons.Filled.Refresh,
                            title =
                                stringResource(
                                    if (shizukuStatus == ShizukuLocalStatus.READY) {
                                        R.string.dashboard_permissions_ready
                                    } else {
                                        R.string.dashboard_permissions
                                    },
                                ),
                            subtitle = stringResource(R.string.dashboard_shizuku_row_hint),
                            tone =
                                if (shizukuStatus == ShizukuLocalStatus.READY) {
                                    DesignTone.Green
                                } else {
                                    DesignTone.Amber
                                },
                            isFirst = true,
                            onClick = { showPermissions = !showPermissions },
                        )
                    }
                    if (showPermissions) {
                        Box(Modifier.padding(top = 8.dp)) {
                            ShizukuStatusCard(shizukuStatus, onRequestShizuku, onRefreshShizuku)
                        }
                    }
                }
            }
            item {
                DesignSectionLabel(stringResource(R.string.dashboard_explore))
                DesignGroup {
                    DashboardCategory.entries.forEachIndexed { index, category ->
                        DesignRow(
                            icon = category.icon,
                            title = stringResource(category.label),
                            subtitle = stringResource(category.hint),
                            tone = category.tone(),
                            isFirst = index == 0,
                            onClick = { onOpenDetail(category) },
                        )
                    }
                }
            }
            if (error != null || metrics == null) {
                item {
                    Column(Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            if (loading && metrics == null) {
                                stringResource(R.string.dashboard_loading)
                            } else {
                                error?.resolve() ?: stringResource(R.string.dashboard_metrics_error)
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (!loading) {
                            PillButton(stringResource(R.string.dashboard_retry), onRefresh, style = PillStyle.Outline)
                        }
                    }
                }
            }
            item { Column(Modifier.padding(top = 12.dp)) { footer() } }
            item { Spacer(Modifier.height(8.dp)) }
        }
    }
}

@Composable
private fun DeviceSummaryCard(
    device: DeviceInfo,
    target: AppTarget,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                if (device.isTv) "TV" else "AD",
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                device.displayName,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                stringResource(
                    R.string.dashboard_android_line,
                    device.androidVersion,
                    device.sdk,
                    device.abi,
                    if (device.isTv) "TV" else "",
                ).trim(),
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                stringResource(
                    if (target == AppTarget.REMOTE) {
                        R.string.dashboard_session_remote
                    } else {
                        R.string.dashboard_session_local
                    },
                ),
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
            )
        }
        Box(Modifier.size(8.dp).clip(RoundedCornerShape(50)).background(Color(0xFF4ADE80)))
    }
}

private fun DashboardCategory.tone(): DesignTone =
    when (this) {
        DashboardCategory.DEVICE -> DesignTone.Green
        DashboardCategory.DISPLAY -> DesignTone.Cyan
        DashboardCategory.SENSORS -> DesignTone.Amber
        DashboardCategory.NETWORK -> DesignTone.Cyan
        DashboardCategory.SYSTEM -> DesignTone.Green
        DashboardCategory.CPU -> DesignTone.Red
        DashboardCategory.BATTERY -> DesignTone.Amber
        DashboardCategory.MEMORY -> DesignTone.Cyan
    }

@Composable
private fun batteryText(metrics: DeviceMetrics?): String =
    metrics?.batteryPercent?.takeIf { it in 0..100 }?.let { "$it%" } ?: stringResource(R.string.dashboard_unavailable)

@Composable
internal fun DashboardCategoryContent(
    category: DashboardCategory,
    device: DeviceInfo,
    metrics: DeviceMetrics?,
    target: AppTarget,
    shizukuStatus: ShizukuLocalStatus,
    modifier: Modifier = Modifier,
) {
    val unavailable = stringResource(R.string.dashboard_unavailable)
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(category.icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Text(stringResource(category.label), style = MaterialTheme.typography.titleMedium)
        }
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), shape = MaterialTheme.shapes.large) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                when (category) {
                    DashboardCategory.DEVICE -> {
                        DeviceInfoRow(stringResource(R.string.dashboard_manufacturer), device.manufacturer.ifBlank { unavailable })
                        DeviceInfoRow(stringResource(R.string.dashboard_model), device.model.ifBlank { unavailable })
                        DeviceInfoRow(
                            stringResource(R.string.dashboard_type),
                            stringResource(if (device.isTv) R.string.dashboard_tv_type else R.string.dashboard_phone_type),
                        )
                        DeviceInfoRow(
                            stringResource(R.string.dashboard_connection),
                            if (target ==
                                AppTarget.LOCAL
                            ) {
                                stringResource(R.string.dashboard_local)
                            } else {
                                device.id
                            },
                        )
                        device.hardwareInfo?.let { HardwareDiagnosticRows(it) }
                    }

                    DashboardCategory.DISPLAY -> {
                        DisplayDiagnosticRows(device.displayInfo)
                    }

                    DashboardCategory.SENSORS -> {
                        SensorDiagnosticRows(device.sensors)
                    }

                    DashboardCategory.NETWORK -> {
                        NetworkDiagnosticRows(device.networkInterfaces)
                    }

                    DashboardCategory.SYSTEM -> {
                        DeviceInfoRow(stringResource(R.string.dashboard_android), device.androidVersion.ifBlank { unavailable })
                        DeviceInfoRow(stringResource(R.string.dashboard_sdk), device.sdk.toString())
                        DeviceInfoRow(
                            stringResource(R.string.dashboard_access),
                            stringResource(
                                when {
                                    device.hasRoot -> R.string.dashboard_root
                                    target == AppTarget.REMOTE -> R.string.dashboard_adb
                                    shizukuStatus == ShizukuLocalStatus.READY -> R.string.dashboard_shizuku
                                    else -> R.string.dashboard_basic
                                },
                            ),
                        )
                        DeviceInfoRow(
                            stringResource(R.string.dashboard_uptime),
                            metrics?.let { formatUptime(it.uptimeSeconds) } ?: unavailable,
                        )
                        DeviceInfoRow(
                            stringResource(R.string.dashboard_temperature),
                            metrics?.temperatureC?.let { "%.1f °C".format(it) } ?: unavailable,
                        )
                        DeviceInfoRow(
                            stringResource(R.string.dashboard_thermal),
                            metrics?.let { "${it.maxThermalStatus}/6" } ?: unavailable,
                        )
                        device.hardwareInfo?.let { SystemDiagnosticRows(it) }
                    }

                    DashboardCategory.CPU -> {
                        Text(
                            stringResource(
                                if (target ==
                                    AppTarget.LOCAL
                                ) {
                                    R.string.diagnostics_cpu_estimate
                                } else {
                                    R.string.dashboard_cpu_usage
                                },
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            metrics?.let { "${it.cpuBusyPercent.toInt()}%" } ?: unavailable,
                            style = MaterialTheme.typography.headlineLarge,
                        )
                        if (metrics !=
                            null
                        ) {
                            LinearProgressIndicator(
                                progress = { (metrics.cpuBusyPercent / 100f).coerceIn(0f, 1f) },
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                        DeviceInfoRow(stringResource(R.string.dashboard_abi), device.abi.ifBlank { unavailable })
                        DeviceInfoRow(stringResource(R.string.dashboard_load), metrics?.let { "%.2f".format(it.load1) } ?: unavailable)
                        DeviceInfoRow(stringResource(R.string.dashboard_processes), metrics?.backgroundProcesses?.toString() ?: unavailable)
                        CpuDiagnosticRows(device.hardwareInfo, metrics?.cpuFrequencies.orEmpty())
                    }

                    DashboardCategory.BATTERY -> {
                        Text(
                            stringResource(R.string.dashboard_battery_level),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(batteryText(metrics), style = MaterialTheme.typography.headlineLarge)
                        metrics?.batteryPercent?.takeIf { it in 0..100 }?.let { level ->
                            LinearProgressIndicator(progress = { level / 100f }, modifier = Modifier.fillMaxWidth())
                        }
                        BatteryDiagnosticRows(metrics?.batteryDiagnostics)
                    }

                    DashboardCategory.MEMORY -> {
                        DeviceInfoRow(
                            stringResource(R.string.dashboard_ram_used),
                            metrics?.takeIf { it.totalRamKb > 0 }?.let { formatKb(it.usedRamKb) } ?: unavailable,
                        )
                        if (metrics != null &&
                            metrics.totalRamKb > 0
                        ) {
                            LinearProgressIndicator(
                                progress = { metrics.ramRatio.coerceIn(0f, 1f) },
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                        DeviceInfoRow(
                            stringResource(R.string.dashboard_ram_total),
                            metrics?.takeIf { it.totalRamKb > 0 }?.let { formatKb(it.totalRamKb) } ?: unavailable,
                        )
                        DeviceInfoRow(
                            stringResource(R.string.dashboard_ram_free),
                            metrics?.takeIf { it.totalRamKb > 0 }?.let { formatKb(it.freeRamKb) } ?: unavailable,
                        )
                        DeviceInfoRow(
                            stringResource(R.string.dashboard_ram_cached),
                            metrics?.takeIf { it.totalRamKb > 0 }?.let { formatKb(it.cachedKb) } ?: unavailable,
                        )
                        metrics?.lowMemoryThresholdKb?.let {
                            DeviceInfoRow(
                                stringResource(R.string.diagnostics_memory_threshold),
                                formatKb(it),
                            )
                        }
                        metrics?.isLowMemory?.let {
                            DeviceInfoRow(
                                stringResource(R.string.diagnostics_low_memory),
                                stringResource(if (it) R.string.diagnostics_yes else R.string.diagnostics_no),
                            )
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        DeviceInfoRow(
                            stringResource(R.string.dashboard_storage),
                            metrics?.takeIf { it.storageTotalKb > 0 }?.let { formatKb(it.storageUsedKb) } ?: unavailable,
                        )
                        DeviceInfoRow(
                            stringResource(R.string.dashboard_storage_free),
                            metrics?.takeIf { it.storageTotalKb > 0 }?.let { formatKb(it.storageAvailableKb) } ?: unavailable,
                        )
                        DeviceInfoRow(
                            stringResource(R.string.dashboard_storage_total),
                            metrics?.takeIf { it.storageTotalKb > 0 }?.let { formatKb(it.storageTotalKb) } ?: unavailable,
                        )
                    }
                }
            }
        }
    }
}

private val previewDevice = DeviceInfo("preview", "Galaxy A35", "Samsung", 36, "16", false, false, "arm64-v8a")
private val previewMetrics = DeviceMetrics(totalRamKb = 8_000_000, usedRamKb = 3_200_000, cpuBusyPercent = 18f, batteryPercent = 78)

@Preview(name = "Resumen", showBackground = true, widthDp = 400, heightDp = 900)
@Composable
private fun DashboardSummaryPreview() {
    AdbOptimizerTheme(ThemeMode.DARK) {
        DashboardSummaryContent(
            previewDevice,
            previewMetrics,
            AppTarget.LOCAL,
            false,
            null,
            ShizukuLocalStatus.NOT_INSTALLED,
            {},
            {},
            {},
            {},
            {},
        )
    }
}

@Preview(name = "Resumen sin métricas texto grande", showBackground = true, widthDp = 360, heightDp = 900, fontScale = 1.3f)
@Composable
private fun DashboardSummaryUnavailablePreview() {
    AdbOptimizerTheme(ThemeMode.LIGHT) {
        DashboardSummaryContent(previewDevice, null, AppTarget.REMOTE, false, null, ShizukuLocalStatus.NOT_INSTALLED, {}, {}, {}, {}, {})
    }
}
