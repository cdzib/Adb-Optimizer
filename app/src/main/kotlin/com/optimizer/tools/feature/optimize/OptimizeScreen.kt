@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.optimizer.tools.feature.optimize

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.optimizer.tools.R
import com.optimizer.tools.app.tv.LocalTelevisionUi
import com.optimizer.tools.app.tv.TvActionRow
import com.optimizer.tools.app.tv.TvRiskChip
import com.optimizer.tools.app.tv.TvScreenHeader
import com.optimizer.tools.app.tv.TvSectionLabel
import com.optimizer.tools.core.ui.AppColors
import com.optimizer.tools.core.ui.DesignChip
import com.optimizer.tools.core.ui.PillButton
import com.optimizer.tools.core.ui.PillStyle
import com.optimizer.tools.core.ui.ScreenHeader
import com.optimizer.tools.core.ui.Translate
import com.optimizer.tools.domain.model.ShizukuLocalStatus
import dev.adboptimizer.core.model.AppTarget
import dev.adboptimizer.core.model.PlanItem
import dev.adboptimizer.core.model.Profile
import dev.adboptimizer.core.model.RecipeRun
import dev.adboptimizer.core.model.Risk
import dev.adboptimizer.core.model.RunStatus
import dev.adboptimizer.core.model.StepStatus

@Composable
internal fun OptimizeScreen(
    state: OptimizeState,
    t: Translate,
    onProfile: (Profile) -> Unit,
    onToggle: (String) -> Unit,
    onApply: () -> Unit,
    onOpenLocalAdb: (() -> Unit)? = null,
) {
    var showRiskWarning by remember { mutableStateOf(false) }
    val riskySelected =
        state.plan.filter {
            it.recipe.id in state.selected && it.recipe.risk != Risk.SAFE
        }

    if (state.target == AppTarget.LOCAL && state.shizukuStatus != ShizukuLocalStatus.READY) {
        LocalOptimizeBlockedContent(onOpenLocalAdb)
        return
    }

    if (showRiskWarning) {
        RiskWarningDialog(
            items = riskySelected,
            t = t,
            onConfirm = {
                showRiskWarning = false
                onApply()
            },
            onDismiss = { showRiskWarning = false },
        )
    }

    if (LocalTelevisionUi.current) {
        TvOptimizeContent(
            state = state,
            t = t,
            riskySelected = riskySelected,
            onProfile = onProfile,
            onToggle = onToggle,
            onApplyRequest = {
                if (!state.running) {
                    if (riskySelected.isEmpty()) onApply() else showRiskWarning = true
                }
            },
        )
        return
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    if (!state.running) {
                        if (riskySelected.isEmpty()) onApply() else showRiskWarning = true
                    }
                },
                modifier = if (state.running) Modifier.semantics { disabled() } else Modifier,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ) {
                if (state.running) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                        trackColor = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.24f),
                    )
                } else {
                    androidx.compose.material3.Icon(Icons.Filled.Bolt, contentDescription = t("optimize.run"))
                }
            }
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(innerPadding),
            contentPadding =
                androidx.compose.foundation.layout
                    .PaddingValues(start = 20.dp, top = 8.dp, end = 20.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                ScreenHeader(
                    title = t("nav.optimize"),
                    subtitle =
                        stringResource(
                            R.string.optimize_header_subtitle,
                            t("optimize.profile.${state.profile.name.lowercase()}"),
                            state.supportedCount,
                        ),
                )
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        stringResource(R.string.optimize_intro),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            item {
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Profile.entries.forEach { profile ->
                        FilterChip(
                            selected = state.profile == profile,
                            onClick = { onProfile(profile) },
                            label = { Text(t("optimize.profile.${profile.name.lowercase()}")) },
                        )
                    }
                }
            }

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        stringResource(R.string.optimize_selected_count, state.selectedCount, state.supportedCount),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }

            items(state.plan.filter { it.supported }, key = { it.recipe.id }) { item ->
                PlanRow(item, selected = item.recipe.id in state.selected, t = t) { onToggle(item.recipe.id) }
            }

            val unsupported = state.plan.filterNot { it.supported }
            if (unsupported.isNotEmpty()) {
                item {
                    Text(
                        t("optimize.notSupported"),
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
                items(unsupported, key = { "u-${it.recipe.id}" }) { item ->
                    PlanRow(item, selected = false, t = t, enabled = false) {}
                }
            }

            if (state.results.isNotEmpty()) {
                item {
                    Text(
                        t("optimize.done"),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
                items(state.results, key = { it.recipeId + it.startedAt }) { run ->
                    RunResultCard(run, t)
                }
            }
        }
    }
}

@Composable
private fun TvOptimizeContent(
    state: OptimizeState,
    t: Translate,
    riskySelected: List<PlanItem>,
    onProfile: (Profile) -> Unit,
    onToggle: (String) -> Unit,
    onApplyRequest: () -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        TvScreenHeader(
            title = stringResource(R.string.tv_optimize_title),
            subtitle =
                stringResource(
                    R.string.tv_optimize_subtitle,
                    t("optimize.profile.${state.profile.name.lowercase()}"),
                    state.supportedCount,
                ),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Profile.entries.forEach { profile ->
                DesignChip(
                    text = t("optimize.profile.${profile.name.lowercase()}"),
                    selected = state.profile == profile,
                    onClick = { onProfile(profile) },
                )
            }
        }
        Spacer(Modifier.padding(top = 4.dp))
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            state.plan.filter { it.supported }.forEach { item ->
                val selected = item.recipe.id in state.selected
                var focused by remember(item.recipe.id) { mutableStateOf(false) }
                val shape = RoundedCornerShape(12.dp)
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(shape)
                        .background(
                            if (focused) {
                                MaterialTheme.colorScheme.surfaceContainerHigh
                            } else {
                                MaterialTheme.colorScheme.surface
                            },
                        ).onFocusChanged { focused = it.isFocused }
                        .toggleable(value = selected, role = Role.Checkbox, onValueChange = { onToggle(item.recipe.id) })
                        .padding(horizontal = 14.dp, vertical = 10.dp)
                        .then(
                            if (focused) {
                                Modifier.border(2.dp, MaterialTheme.colorScheme.onSurface, shape)
                            } else {
                                Modifier
                            },
                        ),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Box(
                        Modifier
                            .size(18.dp)
                            .clip(RoundedCornerShape(5.dp))
                            .background(
                                if (selected) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.surfaceContainerHighest
                                },
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
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                        Text(t(item.recipe.titleKey), style = MaterialTheme.typography.bodyMedium, maxLines = 1)
                        Text(
                            t(item.recipe.summaryKey),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    TvRiskChip(
                        text = t("optimize.risk.${item.recipe.risk.name.lowercase()}"),
                        color =
                            when (item.recipe.risk) {
                                Risk.SAFE -> MaterialTheme.colorScheme.primary
                                Risk.MODERATE -> MaterialTheme.colorScheme.tertiary
                                Risk.ADVANCED -> MaterialTheme.colorScheme.error
                            },
                    )
                }
            }
            if (state.results.isNotEmpty()) {
                TvSectionLabel(t("optimize.done"))
                state.results.forEach { run -> RunResultCard(run, t) }
            }
        }
        Row(
            Modifier.fillMaxWidth().padding(top = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                stringResource(R.string.optimize_selected_count, state.selectedCount, state.supportedCount),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            if (riskySelected.isNotEmpty()) {
                TvRiskChip(
                    text = stringResource(R.string.tv_optimize_risk_review, riskySelected.size),
                    color = MaterialTheme.colorScheme.tertiary,
                )
            }
            TvActionRow(
                primaryLabel =
                    if (state.running) {
                        t("optimize.running")
                    } else {
                        stringResource(R.string.tv_optimize_apply, state.selectedCount)
                    },
                primaryEnabled = !state.running && state.selectedCount > 0,
                onPrimary = onApplyRequest,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun LocalOptimizeBlockedContent(onOpenLocalAdb: (() -> Unit)?) {
    Column(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(stringResource(R.string.optimize_local_blocked_title), style = MaterialTheme.typography.titleMedium)
        Text(
            stringResource(R.string.optimize_local_blocked_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (onOpenLocalAdb != null) {
            PillButton(
                text = stringResource(R.string.optimize_local_blocked_action),
                onClick = onOpenLocalAdb,
                style = PillStyle.Solid,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun RiskWarningDialog(
    items: List<PlanItem>,
    t: Translate,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val hasAdvanced = items.any { it.recipe.risk == Risk.ADVANCED }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                stringResource(
                    if (hasAdvanced) {
                        R.string.optimize_risk_advanced_title
                    } else {
                        R.string.optimize_risk_moderate_title
                    },
                ),
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.optimize_risk_warning_intro))
                items.forEach { item -> Text(stringResource(R.string.optimize_risk_warning_item, t(item.recipe.titleKey))) }
                Text(
                    stringResource(
                        if (hasAdvanced) {
                            R.string.optimize_risk_advanced_body
                        } else {
                            R.string.optimize_risk_moderate_body
                        },
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = AppColors.warning,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text(stringResource(R.string.optimize_apply_confirm)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

@Composable
internal fun PlanRow(
    item: PlanItem,
    selected: Boolean,
    t: Translate,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val recipe = item.recipe
    Card(
        modifier = Modifier.fillMaxWidth().toggleable(value = selected, enabled = enabled, role = Role.Checkbox) { onClick() },
        colors =
            CardDefaults.cardColors(
                containerColor = if (selected) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface,
            ),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = selected, onCheckedChange = null, enabled = enabled)
            Column(Modifier.weight(1f).padding(start = 4.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(t(recipe.titleKey), style = MaterialTheme.typography.titleSmall)
                Text(
                    if (enabled) t(recipe.summaryKey) else reasonLabel(t, item.reason),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(8.dp))
            RiskBadge(recipe.risk, t)
        }
    }
}

@Composable
internal fun RiskBadge(
    risk: Risk,
    t: Translate,
) {
    val color =
        when (risk) {
            Risk.SAFE -> AppColors.accent
            Risk.MODERATE -> AppColors.warning
            Risk.ADVANCED -> AppColors.danger
        }
    Box(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(color.copy(alpha = 0.12f))
            .padding(horizontal = 8.dp, vertical = 3.dp),
    ) {
        Text(t("optimize.risk.${risk.name.lowercase()}"), style = MaterialTheme.typography.labelSmall, color = color)
    }
}

@Composable
internal fun RunResultCard(
    run: RecipeRun,
    t: Translate,
) {
    val statusColor =
        when (run.status) {
            RunStatus.OK -> AppColors.accent
            RunStatus.PARTIAL -> AppColors.warning
            else -> AppColors.danger
        }
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(run.recipeId, style = MaterialTheme.typography.titleSmall)
                Text(run.status.name, style = MaterialTheme.typography.labelSmall, color = statusColor)
            }
            run.steps.forEach { step ->
                HorizontalDivider(color = AppColors.border)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(step.run, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                    Text(
                        step.status.name,
                        style = MaterialTheme.typography.labelSmall,
                        color =
                            when (step.status) {
                                StepStatus.OK -> AppColors.accent
                                StepStatus.FAILED -> AppColors.danger
                                else -> AppColors.muted
                            },
                    )
                }
                step.error?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = AppColors.danger) }
            }
        }
    }
}

// ---------------------------------------------------------------- History
