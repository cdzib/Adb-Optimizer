package com.optimizer.tools.feature.localadb.presentation

import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.optimizer.tools.R
import com.optimizer.tools.app.tv.LocalTelevisionUi
import com.optimizer.tools.core.ui.DesignChip
import com.optimizer.tools.core.ui.DesignSectionLabel
import com.optimizer.tools.core.ui.PillButton
import com.optimizer.tools.core.ui.PillStyle
import com.optimizer.tools.core.ui.ScreenHeader
import com.optimizer.tools.core.ui.ScreenIconButton
import com.optimizer.tools.feature.localadb.domain.LocalAdbErrorKind
import com.optimizer.tools.feature.localadb.domain.LocalAdbPhase

private val QuickCommands =
    listOf(
        "pm list packages -3",
        "settings get global adb_enabled",
        "wm size",
        "dumpsys deviceidle get deep",
    )

@Composable
fun LocalAdbScreen(
    viewModel: LocalAdbViewModel,
    onBack: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LaunchedEffect(Unit) { viewModel.onResume() }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.onResume() }

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        ScreenHeader(
            title = stringResource(R.string.localadb_title),
            subtitle = stringResource(R.string.localadb_subtitle),
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
        StatusStrip(state)
        Spacer(Modifier.height(10.dp))
        when (state.phase) {
            LocalAdbPhase.DEBUGGING_OFF -> {
                DebuggingOffContent(
                    developerOptionsEnabled = state.developerOptionsEnabled,
                    onOpenDeviceInfo = { openDeviceInfoSettings(context) },
                    onOpenDeveloperOptions = { openDeveloperSettings(context) },
                    onRetry = viewModel::retry,
                )
            }

            LocalAdbPhase.CONNECTING,
            LocalAdbPhase.WAITING_AUTHORIZATION,
            -> {
                WaitingContent(state)
            }

            LocalAdbPhase.ERROR -> {
                ErrorContent(
                    state = state,
                    onRetry = viewModel::retry,
                    onOpenSettings = { openDeveloperSettings(context) },
                )
            }

            LocalAdbPhase.CONNECTED -> {
                ConnectedContent(
                    state = state,
                    modifier = Modifier.weight(1f),
                    onCommand = viewModel::setCommand,
                    onRun = viewModel::run,
                    onClear = viewModel::clearOutput,
                )
            }
        }
        if (state.log.isNotEmpty() && state.phase != LocalAdbPhase.CONNECTED) {
            // El registro queda anclado al pie: el contenido explica el estado y el
            // registro dice que se intento y donde fallo.
            Spacer(Modifier.weight(1f))
            LocalAdbLogPanel(state.log)
            Spacer(Modifier.height(12.dp))
        }
    }
}

/**
 * Registro de la ultima conexion: que se probo, que respondio el puerto y donde fallo.
 * Se oculta cuando la sesion ya esta establecida.
 */
@Composable
private fun LocalAdbLogPanel(lines: List<String>) {
    val scroll = rememberScrollState()
    LaunchedEffect(lines.size) { scroll.scrollTo(Int.MAX_VALUE) }
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            stringResource(R.string.localadb_log_title),
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Column(
            Modifier.heightIn(max = 140.dp).verticalScroll(scroll),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            lines.takeLast(MAX_LOG_LINES_VISIBLE).forEach { line ->
                SelectionContainer {
                    Text(
                        line,
                        style =
                            MaterialTheme.typography.bodySmall.copy(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                            ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

private const val MAX_LOG_LINES_VISIBLE = 20

@Composable
private fun StatusStrip(state: LocalAdbUiState) {
    val (label, color) =
        when (state.phase) {
            LocalAdbPhase.DEBUGGING_OFF -> stringResource(R.string.localadb_status_off) to MaterialTheme.colorScheme.onSurfaceVariant
            LocalAdbPhase.CONNECTING -> stringResource(R.string.localadb_status_connecting) to MaterialTheme.colorScheme.tertiary
            LocalAdbPhase.WAITING_AUTHORIZATION -> stringResource(R.string.localadb_status_waiting) to MaterialTheme.colorScheme.tertiary
            LocalAdbPhase.CONNECTED -> stringResource(R.string.localadb_status_connected) to MaterialTheme.colorScheme.primary
            LocalAdbPhase.ERROR -> stringResource(R.string.localadb_status_error) to MaterialTheme.colorScheme.error
        }
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(color))
        Text(
            label,
            style = MaterialTheme.typography.labelLarge.copy(fontSize = 13.sp),
            color = color,
            maxLines = 1,
        )
        Box(Modifier.weight(1f))
        Text(
            "127.0.0.1:5555",
            style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun DebuggingOffContent(
    developerOptionsEnabled: Boolean,
    onOpenDeviceInfo: () -> Unit,
    onOpenDeveloperOptions: () -> Unit,
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
            stringResource(
                if (developerOptionsEnabled) {
                    R.string.localadb_off_title_adb
                } else {
                    R.string.localadb_off_title_dev
                },
            ),
            style = MaterialTheme.typography.titleSmall,
        )
        Text(
            stringResource(
                if (developerOptionsEnabled) {
                    R.string.localadb_off_body_adb
                } else {
                    R.string.localadb_off_body_dev
                },
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(2.dp))
        SetupStep(1, stringResource(R.string.localadb_step_1))
        SetupStep(2, stringResource(R.string.localadb_step_2))
        SetupStep(3, stringResource(R.string.localadb_step_3))
        SetupStep(4, stringResource(R.string.localadb_step_4))
        Text(
            stringResource(
                if (developerOptionsEnabled) {
                    R.string.localadb_dev_options_on
                } else {
                    R.string.localadb_dev_options_off
                },
            ),
            style = MaterialTheme.typography.labelSmall,
            color =
                if (developerOptionsEnabled) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.tertiary
                },
            modifier = Modifier.padding(top = 2.dp),
        )
        PillButton(
            text =
                stringResource(
                    if (developerOptionsEnabled) {
                        R.string.localadb_open_dev_settings
                    } else {
                        R.string.localadb_open_device_info
                    },
                ),
            onClick = if (developerOptionsEnabled) onOpenDeveloperOptions else onOpenDeviceInfo,
            style = PillStyle.Solid,
            modifier = Modifier.fillMaxWidth(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PillButton(
                text = stringResource(R.string.localadb_open_device_info),
                onClick = onOpenDeviceInfo,
                style = PillStyle.Outline,
                modifier = Modifier.weight(1f),
            )
            PillButton(
                text = stringResource(R.string.localadb_open_dev_settings),
                onClick = onOpenDeveloperOptions,
                style = PillStyle.Outline,
                modifier = Modifier.weight(1f),
            )
        }
        Text(
            stringResource(R.string.localadb_off_auto_retry),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        TextButton(onClick = onRetry) { Text(stringResource(R.string.localadb_retry)) }
    }
}

@Composable
private fun SetupStep(
    number: Int,
    text: String,
) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(
            Modifier
                .size(22.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                number.toString(),
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Text(text, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun WaitingContent(state: LocalAdbUiState) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 2.dp)
        Text(stringResource(R.string.localadb_waiting_title), style = MaterialTheme.typography.titleSmall)
        Text(
            stringResource(R.string.localadb_waiting_body),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (state.attempt > 0) {
            Text(
                stringResource(R.string.localadb_attempt, state.attempt, 3),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.tertiary,
            )
        }
    }
}

@Composable
private fun ErrorContent(
    state: LocalAdbUiState,
    onRetry: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val kind = state.errorKind ?: LocalAdbErrorKind.UNKNOWN
    val title =
        when (kind) {
            LocalAdbErrorKind.PORT_CLOSED -> R.string.localadb_error_port_title
            LocalAdbErrorKind.AUTHORIZATION -> R.string.localadb_error_auth_title
            LocalAdbErrorKind.DISCONNECTED -> R.string.localadb_error_disconnected_title
            LocalAdbErrorKind.TIMEOUT -> R.string.localadb_error_unknown_title
            LocalAdbErrorKind.UNKNOWN -> R.string.localadb_error_unknown_title
        }
    val body =
        when (kind) {
            LocalAdbErrorKind.PORT_CLOSED -> R.string.localadb_error_port_body
            LocalAdbErrorKind.AUTHORIZATION -> R.string.localadb_error_auth_body
            LocalAdbErrorKind.DISCONNECTED -> R.string.localadb_error_disconnected_body
            LocalAdbErrorKind.TIMEOUT -> R.string.localadb_error_unknown_body
            LocalAdbErrorKind.UNKNOWN -> R.string.localadb_error_unknown_body
        }
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(stringResource(title), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.error)
        Text(
            stringResource(body),
            style =
                MaterialTheme.typography.bodySmall.copy(
                    fontFamily =
                        if (kind ==
                            LocalAdbErrorKind.PORT_CLOSED
                        ) {
                            FontFamily.Monospace
                        } else {
                            FontFamily.Default
                        },
                ),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        state.detail?.takeIf { it.isNotBlank() }?.let {
            Text(
                it,
                style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PillButton(
                text = stringResource(R.string.localadb_retry),
                onClick = onRetry,
                style = PillStyle.Solid,
                modifier = Modifier.weight(1f),
            )
            PillButton(
                text = stringResource(R.string.localadb_open_dev_settings),
                onClick = onOpenSettings,
                style = PillStyle.Outline,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun ConnectedContent(
    state: LocalAdbUiState,
    modifier: Modifier = Modifier,
    onCommand: (String) -> Unit,
    onRun: () -> Unit,
    onClear: () -> Unit,
) {
    Column(modifier.fillMaxWidth()) {
        Text(
            stringResource(R.string.localadb_connected_note),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(bottom = 8.dp),
        )
        DesignSectionLabel(stringResource(R.string.localadb_quick))
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            QuickCommands.forEach { command ->
                DesignChip(
                    text = command,
                    selected = state.command == command,
                    onClick = { onCommand(command) },
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        CommandField(
            value = state.command,
            running = state.running,
            onValueChange = onCommand,
            onRun = onRun,
        )
        Row(
            Modifier.fillMaxWidth().padding(top = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                stringResource(R.string.localadb_output),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onClear, enabled = state.entries.isNotEmpty()) {
                Text(stringResource(R.string.localadb_clear))
            }
        }
        if (state.entries.isEmpty()) {
            Text(
                stringResource(R.string.localadb_output_empty),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
        } else {
            LazyColumn(
                Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding =
                    androidx.compose.foundation.layout
                        .PaddingValues(bottom = 24.dp),
            ) {
                items(state.entries) { entry -> OutputCard(entry) }
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
                            stringResource(R.string.localadb_command_hint),
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
                    contentDescription = stringResource(R.string.localadb_run),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

@Composable
private fun OutputCard(entry: LocalAdbEntry) {
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
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.SemiBold),
                modifier = Modifier.weight(1f),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            ExitBadge(entry.exitCode)
            Text(
                stringResource(R.string.localadb_duration, entry.durationMs),
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
    }
}

@Composable
private fun ExitBadge(exitCode: Int) {
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
                stringResource(R.string.localadb_exit_ok)
            } else {
                stringResource(R.string.localadb_exit_error, exitCode)
            },
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, fontWeight = FontWeight.Bold),
            color = color,
        )
    }
}

private fun openDeveloperSettings(context: Context) {
    val primary = Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)
    val opened = runCatching { context.startActivity(primary) }.isSuccess
    if (!opened) openDeviceInfoSettings(context)
}

private fun openDeviceInfoSettings(context: Context) {
    val deviceInfo = Intent(Settings.ACTION_DEVICE_INFO_SETTINGS)
    val opened = runCatching { context.startActivity(deviceInfo) }.isSuccess
    if (!opened) runCatching { context.startActivity(Intent(Settings.ACTION_SETTINGS)) }
}
