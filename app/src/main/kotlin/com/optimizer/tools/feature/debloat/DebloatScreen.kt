package com.optimizer.tools.feature.debloat

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.optimizer.tools.R
import com.optimizer.tools.app.tv.LocalTelevisionUi
import com.optimizer.tools.app.tv.TvFocusCard
import com.optimizer.tools.app.tv.TvRiskChip
import com.optimizer.tools.app.tv.TvScreenHeader
import com.optimizer.tools.core.ui.DesignChip
import com.optimizer.tools.core.ui.DesignDivider
import com.optimizer.tools.core.ui.DesignEmptyState
import com.optimizer.tools.core.ui.DesignSearchField
import com.optimizer.tools.core.ui.PillButton
import com.optimizer.tools.core.ui.PillStyle
import com.optimizer.tools.core.ui.ToolEmptyState
import com.optimizer.tools.core.ui.ToolTopBar
import com.optimizer.tools.core.ui.resolve
import com.optimizer.tools.domain.debloat.DebloatAction
import com.optimizer.tools.domain.debloat.DebloatRisk
import com.optimizer.tools.domain.debloat.DebloatRun
import com.optimizer.tools.domain.debloat.DebloatSuggestion
import com.optimizer.tools.domain.profile.ProfileAppState

@Composable
internal fun DebloatScreen(
    state: DebloatState,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onDownload: () -> Unit,
    onQuery: (String) -> Unit,
    onToggleRisk: (DebloatRisk) -> Unit,
    onToggle: (String) -> Unit,
    onSelectAll: () -> Unit,
    onClearSelection: () -> Unit,
    onRequestAction: (DebloatAction) -> Unit,
    onCancelAction: () -> Unit,
    onConfirmAction: () -> Unit,
    onUndo: () -> Unit,
    onDismissMessage: () -> Unit,
    onDismissError: () -> Unit,
) {
    if (LocalTelevisionUi.current) {
        TvDebloatContent(
            state = state,
            onDownload = onDownload,
            onToggle = onToggle,
            onSelectAll = onSelectAll,
            onClearSelection = onClearSelection,
            onRequestAction = onRequestAction,
            onCancelAction = onCancelAction,
            onConfirmAction = onConfirmAction,
            onUndo = onUndo,
            onDismissMessage = onDismissMessage,
            onDismissError = onDismissError,
        )
        return
    }
    Column(Modifier.fillMaxSize()) {
        ToolTopBar(
            title = stringResource(R.string.debloat_title),
            subtitle = stringResource(R.string.debloat_subtitle_count, state.visible.size, state.selection.size),
            onBack = onBack,
            actions = {
                IconButton(onClick = onRefresh, enabled = state.deviceAvailable && !state.running) {
                    Icon(Icons.Filled.Refresh, contentDescription = stringResource(R.string.debloat_refresh))
                }
            },
        )
        when {
            !state.deviceAvailable -> {
                ToolEmptyState(
                    icon = Icons.Filled.PhoneAndroid,
                    title = stringResource(R.string.debloat_no_device_title),
                    description = stringResource(R.string.debloat_no_device),
                )
            }

            !state.catalogReady -> {
                CatalogCard(state, onDownload)
            }

            else -> {
                DebloatBody(
                    state = state,
                    onQuery = onQuery,
                    onToggleRisk = onToggleRisk,
                    onToggle = onToggle,
                    onSelectAll = onSelectAll,
                    onClearSelection = onClearSelection,
                    onRequestAction = onRequestAction,
                    onUndo = onUndo,
                    onDismissMessage = onDismissMessage,
                    onDismissError = onDismissError,
                    onDownload = onDownload,
                )
            }
        }
        state.pendingAction?.let { action ->
            ConfirmDialog(
                action = action,
                count = state.selection.size,
                onDismiss = onCancelAction,
                onConfirm = onConfirmAction,
            )
        }
    }
}

/**
 * Presentacion para television: la lista recibe todo el alto disponible, las filas se
 * recorren con el D-pad y las acciones viven en una sola fila inferior.
 */
@Composable
private fun TvDebloatContent(
    state: DebloatState,
    onDownload: () -> Unit,
    onToggle: (String) -> Unit,
    onSelectAll: () -> Unit,
    onClearSelection: () -> Unit,
    onRequestAction: (DebloatAction) -> Unit,
    onCancelAction: () -> Unit,
    onConfirmAction: () -> Unit,
    onUndo: () -> Unit,
    onDismissMessage: () -> Unit,
    onDismissError: () -> Unit,
) {
    val firstRow = remember { FocusRequester() }
    LaunchedEffect(state.visible.firstOrNull()?.packageName) {
        if (state.visible.isNotEmpty()) {
            runCatching { firstRow.requestFocus() }
        }
    }
    Column(Modifier.fillMaxSize()) {
        TvScreenHeader(
            title = stringResource(R.string.debloat_title),
            subtitle = stringResource(R.string.debloat_subtitle_count, state.visible.size, state.selection.size),
            actions = {
                if (state.lastRun?.changes?.isNotEmpty() == true) {
                    TextButton(onClick = onUndo, enabled = !state.running) {
                        Text(stringResource(R.string.debloat_undo))
                    }
                }
            },
        )
        when {
            !state.deviceAvailable -> {
                Box(Modifier.weight(1f)) {
                    ToolEmptyState(
                        icon = Icons.Filled.PhoneAndroid,
                        title = stringResource(R.string.debloat_no_device_title),
                        description = stringResource(R.string.debloat_no_device),
                    )
                }
            }

            !state.catalogReady -> {
                Box(Modifier.weight(1f)) { CatalogCard(state, onDownload) }
            }

            else -> {
                state.error?.let { NoticeCard(it.resolve(), MaterialTheme.colorScheme.error, onDismissError) }
                state.message?.let { NoticeCard(it.resolve(), MaterialTheme.colorScheme.primary, onDismissMessage) }
                Text(
                    stringResource(R.string.debloat_warning_body),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.tertiary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(
                    Modifier.fillMaxWidth().padding(top = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        stringResource(R.string.debloat_count, state.visible.size, state.catalogSize),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = onSelectAll, enabled = state.visible.isNotEmpty() && !state.running) {
                        Text(stringResource(R.string.debloat_select_visible))
                    }
                    TextButton(onClick = onClearSelection, enabled = state.selection.isNotEmpty() && !state.running) {
                        Text(stringResource(R.string.debloat_clear_selection))
                    }
                }
                if (state.running) {
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Text(
                            stringResource(R.string.debloat_running, state.done, state.total),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        LinearProgressIndicator(Modifier.weight(1f))
                    }
                }
                when {
                    state.visible.isEmpty() -> {
                        Box(Modifier.weight(1f)) {
                            ToolEmptyState(
                                icon = Icons.Filled.Apps,
                                title = stringResource(R.string.debloat_empty_title),
                                description = stringResource(R.string.debloat_empty_body),
                            )
                        }
                    }

                    else -> {
                        LazyColumn(
                            Modifier
                                .weight(1f)
                                .padding(top = 4.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            itemsIndexed(state.visible, key = { _, item -> item.packageName }) { index, suggestion ->
                                TvSuggestionRow(
                                    suggestion = suggestion,
                                    selected = suggestion.packageName in state.selection,
                                    enabled = !state.running,
                                    onToggle = { onToggle(suggestion.packageName) },
                                    modifier =
                                        if (index == 0) {
                                            Modifier.focusRequester(firstRow)
                                        } else {
                                            Modifier
                                        },
                                )
                            }
                        }
                    }
                }
                Row(
                    Modifier.fillMaxWidth().padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    TvDebloatAction(
                        label = stringResource(R.string.debloat_review_count, state.selection.size),
                        enabled = state.selection.isNotEmpty() && !state.running,
                        primary = true,
                        onClick = { onRequestAction(DebloatAction.FREEZE) },
                        modifier = Modifier.weight(1.4f),
                    )
                    TvDebloatAction(
                        label = stringResource(R.string.debloat_unfreeze),
                        enabled = state.selection.isNotEmpty() && !state.running,
                        onClick = { onRequestAction(DebloatAction.UNFREEZE) },
                        modifier = Modifier.weight(1f),
                    )
                    TvDebloatAction(
                        label = stringResource(R.string.debloat_uninstall),
                        enabled = state.selection.isNotEmpty() && !state.running,
                        onClick = { onRequestAction(DebloatAction.UNINSTALL) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
    state.pendingAction?.let { action ->
        ConfirmDialog(
            action = action,
            count = state.selection.size,
            onDismiss = onCancelAction,
            onConfirm = onConfirmAction,
        )
    }
}

@Composable
private fun TvSuggestionRow(
    suggestion: DebloatSuggestion,
    selected: Boolean,
    enabled: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var focused by remember(suggestion.packageName) { mutableStateOf(false) }
    val riskColor = suggestion.entry.risk.tvColor()
    Surface(
        onClick = onToggle,
        enabled = enabled,
        modifier = modifier.fillMaxWidth().onFocusChanged { focused = it.isFocused },
        shape = RoundedCornerShape(12.dp),
        color =
            if (focused) {
                MaterialTheme.colorScheme.surfaceContainerHigh
            } else {
                MaterialTheme.colorScheme.surface
            },
        border = if (focused) BorderStroke(2.dp, MaterialTheme.colorScheme.onSurface) else null,
    ) {
        Row(
            Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Checkbox(checked = selected, onCheckedChange = null, enabled = enabled)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    suggestion.label,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    "${suggestion.packageName} · ${stringResource(suggestion.entry.risk.labelResource())} · " +
                        stringResource(suggestion.state.labelResource()),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            TvRiskChip(
                text = stringResource(suggestion.entry.risk.labelResource()),
                color = riskColor,
            )
        }
    }
}

@Composable
private fun TvDebloatAction(
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    primary: Boolean = false,
) {
    TvFocusCard(
        onClick = if (enabled) onClick else null,
        modifier = modifier,
    ) {
        Text(
            label,
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
            style = MaterialTheme.typography.labelLarge,
            color =
                when {
                    !enabled -> MaterialTheme.colorScheme.onSurfaceVariant
                    primary -> MaterialTheme.colorScheme.primary
                    else -> MaterialTheme.colorScheme.onSurface
                },
            maxLines = 1,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun DebloatRisk.tvColor(): Color =
    when (this) {
        DebloatRisk.RECOMMENDED -> MaterialTheme.colorScheme.primary
        DebloatRisk.ADVANCED -> MaterialTheme.colorScheme.tertiary
        DebloatRisk.EXPERT -> MaterialTheme.colorScheme.error
        DebloatRisk.UNSAFE, DebloatRisk.UNKNOWN -> MaterialTheme.colorScheme.onSurfaceVariant
    }

@Composable
private fun CatalogCard(
    state: DebloatState,
    onDownload: () -> Unit,
) {
    Card(
        Modifier.fillMaxWidth().padding(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(stringResource(R.string.debloat_catalog_title), style = MaterialTheme.typography.titleMedium)
            Text(
                stringResource(R.string.debloat_catalog_body),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (state.downloading) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
                Text(
                    stringResource(R.string.debloat_catalog_progress, state.downloadProgress),
                    style = MaterialTheme.typography.bodySmall,
                )
            } else {
                Button(onClick = onDownload, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                    Icon(Icons.Filled.CloudDownload, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.debloat_catalog_download))
                }
            }
            state.error?.let { error ->
                Text(error.resolve(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
private fun DebloatBody(
    state: DebloatState,
    onQuery: (String) -> Unit,
    onToggleRisk: (DebloatRisk) -> Unit,
    onToggle: (String) -> Unit,
    onSelectAll: () -> Unit,
    onClearSelection: () -> Unit,
    onRequestAction: (DebloatAction) -> Unit,
    onUndo: () -> Unit,
    onDismissMessage: () -> Unit,
    onDismissError: () -> Unit,
    onDownload: () -> Unit,
) {
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        state.error?.let { error ->
            NoticeCard(error.resolve(), MaterialTheme.colorScheme.error, onDismissError)
        }
        state.message?.let { message ->
            NoticeCard(message.resolve(), MaterialTheme.colorScheme.primary, onDismissMessage)
        }
        DesignSearchField(
            value = state.query,
            onValueChange = onQuery,
            placeholder = stringResource(R.string.debloat_search),
            modifier = Modifier.padding(top = 4.dp),
        )
        Spacer(Modifier.height(8.dp))
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            DebloatRisk.entries.filter { it != DebloatRisk.UNSAFE && it != DebloatRisk.UNKNOWN }.forEach { risk ->
                DesignChip(
                    text = stringResource(risk.labelResource()),
                    selected = risk in state.risks,
                    onClick = { onToggleRisk(risk) },
                )
            }
        }
        Text(
            stringResource(R.string.debloat_count, state.visible.size, state.catalogSize),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp, start = 4.dp),
        )
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onSelectAll) { Text(stringResource(R.string.debloat_select_visible)) }
            TextButton(onClick = onClearSelection) { Text(stringResource(R.string.debloat_clear_selection)) }
        }
        if (state.running) {
            Text(
                stringResource(R.string.debloat_running, state.done, state.total),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            LinearProgressIndicator(Modifier.fillMaxWidth().padding(vertical = 6.dp))
        }
        when {
            state.visible.isEmpty() -> {
                DesignEmptyState(
                    icon = Icons.Filled.Apps,
                    title = stringResource(R.string.debloat_empty_title),
                    body = stringResource(R.string.debloat_empty_body),
                    modifier = Modifier.padding(top = 12.dp),
                )
            }

            else -> {
                LazyColumn(
                    Modifier
                        .weight(1f)
                        .padding(top = 6.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.surface),
                ) {
                    itemsIndexed(state.visible, key = { _, item -> item.packageName }) { index, suggestion ->
                        if (index > 0) DesignDivider()
                        SuggestionRow(
                            suggestion = suggestion,
                            selected = suggestion.packageName in state.selection,
                            enabled = !state.running,
                            onToggle = { onToggle(suggestion.packageName) },
                        )
                    }
                }
            }
        }
        Column(
            Modifier
                .fillMaxWidth()
                .padding(top = 8.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(MaterialTheme.colorScheme.tertiary.copy(alpha = 0.10f))
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                stringResource(R.string.debloat_warning_title),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.tertiary,
            )
            Text(
                stringResource(R.string.debloat_warning_body),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        state.lastRun?.takeIf { it.changes.isNotEmpty() }?.let { run ->
            LastRunCard(run = run, enabled = !state.running, onUndo = onUndo)
        }
        PillButton(
            text = stringResource(R.string.debloat_review_count, state.selection.size),
            onClick = { onRequestAction(DebloatAction.FREEZE) },
            enabled = state.selection.isNotEmpty() && !state.running,
            style = PillStyle.Solid,
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        )
        Row(
            Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            PillButton(
                text = stringResource(R.string.debloat_unfreeze),
                onClick = { onRequestAction(DebloatAction.UNFREEZE) },
                enabled = state.selection.isNotEmpty() && !state.running,
                style = PillStyle.Outline,
                modifier = Modifier.weight(1f),
            )
            PillButton(
                text = stringResource(R.string.debloat_uninstall),
                onClick = { onRequestAction(DebloatAction.UNINSTALL) },
                enabled = state.selection.isNotEmpty() && !state.running,
                style = PillStyle.Outline,
                modifier = Modifier.weight(1f),
            )
            PillButton(
                text = stringResource(R.string.debloat_update_list),
                onClick = onDownload,
                enabled = !state.downloading,
                style = PillStyle.Outline,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun SuggestionRow(
    suggestion: DebloatSuggestion,
    selected: Boolean,
    enabled: Boolean,
    onToggle: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .toggleable(value = selected, enabled = enabled, role = Role.Checkbox, onValueChange = { onToggle() })
            .background(if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.08f) else Color.Transparent)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        AppInitialBadge(suggestion.label)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                suggestion.label,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                buildString {
                    append(suggestion.packageName)
                    append(" · ").append(stringResource(suggestion.entry.risk.labelResource()))
                    append(" · ").append(stringResource(suggestion.state.labelResource()))
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (suggestion.entry.description.isNotBlank()) {
                Text(
                    suggestion.entry.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Box(
            Modifier
                .size(20.dp)
                .clip(RoundedCornerShape(5.dp))
                .background(if (selected) MaterialTheme.colorScheme.primary else Color.Transparent)
                .border(
                    width = if (selected) 0.dp else 1.5.dp,
                    color = if (selected) Color.Transparent else MaterialTheme.colorScheme.outline,
                    shape = RoundedCornerShape(5.dp),
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (selected) {
                Icon(
                    Icons.Filled.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(14.dp),
                )
            }
        }
    }
}

@Composable
private fun AppInitialBadge(label: String) {
    val tone =
        when (label.hashCode().mod(3)) {
            0 -> MaterialTheme.colorScheme.tertiary
            1 -> MaterialTheme.colorScheme.primary
            else -> MaterialTheme.colorScheme.secondary
        }
    Box(
        Modifier
            .size(36.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(tone.copy(alpha = 0.16f)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label.take(1).uppercase(),
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
            color = tone,
        )
    }
}

@Composable
private fun NoticeCard(
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
private fun LastRunCard(
    run: DebloatRun,
    enabled: Boolean,
    onUndo: () -> Unit,
) {
    Card(
        Modifier.fillMaxWidth().padding(top = 8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                stringResource(R.string.debloat_last_run, run.count),
                style = MaterialTheme.typography.bodyMedium,
            )
            if (run.failures.isNotEmpty()) {
                Text(
                    stringResource(R.string.debloat_last_run_failures, run.failures.size),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            TextButton(onClick = onUndo, enabled = enabled) { Text(stringResource(R.string.debloat_undo)) }
        }
    }
}

@Composable
private fun ConfirmDialog(
    action: DebloatAction,
    count: Int,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Filled.WarningAmber, contentDescription = null) },
        title = { Text(stringResource(action.confirmTitleResource())) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.debloat_confirm_body, count))
                Text(
                    stringResource(
                        if (action == DebloatAction.UNINSTALL) {
                            R.string.debloat_confirm_uninstall_warning
                        } else {
                            R.string.debloat_confirm_freeze_warning
                        },
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = { Button(onClick = onConfirm) { Text(stringResource(R.string.debloat_confirm)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.profiles_cancel)) } },
    )
}

private fun DebloatRisk.labelResource(): Int =
    when (this) {
        DebloatRisk.RECOMMENDED -> R.string.debloat_risk_recommended
        DebloatRisk.ADVANCED -> R.string.debloat_risk_advanced
        DebloatRisk.EXPERT -> R.string.debloat_risk_expert
        DebloatRisk.UNSAFE -> R.string.debloat_risk_unsafe
        DebloatRisk.UNKNOWN -> R.string.debloat_risk_unknown
    }

private fun ProfileAppState.labelResource(): Int =
    when (this) {
        ProfileAppState.ENABLED -> R.string.debloat_state_enabled
        ProfileAppState.DISABLED -> R.string.debloat_state_disabled
        ProfileAppState.UNINSTALLED -> R.string.debloat_state_uninstalled
    }

private fun DebloatAction.confirmTitleResource(): Int =
    when (this) {
        DebloatAction.FREEZE -> R.string.debloat_confirm_freeze_title
        DebloatAction.UNFREEZE -> R.string.debloat_confirm_unfreeze_title
        DebloatAction.UNINSTALL -> R.string.debloat_confirm_uninstall_title
    }
