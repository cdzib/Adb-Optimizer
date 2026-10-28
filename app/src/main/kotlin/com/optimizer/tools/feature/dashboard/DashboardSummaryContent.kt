@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.optimizer.tools.feature.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.ColumnScope
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
import com.optimizer.tools.core.ui.DesignInfoRow
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
    val hardware = device.hardwareInfo
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        when (category) {
            DashboardCategory.DEVICE -> {
                DetailInfoSection(
                    label = R.string.dashboard_identity,
                    icon = Icons.Filled.Smartphone,
                    tone = DesignTone.Neutral,
                    rows =
                        buildList {
                            add(R.string.diagnostics_brand to (hardware?.brand ?: device.manufacturer))
                            add(R.string.diagnostics_product to (hardware?.product ?: device.model))
                            add(
                                R.string.dashboard_type to
                                    stringResource(
                                        if (device.isTv) R.string.dashboard_tv_type else R.string.dashboard_phone_type,
                                    ),
                            )
                            add(
                                R.string.dashboard_connection to
                                    if (target == AppTarget.LOCAL) {
                                        stringResource(R.string.dashboard_local)
                                    } else {
                                        device.id
                                    },
                            )
                            hardware?.supportsUsbHost?.let {
                                add(
                                    R.string.diagnostics_usb_host to
                                        stringResource(if (it) R.string.diagnostics_yes else R.string.diagnostics_no),
                                )
                            }
                        },
                )
                DetailInfoSection(
                    label = R.string.dashboard_system,
                    icon = Icons.Filled.Layers,
                    tone = DesignTone.Green,
                    rows =
                        buildList {
                            add(R.string.dashboard_android to device.androidVersion.ifBlank { unavailable })
                            add(R.string.dashboard_sdk to device.sdk.toString())
                            add(R.string.dashboard_abi to device.abi.ifBlank { unavailable })
                            add(R.string.dashboard_access to stringResource(accessLabel(device, target, shizukuStatus)))
                            add(
                                R.string.dashboard_uptime to
                                    (metrics?.let { formatUptime(it.uptimeSeconds) } ?: unavailable),
                            )
                        },
                )
                if (hardware != null) {
                    DetailSection(R.string.dashboard_hardware) { HardwareDiagnosticRows(hardware) }
                    DetailSection(R.string.dashboard_build_details) { SystemDiagnosticRows(hardware) }
                }
            }

            DashboardCategory.DISPLAY -> {
                DetailSection(R.string.dashboard_display) { DisplayDiagnosticRows(device.displayInfo) }
            }

            DashboardCategory.SENSORS -> {
                DetailSection(R.string.dashboard_sensors) { SensorDiagnosticRows(device.sensors) }
            }

            DashboardCategory.NETWORK -> {
                DetailSection(R.string.dashboard_network) { NetworkDiagnosticRows(device.networkInterfaces) }
            }

            DashboardCategory.SYSTEM -> {
                DetailInfoSection(
                    label = R.string.dashboard_system,
                    icon = Icons.Filled.Layers,
                    tone = DesignTone.Green,
                    rows =
                        buildList {
                            add(R.string.dashboard_android to device.androidVersion.ifBlank { unavailable })
                            add(R.string.dashboard_sdk to device.sdk.toString())
                            add(R.string.dashboard_access to stringResource(accessLabel(device, target, shizukuStatus)))
                            add(
                                R.string.dashboard_uptime to
                                    (metrics?.let { formatUptime(it.uptimeSeconds) } ?: unavailable),
                            )
                            add(
                                R.string.dashboard_temperature to
                                    (metrics?.temperatureC?.let { "%.1f °C".format(it) } ?: unavailable),
                            )
                            add(
                                R.string.dashboard_thermal to
                                    (metrics?.let { "${it.maxThermalStatus}/6" } ?: unavailable),
                            )
                        },
                )
                if (hardware != null) {
                    DetailSection(R.string.dashboard_hardware) { HardwareDiagnosticRows(hardware) }
                    DetailSection(R.string.dashboard_build_details) { SystemDiagnosticRows(hardware) }
                }
            }

            DashboardCategory.CPU -> {
                Text(
                    stringResource(
                        if (target == AppTarget.LOCAL) {
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
                if (metrics != null) {
                    LinearProgressIndicator(
                        progress = { (metrics.cpuBusyPercent / 100f).coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                DetailInfoSection(
                    label = R.string.dashboard_processor,
                    icon = Icons.Filled.Memory,
                    tone = DesignTone.Red,
                    rows =
                        buildList {
                            add(R.string.dashboard_abi to device.abi.ifBlank { unavailable })
                            add(R.string.dashboard_load to (metrics?.let { "%.2f".format(it.load1) } ?: unavailable))
                            add(
                                R.string.dashboard_processes to
                                    (metrics?.backgroundProcesses?.toString() ?: unavailable),
                            )
                            hardware?.let {
                                add(R.string.diagnostics_soc to it.socModel)
                                add(R.string.diagnostics_soc_manufacturer to it.socManufacturer)
                                add(
                                    R.string.diagnostics_abis to
                                        it.supportedAbis.takeIf { abis -> abis.isNotEmpty() }?.joinToString(),
                                )
                            }
                        },
                )
                DetailSection(R.string.dashboard_cores) { CpuCoreRows(metrics?.cpuFrequencies.orEmpty()) }
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
                DetailSection(R.string.dashboard_battery) {
                    BatteryDiagnosticRows(metrics?.batteryDiagnostics)
                }
            }

            DashboardCategory.MEMORY -> {
                DetailInfoSection(
                    label = R.string.dashboard_memory,
                    icon = Icons.Filled.Storage,
                    tone = DesignTone.Cyan,
                    rows =
                        buildList {
                            add(
                                R.string.dashboard_ram_used to
                                    (metrics?.takeIf { it.totalRamKb > 0 }?.let { formatKb(it.usedRamKb) } ?: unavailable),
                            )
                            add(
                                R.string.dashboard_ram_total to
                                    (metrics?.takeIf { it.totalRamKb > 0 }?.let { formatKb(it.totalRamKb) } ?: unavailable),
                            )
                            add(
                                R.string.dashboard_ram_free to
                                    (metrics?.takeIf { it.totalRamKb > 0 }?.let { formatKb(it.freeRamKb) } ?: unavailable),
                            )
                            add(
                                R.string.dashboard_ram_cached to
                                    (metrics?.takeIf { it.totalRamKb > 0 }?.let { formatKb(it.cachedKb) } ?: unavailable),
                            )
                            metrics?.lowMemoryThresholdKb?.let {
                                add(R.string.diagnostics_memory_threshold to formatKb(it))
                            }
                            metrics?.isLowMemory?.let {
                                add(
                                    R.string.diagnostics_low_memory to
                                        stringResource(if (it) R.string.diagnostics_yes else R.string.diagnostics_no),
                                )
                            }
                        },
                )
                DetailInfoSection(
                    label = R.string.dashboard_storage,
                    icon = Icons.Filled.Storage,
                    tone = DesignTone.Cyan,
                    rows =
                        buildList {
                            val storage = metrics?.takeIf { it.storageTotalKb > 0 }
                            add(R.string.dashboard_storage to (storage?.let { formatKb(it.storageUsedKb) } ?: unavailable))
                            add(
                                R.string.dashboard_storage_free to
                                    (storage?.let { formatKb(it.storageAvailableKb) } ?: unavailable),
                            )
                            add(
                                R.string.dashboard_storage_total to
                                    (storage?.let { formatKb(it.storageTotalKb) } ?: unavailable),
                            )
                        },
                )
            }
        }
    }
}

private fun accessLabel(
    device: DeviceInfo,
    target: AppTarget,
    shizukuStatus: ShizukuLocalStatus,
): Int =
    when {
        device.hasRoot -> R.string.dashboard_root
        target == AppTarget.REMOTE -> R.string.dashboard_adb
        shizukuStatus == ShizukuLocalStatus.READY -> R.string.dashboard_shizuku
        else -> R.string.dashboard_basic
    }

@Composable
private fun DetailSection(
    label: Int,
    content: @Composable ColumnScope.() -> Unit,
) {
    DesignSectionLabel(stringResource(label))
    DesignGroup(content = content)
}

@Composable
private fun DetailInfoSection(
    label: Int,
    icon: ImageVector,
    tone: DesignTone,
    rows: List<Pair<Int, String?>>,
) {
    DetailSection(label) {
        rows.forEachIndexed { index, (titleRes, value) ->
            DesignInfoRow(
                icon = icon,
                title = stringResource(titleRes),
                value = value,
                tone = tone,
                isFirst = index == 0,
            )
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
