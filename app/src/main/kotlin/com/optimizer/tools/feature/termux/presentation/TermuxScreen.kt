package com.optimizer.tools.feature.termux.presentation

import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.optimizer.tools.R
import com.optimizer.tools.app.tv.LocalTelevisionUi
import com.optimizer.tools.core.ui.AdbOptimizerTheme
import com.optimizer.tools.core.ui.DesignChip
import com.optimizer.tools.core.ui.DesignSectionLabel
import com.optimizer.tools.core.ui.PillButton
import com.optimizer.tools.core.ui.PillStyle
import com.optimizer.tools.core.ui.ScreenHeader
import com.optimizer.tools.core.ui.ScreenIconButton
import com.optimizer.tools.feature.termux.domain.TermuxAvailability
import com.optimizer.tools.feature.termux.domain.TermuxErrorKind

@Composable
fun TermuxScreen(
    viewModel: TermuxViewModel,
    onBack: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    // Los permisos peligrosos definidos por otra app no siempre abren su diálogo:
    // se explica el paso y se lleva directamente a "Permisos adicionales".
    var showPermissionHelp by remember { mutableStateOf(false) }
    val requestPermission: () -> Unit = { showPermissionHelp = true }

    LaunchedEffect(Unit) { viewModel.refresh() }
    // Al volver de Ajustes/Termux el permiso o la instalación pueden haber cambiado.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refresh() }

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        ScreenHeader(
            title = stringResource(R.string.termux_title),
            subtitle = stringResource(R.string.termux_subtitle),
            leading =
                if (LocalTelevisionUi.current) {
                    null
                } else {
                    {
                        ScreenIconButton(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            stringResource(R.string.action_back),
                            onBack,
                        )
                    }
                },
        )
        LazyColumn(
            Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(bottom = 24.dp),
        ) {
            item("setup") {
                IntegrationCard(
                    availability = state.availability,
                    onInstall = viewModel::openInstallPage,
                    onPermissions = requestPermission,
                    onOpenTermux = viewModel::openTermux,
                    onConfigureExternalApps = viewModel::configureExternalApps,
                    granting = state.granting,
                )
            }
            state.errorKind?.let { kind ->
                item("error") {
                    TermuxErrorCard(
                        kind = kind,
                        detail = state.errorDetail,
                        onInstall = viewModel::openInstallPage,
                        onPermissions = requestPermission,
                        onConfigureExternalApps = viewModel::configureExternalApps,
                        onRetry = viewModel::refresh,
                    )
                }
            }
            item("presets") {
                PresetSection(
                    command = state.command,
                    enabled = !state.running,
                    onPreset = viewModel::usePreset,
                )
            }
            item("command") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    DesignSectionLabel(stringResource(R.string.termux_command_label))
                    CommandField(
                        value = state.command,
                        running = state.running,
                        onValueChange = viewModel::setCommand,
                        onRun = viewModel::run,
                    )
                }
            }
            item("output-header") {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        stringResource(R.string.termux_output_label),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = viewModel::clearOutput, enabled = state.entries.isNotEmpty()) {
                        Text(stringResource(R.string.termux_clear))
                    }
                }
            }
            if (state.entries.isEmpty()) {
                item("empty") {
                    Text(
                        stringResource(R.string.termux_output_empty),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 4.dp),
                    )
                }
            } else {
                items(state.entries) { entry ->
                    TermuxOutputCard(entry)
                }
            }
        }
    }

    if (showPermissionHelp) {
        AlertDialog(
            onDismissRequest = { showPermissionHelp = false },
            title = { Text(stringResource(R.string.termux_permission_dialog_title)) },
            text = { Text(stringResource(R.string.termux_permission_dialog_body)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showPermissionHelp = false
                        viewModel.grantPermissionAutomatically()
                    },
                ) {
                    Text(stringResource(R.string.termux_permission_dialog_auto))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showPermissionHelp = false
                        viewModel.openAppSettings()
                    },
                ) {
                    Text(stringResource(R.string.termux_permission_dialog_open))
                }
            },
        )
    }
}

@Composable
private fun IntegrationCard(
    availability: TermuxAvailability,
    onInstall: () -> Unit,
    onPermissions: () -> Unit,
    onOpenTermux: () -> Unit,
    onConfigureExternalApps: () -> Unit,
    granting: Boolean,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val color =
                if (availability.ready) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.tertiary
                }
            Box(Modifier.size(8.dp).clip(CircleShape).background(color))
            Text(
                stringResource(if (availability.ready) R.string.termux_status_ready else R.string.termux_status_pending),
                style = MaterialTheme.typography.titleSmall,
                color = color,
            )
        }
        CheckRow(
            label = stringResource(R.string.termux_check_installed),
            ok = availability.installed,
            detail = availability.versionName?.let { stringResource(R.string.termux_version, it) },
        )
        CheckRow(
            label = stringResource(R.string.termux_check_permission),
            ok = availability.permissionGranted,
        )
        CheckRow(
            label = stringResource(R.string.termux_check_api),
            ok = availability.apiInstalled,
            optional = true,
        )
        Text(
            stringResource(R.string.termux_note_external),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (!availability.installed) {
            PillButton(
                text = stringResource(R.string.termux_action_install),
                onClick = onInstall,
                style = PillStyle.Solid,
                modifier = Modifier.fillMaxWidth(),
            )
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (!availability.permissionGranted) {
                    if (granting) {
                        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                        }
                    } else {
                        PillButton(
                            text = stringResource(R.string.termux_action_permissions),
                            onClick = onPermissions,
                            style = PillStyle.Solid,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
                PillButton(
                    text = stringResource(R.string.termux_action_open),
                    onClick = onOpenTermux,
                    style = if (availability.permissionGranted) PillStyle.Solid else PillStyle.Outline,
                    modifier = Modifier.weight(1f),
                )
            }
            PillButton(
                text = stringResource(R.string.termux_action_external),
                onClick = onConfigureExternalApps,
                style = PillStyle.Outline,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun CheckRow(
    label: String,
    ok: Boolean,
    detail: String? = null,
    optional: Boolean = false,
) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(
            Modifier
                .size(18.dp)
                .clip(CircleShape)
                .background(
                    if (ok) {
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
                    } else {
                        MaterialTheme.colorScheme.outlineVariant
                    },
                ),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                if (ok) "✓" else "·",
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                color = if (ok) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color =
                if (ok || optional) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            modifier = Modifier.weight(1f),
        )
        detail?.let {
            Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun TermuxErrorCard(
    kind: TermuxErrorKind,
    detail: String?,
    onInstall: () -> Unit,
    onPermissions: () -> Unit,
    onConfigureExternalApps: () -> Unit,
    onRetry: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            stringResource(kind.titleRes()),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.error,
        )
        Text(
            stringResource(kind.bodyRes()),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        detail?.takeIf { it.isNotBlank() }?.let {
            Text(
                it,
                style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
        }
        when (kind) {
            TermuxErrorKind.NOT_INSTALLED -> {
                PillButton(
                    text = stringResource(R.string.termux_action_install),
                    onClick = onInstall,
                    style = PillStyle.Solid,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            TermuxErrorKind.PERMISSION_DENIED -> {
                PillButton(
                    text = stringResource(R.string.termux_action_permissions),
                    onClick = onPermissions,
                    style = PillStyle.Solid,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            TermuxErrorKind.EXTERNAL_APPS_DISABLED -> {
                PillButton(
                    text = stringResource(R.string.termux_action_external),
                    onClick = onConfigureExternalApps,
                    style = PillStyle.Solid,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            else -> {
                TextButton(onClick = onRetry) {
                    Text(stringResource(R.string.termux_retry))
                }
            }
        }
    }
}

private fun TermuxErrorKind.titleRes(): Int =
    when (this) {
        TermuxErrorKind.NOT_INSTALLED -> R.string.termux_error_not_installed_title
        TermuxErrorKind.PERMISSION_DENIED -> R.string.termux_error_permission_title
        TermuxErrorKind.EXTERNAL_APPS_DISABLED -> R.string.termux_error_external_title
        TermuxErrorKind.START_FAILED -> R.string.termux_error_start_title
        TermuxErrorKind.TIMEOUT -> R.string.termux_error_timeout_title
        TermuxErrorKind.UNKNOWN -> R.string.termux_error_unknown_title
    }

private fun TermuxErrorKind.bodyRes(): Int =
    when (this) {
        TermuxErrorKind.NOT_INSTALLED -> R.string.termux_error_not_installed_body
        TermuxErrorKind.PERMISSION_DENIED -> R.string.termux_error_permission_body
        TermuxErrorKind.EXTERNAL_APPS_DISABLED -> R.string.termux_error_external_body
        TermuxErrorKind.START_FAILED -> R.string.termux_error_start_body
        TermuxErrorKind.TIMEOUT -> R.string.termux_error_timeout_body
        TermuxErrorKind.UNKNOWN -> R.string.termux_error_unknown_body
    }

@Composable
private fun PresetSection(
    command: String,
    enabled: Boolean,
    onPreset: (TermuxPreset) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        DesignSectionLabel(stringResource(R.string.termux_presets))
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            TermuxPreset.entries.forEach { preset ->
                DesignChip(
                    text = stringResource(preset.titleRes),
                    selected = command == preset.command,
                    onClick = { if (enabled) onPreset(preset) },
                )
            }
        }
    }
}

@Composable
private fun CommandField(
    value: String,
    running: Boolean,
    onValueChange: (String) -> Unit,
    onRun: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(start = 14.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle =
                MaterialTheme.typography.bodyMedium.copy(
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurface,
                ),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            modifier = Modifier.weight(1f).padding(vertical = 12.dp),
            decorationBox = { inner ->
                Box {
                    if (value.isEmpty()) {
                        Text(
                            stringResource(R.string.termux_command_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    inner()
                }
            },
        )
        IconButton(onClick = onRun, enabled = value.isNotBlank() && !running) {
            if (running) {
                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
            } else {
                Icon(
                    Icons.Filled.PlayArrow,
                    contentDescription = stringResource(R.string.termux_run),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

@Composable
private fun TermuxOutputCard(entry: TermuxEntry) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                "$ ${entry.command}",
                style =
                    MaterialTheme.typography.bodySmall.copy(
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.SemiBold,
                    ),
                modifier = Modifier.weight(1f),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            TermuxExitBadge(entry.exitCode)
            Text(
                stringResource(R.string.termux_duration, entry.durationMs),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (entry.stdout.isNotBlank()) {
            SelectionContainer {
                Text(
                    entry.stdout.trimEnd(),
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace, fontSize = 12.sp),
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
        if (entry.stderr.isNotBlank()) {
            SelectionContainer {
                Text(
                    entry.stderr.trimEnd(),
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace, fontSize = 12.sp),
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
        if (entry.truncated) {
            Text(
                stringResource(R.string.termux_truncated),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.tertiary,
            )
        }
        hintsFor(entry.stdout + "\n" + entry.stderr).forEach { hint ->
            Text(
                stringResource(hint.messageRes),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.tertiary,
            )
        }
    }
}

@Composable
private fun TermuxExitBadge(exitCode: Int) {
    val color =
        if (exitCode == 0) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.error
        }
    Box(
        Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(color.copy(alpha = 0.14f))
            .padding(horizontal = 7.dp, vertical = 2.dp),
    ) {
        Text(
            if (exitCode == 0) {
                stringResource(R.string.termux_exit_ok)
            } else {
                stringResource(R.string.termux_exit_error, exitCode)
            },
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, fontWeight = FontWeight.Bold),
            color = color,
        )
    }
}

@Preview(name = "Termux sin instalar", widthDp = 360, heightDp = 780)
@Composable
private fun TermuxScreenPreview() {
    AdbOptimizerTheme {
        Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            IntegrationCard(
                availability = TermuxAvailability(),
                onInstall = {},
                onPermissions = {},
                onOpenTermux = {},
                onConfigureExternalApps = {},
                granting = false,
            )
            PresetSection(command = "", enabled = true, onPreset = {})
            Spacer(Modifier.height(4.dp))
            TermuxOutputCard(
                TermuxEntry(
                    command = "pkg install -y htop",
                    stdout = "Installing htop...\nDone",
                    stderr = "",
                    exitCode = 0,
                    truncated = false,
                    durationMs = 4_200,
                ),
            )
        }
    }
}
