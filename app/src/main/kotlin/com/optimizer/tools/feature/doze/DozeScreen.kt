package com.optimizer.tools.feature.doze

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.optimizer.tools.R
import com.optimizer.tools.core.ui.SkeletonLine
import com.optimizer.tools.core.ui.ToolEmptyState
import com.optimizer.tools.core.ui.ToolTopBar
import com.optimizer.tools.core.ui.resolve

@Composable
internal fun DozeScreen(
    state: DozeState,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onAction: (DozeAction) -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        ToolTopBar(
            title = stringResource(R.string.doze_title),
            subtitle = stringResource(R.string.doze_subtitle),
            onBack = onBack,
        )
        when {
            !state.connected -> {
                ToolEmptyState(
                    icon = Icons.Filled.PhoneAndroid,
                    title = stringResource(R.string.doze_no_device_title),
                    description = stringResource(R.string.doze_no_device),
                )
            }

            state.error != null -> {
                ToolEmptyState(
                    icon = Icons.Filled.WarningAmber,
                    title = stringResource(R.string.doze_error_title),
                    description = state.error.resolve(),
                    action = { OutlinedButton(onClick = onRefresh) { Text(stringResource(R.string.action_retry)) } },
                )
            }

            state.loading -> {
                DozeSkeleton()
            }

            else -> {
                DozeBody(state, onAction = onAction)
            }
        }
    }
}

@Composable
private fun DozeBody(
    state: DozeState,
    onAction: (DozeAction) -> Unit,
) {
    Column(
        Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Spacer(Modifier.height(4.dp))
        StatusCard(state.status)
        Timeline(state.status.phase)
        Text(stringResource(R.string.doze_actions_title), style = MaterialTheme.typography.titleSmall)
        DozeActions(state.running, onAction)
        state.message?.let { message ->
            Text(
                message.resolve(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun StatusCard(status: DozeStatus) {
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                stringResource(R.string.doze_deep_state),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    status.deep ?: stringResource(R.string.doze_unknown),
                    style = MaterialTheme.typography.headlineSmall,
                    fontFamily = FontFamily.Monospace,
                )
                Spacer(Modifier.width(12.dp))
                ForcedBadge(status.forced)
            }
            Text(
                stringResource(R.string.doze_light_state, status.light ?: stringResource(R.string.doze_unknown)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ForcedBadge(forced: Boolean?) {
    if (forced == null) return
    val text = if (forced) R.string.doze_forced_yes else R.string.doze_forced_no
    val tint = if (forced) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant
    Box(
        Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(tint.copy(alpha = 0.14f))
            .padding(horizontal = 10.dp, vertical = 4.dp),
    ) {
        Text(stringResource(text), style = MaterialTheme.typography.labelMedium, color = tint)
    }
}

@Composable
private fun Timeline(phase: DozePhase) {
    val phases =
        listOf(
            DozePhase.ACTIVE to R.string.doze_phase_active,
            DozePhase.INACTIVE to R.string.doze_phase_inactive,
            DozePhase.PENDING to R.string.doze_phase_pending,
            DozePhase.IDLE to R.string.doze_phase_idle,
            DozePhase.MAINTENANCE to R.string.doze_phase_maintenance,
        )
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        phases.forEach { (item, label) ->
            val current = item == phase
            val reached = phase.ordinal >= item.ordinal && phase != DozePhase.UNKNOWN
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(
                            when {
                                current -> MaterialTheme.colorScheme.primary
                                reached -> MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
                                else -> MaterialTheme.colorScheme.surfaceVariant
                            },
                        ),
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    stringResource(label),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (current) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = if (current) FontWeight.SemiBold else FontWeight.Normal,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun DozeActions(
    running: DozeAction?,
    onAction: (DozeAction) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        ActionRow(DozeAction.FORCE, running, onAction, primary = true)
        ActionRow(DozeAction.STEP, running, onAction)
        ActionRow(DozeAction.UNFORCE, running, onAction)
        ActionRow(DozeAction.MOTION, running, onAction)
    }
}

@Composable
private fun ActionRow(
    action: DozeAction,
    running: DozeAction?,
    onAction: (DozeAction) -> Unit,
    primary: Boolean = false,
) {
    val label = stringResource(action.labelResource())
    val enabled = running == null
    val content: @Composable () -> Unit = {
        if (running == action) {
            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
        } else {
            Text(label)
        }
    }
    if (primary) {
        FilledTonalButton(onClick = { onAction(action) }, enabled = enabled, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
            content()
        }
    } else {
        OutlinedButton(onClick = { onAction(action) }, enabled = enabled, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
            content()
        }
    }
}

@Composable
private fun DozeSkeleton() {
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SkeletonLine(0.9f)
        SkeletonLine(0.55f)
        SkeletonLine(0.35f)
    }
}

internal fun DozeAction.labelResource(): Int =
    when (this) {
        DozeAction.FORCE -> R.string.doze_force
        DozeAction.STEP -> R.string.doze_step
        DozeAction.UNFORCE -> R.string.doze_unforce
        DozeAction.MOTION -> R.string.doze_motion
    }

@Preview(name = "Doze · activo", heightDp = 720)
@Composable
private fun DozeActivePreview() {
    DozeScreen(
        state = DozeState(connected = true, status = DozeStatus(deep = "ACTIVE", light = "ACTIVE", forced = false)),
        onBack = {},
        onRefresh = {},
        onAction = {},
    )
}

@Preview(name = "Doze · en reposo", heightDp = 720)
@Composable
private fun DozeIdlePreview() {
    DozeScreen(
        state = DozeState(connected = true, status = DozeStatus(deep = "IDLE_MAINTENANCE", light = "IDLE", forced = true)),
        onBack = {},
        onRefresh = {},
        onAction = {},
    )
}

@Preview(name = "Doze · sin dispositivo", heightDp = 720)
@Composable
private fun DozeDisconnectedPreview() {
    DozeScreen(state = DozeState(connected = false), onBack = {}, onRefresh = {}, onAction = {})
}
