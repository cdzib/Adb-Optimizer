@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.optimizer.tools.feature.dpc

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.optimizer.tools.R
import com.optimizer.tools.core.ui.AdbOptimizerTheme
import com.optimizer.tools.core.ui.QrImage
import com.optimizer.tools.core.ui.UiText
import com.optimizer.tools.core.ui.resolve
import com.optimizer.tools.domain.dpc.DpcCheckStatus
import com.optimizer.tools.domain.dpc.DpcMode
import com.optimizer.tools.domain.dpc.DpcReceiver

@Composable
internal fun DpcScreen(
    state: DpcState,
    onBack: () -> Unit,
    onMode: (DpcMode) -> Unit,
    onPreset: (Int) -> Unit,
    onComponent: (String) -> Unit,
    onUrl: (String) -> Unit,
    onRefresh: () -> Unit,
    onDetectReceivers: () -> Unit,
    onUseReceiver: (DpcReceiver) -> Unit,
    onInstallLocal: (List<Uri>) -> Unit,
    onInstallUrl: () -> Unit,
    onSetOwner: () -> Unit,
    onRemoveOwner: () -> Unit,
    onGenerateQr: () -> Unit,
    onDismissMessage: () -> Unit,
) {
    val apkPicker =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
            if (uris.isNotEmpty()) onInstallLocal(uris)
        }
    val clipboard = LocalClipboardManager.current
    var showAdvanced by rememberSaveable { mutableStateOf(false) }
    var showQr by rememberSaveable { mutableStateOf(false) }
    var ownerAction by rememberSaveable { mutableStateOf<String?>(null) }
    val modeLabel =
        stringResource(
            if (state.mode == DpcMode.DEVICE) R.string.dpc_device_owner else R.string.dpc_profile_owner,
        )
    val setOwnerCommand =
        "dpm set-${if (state.mode == DpcMode.DEVICE) "device" else "profile"}-owner --user 0 ${state.component}"

    if (ownerAction != null) {
        val removing = ownerAction == "remove"
        AlertDialog(
            onDismissRequest = { ownerAction = null },
            title = {
                Text(
                    stringResource(
                        if (removing) {
                            R.string.dpc_remove_owner_title
                        } else {
                            R.string.dpc_assign_owner_title
                        },
                        modeLabel,
                    ),
                )
            },
            text = {
                Text(
                    stringResource(
                        if (removing) {
                            R.string.dpc_remove_owner_body
                        } else {
                            R.string.dpc_assign_owner_body
                        },
                        state.component.substringBefore('/'),
                    ),
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    ownerAction = null
                    if (removing) onRemoveOwner() else onSetOwner()
                }, enabled = !state.busy) {
                    Text(
                        stringResource(
                            if (removing) {
                                R.string.dpc_remove_owner_action
                            } else {
                                R.string.dpc_assign_owner_action
                            },
                        ),
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { ownerAction = null }) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(top = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack, modifier = Modifier.offset(x = (-12).dp)) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
            }
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.dpc_title), style = MaterialTheme.typography.titleLarge)
                Text(
                    stringResource(R.string.dpc_subtitle),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        DpcStatusCard(state = state, modeLabel = modeLabel, onRefresh = onRefresh)

        if (state.busy || state.message != null) {
            DpcFeedbackCard(
                busy = state.busy,
                progress = state.progress,
                message = state.message,
                onDismissMessage = onDismissMessage,
            )
        }

        DpcSection(
            number = "1",
            title = stringResource(R.string.dpc_section_controller),
            subtitle = stringResource(R.string.dpc_section_controller_body),
        ) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                defaultDpcPresets.forEachIndexed { index, preset ->
                    FilterChip(
                        selected = state.presetIndex == index && state.component == preset.component,
                        enabled = !state.busy,
                        onClick = { onPreset(index) },
                        label = { Text(preset.label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    )
                }
            }
            OutlinedTextField(
                value = state.component,
                onValueChange = onComponent,
                label = { Text(stringResource(R.string.dpc_component_label)) },
                singleLine = true,
                enabled = !state.busy,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedButton(
                onClick = onDetectReceivers,
                enabled = !state.busy,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
            ) { Text(stringResource(R.string.dpc_detect_receivers)) }
            if (state.receivers.isNotEmpty()) {
                state.receivers.forEach { receiver ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            receiver.component,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.weight(1f),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        TextButton(onClick = { onUseReceiver(receiver) }, enabled = !state.busy) {
                            Text(stringResource(R.string.dpc_use))
                        }
                    }
                }
            }
        }

        DpcSection(
            number = "2",
            title = stringResource(R.string.dpc_section_install),
            subtitle = stringResource(R.string.dpc_section_install_body),
        ) {
            OutlinedButton(
                onClick = { apkPicker.launch(arrayOf("application/vnd.android.package-archive")) },
                enabled = !state.busy,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
            ) { Text(stringResource(R.string.dpc_install_apk)) }
        }

        DpcSection(
            number = "3",
            title = stringResource(R.string.dpc_section_checks),
            subtitle = stringResource(R.string.dpc_section_checks_body),
        ) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DpcMode.entries.forEach { mode ->
                    FilterChip(
                        selected = state.mode == mode,
                        enabled = !state.busy,
                        onClick = { onMode(mode) },
                        label = {
                            Text(
                                stringResource(
                                    if (mode == DpcMode.DEVICE) {
                                        R.string.dpc_device_owner
                                    } else {
                                        R.string.dpc_profile_owner
                                    },
                                ),
                            )
                        },
                        modifier = Modifier.padding(end = 8.dp),
                    )
                }
            }
            state.preflight?.let { preflight ->
                preflight.checks.forEach { check ->
                    DpcCheckRow(
                        label = check.label,
                        detail = check.detail,
                        status = check.status,
                    )
                }
                Text(
                    stringResource(
                        R.string.dpc_owners,
                        if (preflight.owners.isEmpty()) {
                            stringResource(R.string.dpc_none)
                        } else {
                            preflight.owners.joinToString()
                        },
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } ?: Text(
                stringResource(R.string.dpc_no_checks),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        DpcSection(
            number = "4",
            title = stringResource(R.string.dpc_section_activate),
            subtitle = stringResource(R.string.dpc_section_activate_body),
        ) {
            Button(
                onClick = { ownerAction = "assign" },
                enabled = !state.busy,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
            ) {
                if (state.busy) {
                    CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                }
                Text(
                    stringResource(
                        if (state.busy) {
                            R.string.dpc_working
                        } else {
                            R.string.dpc_assign
                        },
                        modeLabel,
                    ),
                )
            }
        }

        HorizontalDivider()
        TextButton(onClick = { showAdvanced = !showAdvanced }, modifier = Modifier.fillMaxWidth()) {
            Text(
                stringResource(
                    if (showAdvanced) R.string.dpc_hide_advanced else R.string.dpc_show_advanced,
                ),
            )
        }
        if (showAdvanced) {
            DpcSection(
                number = "",
                title = stringResource(R.string.dpc_section_remote_install),
                subtitle = stringResource(R.string.dpc_section_remote_install_body),
            ) {
                OutlinedTextField(
                    value = state.url,
                    onValueChange = onUrl,
                    enabled = !state.busy,
                    label = { Text(stringResource(R.string.dpc_apk_url)) },
                    placeholder = { Text(stringResource(R.string.dpc_apk_url_placeholder)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedButton(
                    onClick = onInstallUrl,
                    enabled = !state.busy && state.url.isNotBlank(),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.dpc_download_install))
                }
            }
            DpcSection(
                number = "",
                title = stringResource(R.string.dpc_section_command),
                subtitle = stringResource(R.string.dpc_section_command_body),
            ) {
                SelectionContainer { Text(setOwnerCommand, style = MaterialTheme.typography.bodySmall) }
                TextButton(onClick = { clipboard.setText(AnnotatedString(setOwnerCommand)) }) {
                    Text(stringResource(R.string.dpc_copy_command))
                }
            }
            DpcSection(
                number = "",
                title = stringResource(R.string.dpc_section_remove),
                subtitle = stringResource(R.string.dpc_section_remove_body),
            ) {
                OutlinedButton(
                    onClick = { ownerAction = "remove" },
                    enabled = !state.busy,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                ) { Text(stringResource(R.string.dpc_remove_owner_action)) }
            }
        }

        TextButton(onClick = { showQr = !showQr }, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(if (showQr) R.string.dpc_hide_qr else R.string.dpc_show_qr))
        }
        if (showQr) {
            DpcSection(
                number = "",
                title = stringResource(R.string.dpc_section_qr),
                subtitle = stringResource(R.string.dpc_section_qr_body),
            ) {
                Text(
                    stringResource(R.string.dpc_qr_body),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = state.url,
                    onValueChange = onUrl,
                    enabled = !state.busy,
                    label = { Text(stringResource(R.string.dpc_public_apk_url)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedButton(
                    onClick = onGenerateQr,
                    enabled = !state.busy && state.url.isNotBlank(),
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                ) { Text(stringResource(R.string.dpc_generate_qr)) }
                state.qrPayload?.let { payload ->
                    QrImage(text = payload, modifier = Modifier.fillMaxWidth().heightIn(min = 220.dp))
                    Text(
                        stringResource(R.string.dpc_checksum, state.checksum.orEmpty()),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(payload, style = MaterialTheme.typography.bodySmall)
                    TextButton(onClick = { clipboard.setText(AnnotatedString(payload)) }) {
                        Text(stringResource(R.string.dpc_copy_payload))
                    }
                }
            }
        }

        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun DpcStatusCard(
    state: DpcState,
    modeLabel: String,
    onRefresh: () -> Unit,
) {
    val ready = state.preflight?.ready
    val statusColor =
        when (ready) {
            true -> MaterialTheme.colorScheme.primary
            false -> MaterialTheme.colorScheme.error
            null -> MaterialTheme.colorScheme.onSurfaceVariant
        }
    val statusText =
        when (ready) {
            true -> stringResource(R.string.dpc_ready)
            false -> stringResource(R.string.dpc_review_checks)
            null -> stringResource(R.string.dpc_unchecked)
        }

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        stringResource(R.string.dpc_status),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text(statusText, style = MaterialTheme.typography.titleSmall, color = statusColor)
                }
                OutlinedButton(onClick = onRefresh, enabled = !state.busy) {
                    Text(stringResource(if (state.busy) R.string.dpc_analyzing else R.string.dpc_analyze))
                }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                StatusPill(text = modeLabel)
                StatusPill(text = state.component.substringBefore('/'), muted = true)
            }
            state.preflight?.let { preflight ->
                Text(
                    stringResource(
                        R.string.dpc_checks_summary,
                        preflight.checks.count { it.status == DpcCheckStatus.OK },
                        preflight.checks.size,
                        preflight.sdk,
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun DpcFeedbackCard(
    busy: Boolean,
    progress: UiText?,
    message: UiText?,
    onDismissMessage: () -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (busy) {
                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(10.dp))
            }
            Text(
                if (busy) progress?.resolve() ?: stringResource(R.string.dpc_processing) else message?.resolve().orEmpty(),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.weight(1f),
            )
            if (!busy && message != null) {
                TextButton(onClick = onDismissMessage) { Text(stringResource(R.string.dpc_ok)) }
            }
        }
    }
}

@Composable
private fun DpcCheckRow(
    label: String,
    detail: String,
    status: DpcCheckStatus,
) {
    val color =
        when (status) {
            DpcCheckStatus.OK -> MaterialTheme.colorScheme.primary
            DpcCheckStatus.WARN -> MaterialTheme.colorScheme.tertiary
            DpcCheckStatus.FAIL -> MaterialTheme.colorScheme.error
        }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        StatusPill(
            text =
                when (status) {
                    DpcCheckStatus.OK -> stringResource(R.string.dpc_ok)
                    DpcCheckStatus.WARN -> stringResource(R.string.dpc_check_review)
                    DpcCheckStatus.FAIL -> stringResource(R.string.dpc_check_blocked)
                },
            color = color,
        )
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            Text(
                detail,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun StatusPill(
    text: String,
    color: Color = MaterialTheme.colorScheme.primary,
    muted: Boolean = false,
) {
    val pillColor = if (muted) MaterialTheme.colorScheme.onSurfaceVariant else color
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = pillColor,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier =
            Modifier
                .clip(RoundedCornerShape(50))
                .background(pillColor.copy(alpha = 0.12f))
                .padding(horizontal = 10.dp, vertical = 4.dp),
    )
}

@Composable
private fun DpcSection(
    number: String,
    title: String,
    subtitle: String,
    content: @Composable () -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (number.isNotEmpty()) {
                    Text(
                        number,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onPrimary,
                        modifier =
                            Modifier
                                .clip(RoundedCornerShape(50))
                                .background(MaterialTheme.colorScheme.primary)
                                .padding(horizontal = 8.dp, vertical = 2.dp),
                    )
                }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleSmall)
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            content()
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun DpcScreenPreview() {
    AdbOptimizerTheme {
        DpcScreen(
            state = DpcState(),
            onBack = {},
            onMode = {},
            onPreset = {},
            onComponent = {},
            onUrl = {},
            onRefresh = {},
            onDetectReceivers = {},
            onUseReceiver = {},
            onInstallLocal = {},
            onInstallUrl = {},
            onSetOwner = {},
            onRemoveOwner = {},
            onGenerateQr = {},
            onDismissMessage = {},
        )
    }
}
