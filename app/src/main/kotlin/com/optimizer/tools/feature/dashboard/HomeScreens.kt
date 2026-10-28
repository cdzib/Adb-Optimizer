@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.optimizer.tools.feature.dashboard

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.ScreenShare
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.optimizer.tools.R
import com.optimizer.tools.app.tv.LocalTelevisionUi
import com.optimizer.tools.app.tv.TvActionRow
import com.optimizer.tools.app.tv.TvSectionLabel
import com.optimizer.tools.app.tv.TvStatCard
import com.optimizer.tools.app.tv.TvTile
import com.optimizer.tools.core.ui.AdbOptimizerTheme
import com.optimizer.tools.core.ui.AppColors
import com.optimizer.tools.core.ui.DesignGroup
import com.optimizer.tools.core.ui.DesignRow
import com.optimizer.tools.core.ui.DesignTone
import com.optimizer.tools.core.ui.Translate
import com.optimizer.tools.core.ui.formatKb
import com.optimizer.tools.core.ui.resolve
import com.optimizer.tools.domain.model.DevicePowerAction
import com.optimizer.tools.domain.model.ShizukuLocalStatus
import dev.adboptimizer.core.model.AppTarget
import dev.adboptimizer.core.model.DeviceInfo
import dev.adboptimizer.core.model.DeviceMetrics
import dev.adboptimizer.core.model.HealthLevel

@Composable
internal fun DashboardScreen(
    state: DashboardState,
    target: AppTarget,
    shizukuStatus: com.optimizer.tools.domain.model.ShizukuLocalStatus,
    localAdbReady: Boolean = false,
    t: Translate,
    onRefresh: () -> Unit,
    onOptimize: () -> Unit,
    onOpenApps: () -> Unit,
    onRequestShizuku: () -> Unit,
    onRefreshShizuku: () -> Unit,
    onOpenTvLauncher: () -> Unit,
    onOpenRemote: () -> Unit,
    onOpenDashboardDetail: (DashboardCategory) -> Unit,
    onRequestPowerAction: (DevicePowerAction) -> Unit,
    onConfirmPowerAction: () -> Unit,
    onDismissPowerAction: () -> Unit,
    onOpenTools: (() -> Unit)? = null,
    onOpenProfiles: (() -> Unit)? = null,
    onOpenPowerOptions: () -> Unit = {},
) {
    val device = state.device.takeIf { state.target == target }
    val metrics = state.metrics.takeIf { state.target == target }
    val localPrivilege = target == AppTarget.REMOTE || shizukuStatus == ShizukuLocalStatus.READY || localAdbReady

    state.pendingPowerAction?.takeIf { it.target == target }?.let {
        DevicePowerConfirmation(it, onConfirmPowerAction, onDismissPowerAction)
    }

    if (LocalTelevisionUi.current) {
        TelevisionDashboardContent(
            state,
            target,
            shizukuStatus,
            t,
            onRefresh,
            onOptimize,
            onOpenApps,
            onRequestShizuku,
            onRefreshShizuku,
            onOpenTvLauncher,
            onOpenRemote,
            onOpenDashboardDetail,
            onOpenTools,
            localAdbReady = localAdbReady,
        )
        return
    }

    if (device == null) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            state.powerMessage?.let { Text(it.resolve()) }
            onOpenTools?.let { ToolsEntryCard(it) }
            ShizukuStatusCard(
                status = shizukuStatus,
                onRequestPermission = onRequestShizuku,
                onRefresh = onRefreshShizuku,
            )
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    Modifier.fillMaxWidth().padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Icon(
                        Icons.Filled.Devices,
                        contentDescription = null,
                        modifier = Modifier.size(40.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        stringResource(
                            if (target == AppTarget.LOCAL) {
                                R.string.dashboard_local_loading
                            } else {
                                R.string.dashboard_remote_empty
                            },
                        ),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        stringResource(
                            if (target == AppTarget.LOCAL) {
                                R.string.dashboard_local_loading_body
                            } else {
                                R.string.dashboard_remote_empty_body
                            },
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (state.loading) {
                        CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                    }
                    Button(
                        onClick = onRefresh,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                    ) { Text(stringResource(R.string.dashboard_retry)) }
                    if (target == AppTarget.LOCAL) {
                        OutlinedButton(
                            onClick = onOpenApps,
                            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                        ) { Text(stringResource(R.string.dashboard_manage_apps)) }
                    }
                }
            }
        }
        return
    }

    DashboardSummaryContent(
        device = device,
        metrics = metrics,
        target = target,
        loading = state.loading,
        error = state.error,
        shizukuStatus = shizukuStatus,
        onRefresh = onRefresh,
        onRequestShizuku = onRequestShizuku,
        onRefreshShizuku = onRefreshShizuku,
        onOpenDetail = onOpenDashboardDetail,
        health =
            if (metrics != null && localPrivilege) {
                {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        HealthBanner(metrics, t, t("optimize.title"), onOptimize)
                        if (target == AppTarget.REMOTE) MirrorEntryCard(onOpenRemote)
                    }
                }
            } else {
                null
            },
        quickAccess = {
            val canOptimize = localPrivilege
            DesignGroup {
                var first = true
                if (canOptimize) {
                    DesignRow(
                        icon = Icons.Filled.Bolt,
                        title = t("optimize.title"),
                        subtitle = stringResource(R.string.dashboard_quick_optimize_body),
                        tone = DesignTone.Green,
                        isFirst = true,
                        onClick = onOptimize,
                    )
                    first = false
                }
                onOpenTools?.let { openTools ->
                    DesignRow(
                        icon = Icons.Filled.Tune,
                        title = stringResource(R.string.tools_title),
                        subtitle = stringResource(R.string.tools_description),
                        tone = DesignTone.Cyan,
                        isFirst = first,
                        onClick = openTools,
                    )
                    first = false
                }
                onOpenProfiles?.let { openProfiles ->
                    DesignRow(
                        icon = Icons.Filled.Layers,
                        title = stringResource(R.string.profiles_title),
                        subtitle = stringResource(R.string.profiles_subtitle),
                        tone = DesignTone.Amber,
                        isFirst = first,
                        onClick = openProfiles,
                    )
                    first = false
                }
                DesignRow(
                    icon = Icons.Filled.PowerSettingsNew,
                    title = stringResource(R.string.dashboard_power_options),
                    subtitle = stringResource(R.string.dashboard_power_options_body),
                    tone = DesignTone.Neutral,
                    isFirst = first,
                    onClick = onOpenPowerOptions,
                )
            }
        },
        footer = {
            if (target == AppTarget.REMOTE) {
                if (device.isTv) {
                    OutlinedButton(onClick = onOpenTvLauncher, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                        Text(stringResource(R.string.dashboard_tv_launchers))
                    }
                }
            }
        },
    )
}

@Composable
private fun TelevisionDashboardContent(
    state: DashboardState,
    target: AppTarget,
    shizukuStatus: ShizukuLocalStatus,
    t: Translate,
    onRefresh: () -> Unit,
    onOptimize: () -> Unit,
    onOpenApps: () -> Unit,
    onRequestShizuku: () -> Unit,
    onRefreshShizuku: () -> Unit,
    onOpenTvLauncher: () -> Unit,
    onOpenRemote: () -> Unit,
    onOpenDetail: (DashboardCategory) -> Unit,
    onOpenTools: (() -> Unit)?,
    onOpenProfiles: (() -> Unit)? = null,
    localAdbReady: Boolean = false,
) {
    val device = state.device.takeIf { state.target == target }
    val metrics = state.metrics.takeIf { state.target == target }
    val unavailable = stringResource(R.string.dashboard_unavailable)
    val canOptimize = device != null && (target == AppTarget.REMOTE || shizukuStatus == ShizukuLocalStatus.READY || localAdbReady)
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // Hero del equipo, como en la maqueta de TV.
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerLow)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Box(
                    Modifier
                        .size(52.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        if (device?.isTv == true) "TV" else "AD",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        device?.displayName
                            ?: stringResource(
                                if (target == AppTarget.LOCAL) {
                                    R.string.dashboard_local_loading
                                } else {
                                    R.string.dashboard_remote_empty
                                },
                            ),
                        style = MaterialTheme.typography.titleLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        device?.let {
                            stringResource(R.string.dashboard_android_line, it.androidVersion, it.sdk, it.abi, "")
                                .trim()
                        } ?: stringResource(
                            if (target == AppTarget.LOCAL) {
                                R.string.dashboard_local_loading_body
                            } else {
                                R.string.dashboard_remote_empty_body
                            },
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            TvActionRow(
                primaryLabel = t("nav.optimize"),
                primaryEnabled = canOptimize && !state.loading,
                onPrimary = onOptimize,
                secondaryLabel = stringResource(R.string.tools_title),
                onSecondary = onOpenTools,
            )
        }

        state.error?.let { Text(it.resolve(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
        state.powerMessage?.let { Text(it.resolve(), style = MaterialTheme.typography.bodySmall) }

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            TvStatCard(
                label = stringResource(R.string.dashboard_cpu),
                value = metrics?.let { "${it.cpuBusyPercent.toInt()}%" } ?: unavailable,
                hint = metrics?.let { "Carga ${"%.2f".format(it.load1)}" },
                modifier = Modifier.weight(1f),
            )
            TvStatCard(
                label = stringResource(R.string.dashboard_ram_used),
                value = metrics?.takeIf { it.totalRamKb > 0 }?.let { "${(it.ramRatio * 100).toInt()}%" } ?: unavailable,
                hint = metrics?.takeIf { it.totalRamKb > 0 }?.let { stringResource(R.string.dashboard_of_total, formatKb(it.totalRamKb)) },
                modifier = Modifier.weight(1f),
            )
            TvStatCard(
                label = stringResource(R.string.dashboard_storage),
                value = metrics?.takeIf { it.storageTotalKb > 0 }?.let { "${(it.storageRatio * 100).toInt()}%" } ?: unavailable,
                hint = metrics?.takeIf { it.storageAvailableKb > 0 }?.let { formatKb(it.storageAvailableKb) },
                modifier = Modifier.weight(1f),
            )
        }

        TvSectionLabel(stringResource(R.string.dashboard_quick_access))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            TvTile(
                icon = Icons.Filled.Bolt,
                title = t("nav.optimize"),
                subtitle = stringResource(R.string.tv_dashboard_recipes),
                onClick = onOptimize,
                enabled = canOptimize,
                modifier = Modifier.weight(1f),
            )
            TvTile(
                icon = Icons.Filled.Tune,
                title = stringResource(R.string.tools_title),
                subtitle = stringResource(R.string.tv_dashboard_tools_subtitle),
                onClick = onOpenTools,
                modifier = Modifier.weight(1f),
            )
            TvTile(
                icon = Icons.Filled.Layers,
                title = stringResource(R.string.profiles_title),
                subtitle = stringResource(R.string.tv_dashboard_profiles_subtitle),
                onClick = onOpenProfiles,
                modifier = Modifier.weight(1f),
            )
        }

        if (target == AppTarget.REMOTE && device != null) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                TvTile(
                    icon = Icons.AutoMirrored.Filled.ScreenShare,
                    title = stringResource(R.string.dashboard_mirror_title),
                    subtitle = stringResource(R.string.tv_dashboard_mirror_subtitle),
                    onClick = onOpenRemote,
                    modifier = Modifier.weight(1f),
                )
                if (device.isTv) {
                    TvTile(
                        icon = Icons.Filled.Tv,
                        title = stringResource(R.string.dashboard_tv_launchers),
                        subtitle = stringResource(R.string.tv_dashboard_launchers_subtitle),
                        onClick = onOpenTvLauncher,
                        modifier = Modifier.weight(1f),
                    )
                }
                TvTile(
                    icon = Icons.Filled.Apps,
                    title = stringResource(R.string.dashboard_manage_apps),
                    subtitle = stringResource(R.string.tv_dashboard_apps_subtitle),
                    onClick = onOpenApps,
                    modifier = Modifier.weight(1f),
                )
            }
        } else if (target == AppTarget.LOCAL && shizukuStatus != ShizukuLocalStatus.READY) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                TvTile(
                    icon = Icons.Filled.Shield,
                    title = stringResource(R.string.dashboard_shizuku_permission_title),
                    subtitle = stringResource(R.string.tv_dashboard_shizuku_subtitle),
                    onClick = onRequestShizuku,
                    modifier = Modifier.weight(1f),
                )
                TvTile(
                    icon = Icons.Filled.Refresh,
                    title = stringResource(R.string.dashboard_refresh),
                    subtitle = stringResource(R.string.tv_dashboard_refresh_subtitle),
                    onClick = onRefreshShizuku,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            TvTile(
                icon = Icons.Filled.Apps,
                title = stringResource(R.string.dashboard_manage_apps),
                subtitle = stringResource(R.string.tv_dashboard_apps_subtitle),
                onClick = onOpenApps,
                modifier = Modifier.weight(1f),
            )
            TvTile(
                icon = Icons.Filled.Refresh,
                title = stringResource(R.string.tv_dashboard_refresh_title),
                subtitle = stringResource(R.string.tv_dashboard_refresh_subtitle),
                onClick = onRefresh,
                enabled = !state.loading,
                modifier = Modifier.weight(1f),
            )
        }
        if (device != null) {
            TvSectionLabel(stringResource(R.string.dashboard_explore))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                DashboardCategory.entries.take(3).forEach { category ->
                    TvTile(
                        icon = category.icon,
                        title = stringResource(category.label),
                        subtitle = stringResource(category.hint),
                        onClick = { onOpenDetail(category) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Preview(widthDp = 608, heightDp = 400, showBackground = true)
@Composable
private fun TelevisionDashboardContentPreview() {
    AdbOptimizerTheme {
        CompositionLocalProvider(LocalTelevisionUi provides true) {
            TelevisionDashboardContent(
                state =
                    DashboardState(
                        device = DeviceInfo("preview", "Android TV", "Demo", 34, "14", true, false, "arm64-v8a"),
                        metrics =
                            DeviceMetrics(
                                totalRamKb = 2048,
                                usedRamKb = 1024,
                                cpuBusyPercent = 24f,
                                storageTotalKb = 8192,
                                storageUsedKb = 2048,
                            ),
                    ),
                target = AppTarget.LOCAL,
                shizukuStatus = ShizukuLocalStatus.READY,
                t = { "Optimizar" },
                onRefresh = {},
                onOptimize = {},
                onOpenApps = {},
                onRequestShizuku = {},
                onRefreshShizuku = {},
                onOpenTvLauncher = {},
                onOpenRemote = {},
                onOpenDetail = {},
                onOpenTools = {},
            )
        }
    }
}

@Preview(widthDp = 608, heightDp = 400, showBackground = true)
@Composable
private fun TelevisionDashboardEmptyPreview() {
    AdbOptimizerTheme {
        TelevisionDashboardContent(
            state = DashboardState(target = AppTarget.REMOTE),
            target = AppTarget.REMOTE,
            shizukuStatus = ShizukuLocalStatus.READY,
            t = { "Optimizar" },
            onRefresh = {},
            onOptimize = {},
            onOpenApps = {},
            onRequestShizuku = {},
            onRefreshShizuku = {},
            onOpenTvLauncher = {},
            onOpenRemote = {},
            onOpenDetail = {},
            onOpenTools = {},
        )
    }
}

@Composable
private fun ToolsEntryCard(onClick: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.tools_title), style = MaterialTheme.typography.titleMedium)
                Text(
                    stringResource(R.string.tools_description),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null)
        }
    }
}

@Composable
private fun MirrorEntryCard(onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                modifier = Modifier.size(48.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.AutoMirrored.Filled.ScreenShare, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(stringResource(R.string.dashboard_mirror_title), style = MaterialTheme.typography.titleSmall)
                Text(
                    stringResource(R.string.dashboard_mirror_body),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
internal fun DashboardDetailScreen(
    category: DashboardCategory,
    state: DashboardState,
    target: AppTarget,
    shizukuStatus: ShizukuLocalStatus,
    onBack: () -> Unit,
) {
    BackHandler(onBack = onBack)
    val device = state.device.takeIf { state.target == target }
    val metrics = state.metrics.takeIf { state.target == target }
    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (!LocalTelevisionUi.current) {
                IconButton(onClick = onBack) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.dashboard_back_summary),
                    )
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    stringResource(category.label),
                    style = MaterialTheme.typography.headlineSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    stringResource(category.hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (device != null) {
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 24.dp),
            ) {
                DevicePlate(device)
                Spacer(Modifier.height(4.dp))
                DashboardCategoryContent(category, device, metrics, target, shizukuStatus)
            }
        } else {
            Text(
                stringResource(R.string.dashboard_metrics_error),
                Modifier.padding(24.dp),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

/** Cabecera del detalle: identidad compacta del equipo, como el "plate" del prototipo. */
@Composable
private fun DevicePlate(device: DeviceInfo) {
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
                "Android ${device.androidVersion} · SDK ${device.sdk} · ${device.abi}",
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
internal fun DeviceOverviewCard(
    device: DeviceInfo,
    loading: Boolean,
    onRefresh: () -> Unit,
    target: AppTarget = AppTarget.REMOTE,
    shizukuStatus: ShizukuLocalStatus = ShizukuLocalStatus.NOT_INSTALLED,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        stringResource(
                            if (target == AppTarget.LOCAL) {
                                R.string.dashboard_device_local_label
                            } else {
                                R.string.dashboard_device_remote_label
                            },
                        ),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text(device.displayName, style = MaterialTheme.typography.titleLarge)
                }
                IconButton(onClick = onRefresh, enabled = !loading) {
                    if (loading) {
                        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Filled.Refresh, contentDescription = stringResource(R.string.dashboard_refresh))
                    }
                }
            }
            HorizontalDivider()
            val unavailable = stringResource(R.string.dashboard_unavailable)
            DeviceInfoRow(stringResource(R.string.dashboard_manufacturer), device.manufacturer.ifBlank { unavailable })
            DeviceInfoRow(stringResource(R.string.dashboard_model), device.model.ifBlank { unavailable })
            DeviceInfoRow(
                stringResource(R.string.dashboard_android),
                "${device.androidVersion} · SDK ${device.sdk}",
            )
            DeviceInfoRow(stringResource(R.string.dashboard_abi), device.abi.ifBlank { unavailable })
            DeviceInfoRow(
                stringResource(R.string.dashboard_connection),
                if (target == AppTarget.LOCAL) stringResource(R.string.dashboard_local) else device.id,
            )
            DeviceInfoRow(
                stringResource(R.string.dashboard_access),
                when {
                    device.hasRoot -> {
                        stringResource(R.string.dashboard_root)
                    }

                    target == AppTarget.LOCAL && shizukuStatus == ShizukuLocalStatus.READY -> {
                        stringResource(R.string.dashboard_shizuku)
                    }

                    target == AppTarget.LOCAL -> {
                        stringResource(R.string.dashboard_shizuku_unauthorized)
                    }

                    else -> {
                        stringResource(R.string.dashboard_adb)
                    }
                },
            )
        }
    }
}

@Composable
internal fun DeviceInfoRow(
    label: String,
    value: String,
) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(label, Modifier.weight(0.8f), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, Modifier.weight(1.2f), style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
internal fun ShizukuStatusCard(
    status: ShizukuLocalStatus,
    onRequestPermission: () -> Unit,
    onRefresh: () -> Unit,
) {
    val (titleRes, descriptionRes, actionRes) =
        when (status) {
            ShizukuLocalStatus.NOT_INSTALLED -> {
                Triple(
                    R.string.dashboard_shizuku_not_installed_title,
                    R.string.dashboard_shizuku_not_installed_body,
                    R.string.dashboard_refresh,
                )
            }

            ShizukuLocalStatus.STOPPED -> {
                Triple(
                    R.string.dashboard_shizuku_stopped_title,
                    R.string.dashboard_shizuku_stopped_body,
                    R.string.dashboard_shizuku_check,
                )
            }

            ShizukuLocalStatus.PERMISSION_REQUIRED -> {
                Triple(
                    R.string.dashboard_shizuku_permission_title,
                    R.string.dashboard_shizuku_permission_body,
                    R.string.dashboard_shizuku_request,
                )
            }

            ShizukuLocalStatus.READY -> {
                Triple(
                    R.string.dashboard_shizuku_ready_title,
                    R.string.dashboard_shizuku_ready_body,
                    R.string.dashboard_refresh,
                )
            }
        }
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                stringResource(R.string.dashboard_permissions_eyebrow),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(stringResource(titleRes), style = MaterialTheme.typography.titleSmall)
            Text(stringResource(descriptionRes), style = MaterialTheme.typography.bodySmall)
            if (status != ShizukuLocalStatus.READY) {
                Button(onClick = if (status == ShizukuLocalStatus.PERMISSION_REQUIRED) onRequestPermission else onRefresh) {
                    Text(stringResource(actionRes))
                }
            }
        }
    }
}

@Composable
internal fun DeviceHeader(
    device: DeviceInfo,
    t: Translate,
) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(device.displayName, style = MaterialTheme.typography.headlineSmall)
        Text(
            stringResource(
                R.string.dashboard_android_line,
                device.androidVersion,
                device.sdk,
                device.abi,
                if (device.isTv) stringResource(R.string.dashboard_tv_suffix) else "",
            ),
            style = MaterialTheme.typography.bodySmall,
        )
        Text(
            stringResource(if (device.hasRoot) R.string.dashboard_root_detected else R.string.dashboard_shell_mode),
            style = MaterialTheme.typography.labelSmall,
            color = if (device.hasRoot) MaterialTheme.colorScheme.primary else AppColors.muted,
        )
    }
}

@Composable
internal fun HealthBanner(
    metrics: DeviceMetrics,
    t: Translate,
    buttonText: String,
    onAction: () -> Unit,
) {
    val level = metrics.healthLevel()
    val color =
        when (level) {
            HealthLevel.OK -> AppColors.accent
            HealthLevel.WARN -> AppColors.warning
            HealthLevel.BAD -> AppColors.danger
        }
    Card(colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.10f))) {
        Row(
            Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(t("device.health.${level.name.lowercase()}"), style = MaterialTheme.typography.titleMedium, color = color)
                Text(stringResource(R.string.dashboard_health_score, metrics.healthScore()), style = MaterialTheme.typography.bodySmall)
            }
            Button(
                onClick = onAction,
                colors = ButtonDefaults.buttonColors(containerColor = AppColors.accent, contentColor = MaterialTheme.colorScheme.onPrimary),
            ) {
                Text(buttonText, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
internal fun MetricCard(
    label: String,
    value: String,
    hint: String,
    ratio: Float,
    modifier: Modifier = Modifier,
) {
    Card(modifier = modifier, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(label.uppercase(), style = MaterialTheme.typography.labelSmall)
            Text(value, style = MaterialTheme.typography.headlineSmall)
            if (hint.isNotEmpty()) Text(hint, style = MaterialTheme.typography.bodySmall)
            if (ratio > 0f) {
                Spacer(Modifier.height(6.dp))
                val tone =
                    when {
                        ratio >= 0.85f -> AppColors.danger
                        ratio >= 0.65f -> AppColors.warning
                        else -> AppColors.accent
                    }
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(5.dp)
                        .clip(RoundedCornerShape(50))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth(ratio.coerceIn(0f, 1f))
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(50))
                            .background(tone),
                    )
                }
            }
        }
    }
}

// ---------------------------------------------------------------- Optimize
