package com.optimizer.tools.app

import com.optimizer.tools.core.ui.UiText
import dev.adboptimizer.core.model.ActionSpec
import dev.adboptimizer.core.model.AppTarget
import dev.adboptimizer.core.model.DeviceInfo

data class UiState(
    val connected: DeviceInfo? = null,
    val connectedDevices: List<DeviceInfo> = emptyList(),
    val activeTarget: AppTarget = AppTarget.LOCAL,
    val actions: List<ActionSpec> = emptyList(),
    val errorKey: String? = null,
    val errorDetail: UiText? = null,
    val messageKey: String? = null,
)
