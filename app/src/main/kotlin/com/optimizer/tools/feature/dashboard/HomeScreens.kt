@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.optimizer.tools.feature.dashboard

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
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
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.Refresh
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
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.optimizer.tools.R
import com.optimizer.tools.core.ui.AppColors
import com.optimizer.tools.core.ui.Translate
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
) {
    val device = state.device.takeIf { state.target == target }
    val metrics = state.metrics.takeIf { state.target == target }

    state.pendingPowerAction?.takeIf { it.target == target }?.let {
        DevicePowerConfirmation(it, onConfirmPowerAction, onDismissPowerAction)
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
            if (metrics != null && (target == AppTarget.REMOTE || shizukuStatus == ShizukuLocalStatus.READY)) {
                {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        HealthBanner(metrics, t, t("optimize.title"), onOptimize)
                        if (target == AppTarget.REMOTE) MirrorEntryCard(onOpenRemote)
                    }
                }
            } else {
                null
            },
        footer = {
            DevicePowerContent(
                state,
                target == AppTarget.REMOTE || shizukuStatus == ShizukuLocalStatus.READY,
                onRequestPowerAction,
            )
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
    Scaffold(
        // OptimizerApp already applies the system bar insets around the NavHost.
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                windowInsets = WindowInsets(0, 0, 0, 0),
                title = { Text(stringResource(category.label)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.dashboard_back_summary))
                    }
                },
            )
        },
    ) { insets ->
        if (device != null) {
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(insets)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp, vertical = 16.dp),
            ) {
                Text(device.displayName, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(16.dp))
                DashboardCategoryContent(category, device, metrics, target, shizukuStatus)
            }
        } else {
            Text(
                stringResource(R.string.dashboard_metrics_error),
                Modifier.padding(insets).padding(24.dp),
                style = MaterialTheme.typography.bodyMedium,
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
