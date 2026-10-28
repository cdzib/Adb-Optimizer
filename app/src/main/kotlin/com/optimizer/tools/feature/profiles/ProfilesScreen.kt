package com.optimizer.tools.feature.profiles

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.optimizer.tools.R
import com.optimizer.tools.core.ui.ToolEmptyState
import com.optimizer.tools.core.ui.ToolTopBar
import com.optimizer.tools.core.ui.formatEpoch
import com.optimizer.tools.core.ui.resolve
import com.optimizer.tools.domain.profile.AppProfile
import com.optimizer.tools.domain.profile.ProfilePlan
import com.optimizer.tools.domain.profile.ProfileRun
import com.optimizer.tools.domain.profile.ProfileStepKind

@Composable
internal fun ProfilesScreen(
    state: ProfilesState,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onStartCreate: () -> Unit,
    onCancelCreate: () -> Unit,
    onNameDraft: (String) -> Unit,
    onCreate: () -> Unit,
    onPreview: (AppProfile) -> Unit,
    onDismissPreview: () -> Unit,
    onApply: (AppProfile) -> Unit,
    onToggleWatch: (AppProfile) -> Unit,
    onRequestDelete: (AppProfile) -> Unit,
    onCancelDelete: () -> Unit,
    onConfirmDelete: () -> Unit,
    onUndo: () -> Unit,
    onDismissMessage: () -> Unit,
    onDismissError: () -> Unit,
    onDismissDrift: () -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        ToolTopBar(
            title = stringResource(R.string.profiles_title),
            subtitle = stringResource(R.string.profiles_subtitle),
            onBack = onBack,
            actions = {
                IconButton(onClick = onRefresh) {
                    Icon(Icons.Filled.Refresh, contentDescription = stringResource(R.string.profiles_refresh))
                }
            },
        )
        when {
            !state.deviceAvailable -> {
                ToolEmptyState(
                    icon = Icons.Filled.PhoneAndroid,
                    title = stringResource(R.string.profiles_no_device_title),
                    description = stringResource(R.string.profiles_no_device),
                    action = { OutlinedButton(onClick = onRefresh) { Text(stringResource(R.string.action_retry)) } },
                )
            }

            state.loading && state.profiles.isEmpty() -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            }

            else -> {
                ProfilesBody(
                    state = state,
                    onStartCreate = onStartCreate,
                    onPreview = onPreview,
                    onToggleWatch = onToggleWatch,
                    onRequestDelete = onRequestDelete,
                    onUndo = onUndo,
                    onDismissDrift = onDismissDrift,
                    onDismissMessage = onDismissMessage,
                    onDismissError = onDismissError,
                )
            }
        }
        state.preview?.let { preview ->
            PreviewDialog(
                preview = preview,
                onDismiss = onDismissPreview,
                onApply = { onApply(preview.profile) },
            )
        }
        state.pendingDelete?.let { profile ->
            DeleteDialog(
                profile = profile,
                onDismiss = onCancelDelete,
                onConfirm = onConfirmDelete,
            )
        }
        if (state.creating) {
            CreateDialog(
                draft = state.nameDraft,
                onDraft = onNameDraft,
                onDismiss = onCancelCreate,
                onCreate = onCreate,
            )
        }
    }
}

@Composable
private fun ProfilesBody(
    state: ProfilesState,
    onStartCreate: () -> Unit,
    onPreview: (AppProfile) -> Unit,
    onToggleWatch: (AppProfile) -> Unit,
    onRequestDelete: (AppProfile) -> Unit,
    onUndo: () -> Unit,
    onDismissDrift: () -> Unit,
    onDismissMessage: () -> Unit,
    onDismissError: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        state.error?.let { error ->
            MessageCard(
                text = error.resolve(),
                tint = MaterialTheme.colorScheme.error,
                onDismiss = onDismissError,
            )
        }
        state.drift?.let { drift ->
            DriftCard(
                preview = drift,
                onReview = { onPreview(drift.profile) },
                onDismiss = onDismissDrift,
            )
        }
        if (state.running) {
            RunningCard(state)
        }
        state.message?.let { message ->
            MessageCard(
                text = message.resolve(),
                tint = MaterialTheme.colorScheme.primary,
                onDismiss = onDismissMessage,
            )
        }
        Spacer(Modifier.height(4.dp))
        Button(
            onClick = onStartCreate,
            enabled = !state.running,
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
        ) {
            Text(stringResource(R.string.profiles_create))
        }
        if (state.profiles.isEmpty()) {
            Text(
                stringResource(R.string.profiles_empty_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 8.dp),
            )
        }
        state.profiles.forEach { profile ->
            ProfileCard(
                profile = profile,
                enabled = !state.running,
                onPreview = { onPreview(profile) },
                onToggleWatch = { onToggleWatch(profile) },
                onDelete = { onRequestDelete(profile) },
            )
        }
        state.lastRun?.takeIf { it.changes.isNotEmpty() }?.let { run ->
            LastRunCard(run = run, enabled = !state.running, onUndo = onUndo)
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun ProfileCard(
    profile: AppProfile,
    enabled: Boolean,
    onPreview: () -> Unit,
    onToggleWatch: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        profile.name,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        stringResource(
                            R.string.profiles_card_subtitle,
                            profile.appCount,
                            formatEpoch(profile.createdAt),
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (profile.watched) {
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(999.dp))
                            .background(MaterialTheme.colorScheme.tertiary.copy(alpha = 0.16f))
                            .padding(horizontal = 10.dp, vertical = 4.dp),
                    ) {
                        Text(
                            stringResource(R.string.profiles_watched),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.tertiary,
                        )
                    }
                }
                IconButton(onClick = onDelete, enabled = enabled) {
                    Icon(
                        Icons.Filled.Delete,
                        contentDescription = stringResource(R.string.profiles_delete),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.profiles_watch),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f),
                )
                Switch(checked = profile.watched, onCheckedChange = { onToggleWatch() }, enabled = enabled)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(onClick = onPreview, enabled = enabled, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Filled.Visibility, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.profiles_preview))
                }
            }
        }
    }
}

@Composable
private fun RunningCard(state: ProfilesState) {
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                if (state.total > 0) {
                    stringResource(R.string.profiles_running, state.done, state.total)
                } else {
                    stringResource(R.string.profiles_running_start)
                },
                style = MaterialTheme.typography.bodyMedium,
            )
            LinearProgressIndicator(Modifier.fillMaxWidth())
            state.currentPackage?.let { packageName ->
                Text(
                    packageName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun LastRunCard(
    run: ProfileRun,
    enabled: Boolean,
    onUndo: () -> Unit,
) {
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(run.profileName, style = MaterialTheme.typography.titleSmall)
            Text(
                stringResource(R.string.profiles_last_run, run.count, formatEpoch(run.appliedAt)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (run.failures.isNotEmpty()) {
                Text(
                    stringResource(R.string.profiles_last_run_failures, run.failures.size),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            OutlinedButton(onClick = onUndo, enabled = enabled, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.profiles_undo))
            }
        }
    }
}

@Composable
private fun DriftCard(
    preview: ProfilePreview,
    onReview: () -> Unit,
    onDismiss: () -> Unit,
) {
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                stringResource(R.string.profiles_drift_title),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                stringResource(R.string.profiles_drift_body, preview.plan.appCount, preview.profile.name),
                style = MaterialTheme.typography.bodySmall,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onReview) { Text(stringResource(R.string.profiles_drift_review)) }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.profiles_drift_dismiss)) }
            }
        }
    }
}

@Composable
private fun MessageCard(
    text: String,
    tint: androidx.compose.ui.graphics.Color,
    onDismiss: () -> Unit,
) {
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = tint.copy(alpha = 0.12f)),
        shape = RoundedCornerShape(12.dp),
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(text, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_close)) }
        }
    }
}

@Composable
private fun PreviewDialog(
    preview: ProfilePreview,
    onDismiss: () -> Unit,
    onApply: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(preview.profile.name) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (preview.plan.isEmpty) {
                    Text(stringResource(R.string.profiles_nothing_to_do))
                } else {
                    Text(stringResource(R.string.profiles_plan_intro, preview.plan.appCount))
                    PlanRow(ProfileStepKind.REINSTALL, R.string.profiles_step_reinstall, preview.plan)
                    PlanRow(ProfileStepKind.ENABLE, R.string.profiles_step_enable, preview.plan)
                    PlanRow(ProfileStepKind.DISABLE, R.string.profiles_step_disable, preview.plan)
                    PlanRow(ProfileStepKind.UNINSTALL, R.string.profiles_step_uninstall, preview.plan)
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    stringResource(
                        R.string.profiles_plan_footer,
                        preview.plan.same,
                        preview.plan.missing,
                        preview.plan.blocked,
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            Button(onClick = onApply, enabled = !preview.plan.isEmpty) {
                Text(stringResource(R.string.profiles_apply))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.profiles_cancel)) }
        },
    )
}

@Composable
private fun PlanRow(
    kind: ProfileStepKind,
    label: Int,
    plan: ProfilePlan,
) {
    val count = plan.count(kind)
    if (count == 0) return
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(label), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Text(count.toString(), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun DeleteDialog(
    profile: AppProfile,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.profiles_delete_title)) },
        text = { Text(stringResource(R.string.profiles_delete_body, profile.name)) },
        confirmButton = {
            Button(onClick = onConfirm) { Text(stringResource(R.string.profiles_delete)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.profiles_cancel)) }
        },
    )
}

@Composable
private fun CreateDialog(
    draft: String,
    onDraft: (String) -> Unit,
    onDismiss: () -> Unit,
    onCreate: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.profiles_create)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    stringResource(R.string.profiles_create_body),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = draft,
                    onValueChange = onDraft,
                    label = { Text(stringResource(R.string.profiles_name_label)) },
                    singleLine = true,
                )
            }
        },
        confirmButton = {
            Button(onClick = onCreate, enabled = draft.isNotBlank()) {
                Text(stringResource(R.string.profiles_create_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.profiles_cancel)) }
        },
    )
}
