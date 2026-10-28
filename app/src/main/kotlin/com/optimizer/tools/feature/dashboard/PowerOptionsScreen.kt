package com.optimizer.tools.feature.dashboard

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.optimizer.tools.R
import com.optimizer.tools.app.tv.LocalTelevisionUi
import com.optimizer.tools.core.ui.PillButton
import com.optimizer.tools.core.ui.PillStyle
import com.optimizer.tools.core.ui.ScreenHeader
import com.optimizer.tools.core.ui.ScreenIconButton
import com.optimizer.tools.domain.model.DevicePowerAction
import dev.adboptimizer.core.model.AppTarget

/** Pantalla de energía del destino activo; misma jerarquía que el prototipo. */
@Composable
internal fun PowerOptionsScreen(
    state: DashboardState,
    target: AppTarget,
    canModify: Boolean,
    onOpenLocalAdb: (() -> Unit)? = null,
    onBack: () -> Unit,
    onRequest: (DevicePowerAction) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    state.pendingPowerAction?.takeIf { it.target == target }?.let {
        DevicePowerConfirmation(it, onConfirm, onDismiss)
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
    ) {
        ScreenHeader(
            title = stringResource(R.string.dashboard_power_options),
            subtitle = stringResource(R.string.dashboard_power_options_body),
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
        if (canModify) {
            DevicePowerContent(
                state = state,
                enabled = true,
                onRequest = onRequest,
                showTitle = false,
                showDevice = false,
            )
        } else {
            Text(
                stringResource(R.string.power_local_blocked_body),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (onOpenLocalAdb != null) {
                PillButton(
                    text = stringResource(R.string.power_local_blocked_action),
                    onClick = onOpenLocalAdb,
                    style = PillStyle.Solid,
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        Text(
            stringResource(R.string.dashboard_power_note),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
        Spacer(Modifier.height(24.dp))
    }
}
