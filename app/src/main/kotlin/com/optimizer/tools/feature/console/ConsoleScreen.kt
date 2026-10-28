package com.optimizer.tools.feature.console

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.optimizer.tools.R
import com.optimizer.tools.core.ui.ToolEmptyState
import com.optimizer.tools.core.ui.ToolTopBar
import com.optimizer.tools.core.ui.resolve

private val QUICK_COMMANDS =
    listOf(
        "getprop ro.product.model",
        "dumpsys battery",
        "pm list packages -3",
        "df -h /data",
        "uptime",
        "dumpsys meminfo | head -20",
    )

@Composable
internal fun ConsoleScreen(
    state: ConsoleState,
    onBack: () -> Unit,
    onCommand: (String) -> Unit,
    onRun: () -> Unit,
    onUseHistory: (String) -> Unit,
    onClear: () -> Unit,
    onDismissMessage: () -> Unit,
    onDismissError: () -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        ToolTopBar(
            title = stringResource(R.string.console_title),
            subtitle = stringResource(R.string.console_subtitle),
            onBack = onBack,
            actions = {
                IconButton(onClick = onClear, enabled = state.entries.isNotEmpty()) {
                    Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.console_clear))
                }
            },
        )
        if (!state.deviceAvailable) {
            ToolEmptyState(
                icon = Icons.Filled.Terminal,
                title = stringResource(R.string.console_no_device_title),
                description = stringResource(R.string.console_no_device),
            )
            return@Column
        }
        Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
            state.error?.let { error ->
                Notice(error.resolve(), MaterialTheme.colorScheme.error, onDismissError)
            }
            state.message?.let { message ->
                Notice(message.resolve(), MaterialTheme.colorScheme.primary, onDismissMessage)
            }
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                QUICK_COMMANDS.forEach { command ->
                    AssistChip(
                        onClick = { onUseHistory(command) },
                        label = { Text(command.take(26), style = MaterialTheme.typography.labelSmall) },
                    )
                }
            }
            if (state.history.isNotEmpty()) {
                Text(
                    stringResource(R.string.console_history),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    state.history.take(6).forEach { command ->
                        TextButton(
                            onClick = { onUseHistory(command) },
                            contentPadding =
                                androidx.compose.foundation.layout
                                    .PaddingValues(horizontal = 8.dp),
                        ) {
                            Text(command.take(24), style = MaterialTheme.typography.labelSmall, maxLines = 1)
                        }
                    }
                }
            }
            OutlinedTextField(
                value = state.command,
                onValueChange = onCommand,
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.console_command)) },
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
            )
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = onRun,
                    enabled = state.command.isNotBlank() && !state.running,
                    modifier = Modifier.heightIn(min = 44.dp),
                ) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.size(6.dp))
                    Text(stringResource(R.string.console_run))
                }
                Text(
                    if (state.running) stringResource(R.string.console_running) else stringResource(R.string.console_timeout),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                state.entries.forEach { entry ->
                    EntryCard(entry)
                }
                Spacer(Modifier.height(16.dp))
            }
        }
    }
}

@Composable
private fun EntryCard(entry: ConsoleEntry) {
    Card(
        Modifier.fillMaxWidth().padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                "$ ${entry.command}",
                style = MaterialTheme.typography.bodyMedium,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.SemiBold,
            )
            val body =
                entry.stdout
                    .ifBlank { entry.stderr }
                    .trim()
                    .ifBlank { stringResource(R.string.console_empty_output) }
            Text(
                body,
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
            )
            Text(
                stringResource(R.string.console_exit, entry.exitCode, entry.durationMs),
                style = MaterialTheme.typography.labelSmall,
                color = if (entry.exitCode == 0) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error,
            )
        }
    }
}

@Composable
private fun Notice(
    text: String,
    tint: androidx.compose.ui.graphics.Color,
    onDismiss: () -> Unit,
) {
    Card(
        Modifier.fillMaxWidth().padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = tint.copy(alpha = 0.12f)),
        shape = RoundedCornerShape(10.dp),
    ) {
        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(text, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_close)) }
        }
    }
}
