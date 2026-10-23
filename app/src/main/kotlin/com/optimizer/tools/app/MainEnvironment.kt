package com.optimizer.tools.app

import dev.adboptimizer.core.model.ActionSpec
import dev.adboptimizer.core.model.DeviceInfo
import kotlinx.coroutines.flow.Flow

/** Estado de sesiones que necesita la composición raíz. */
data class MainSessionState(
    val devices: List<DeviceInfo> = emptyList(),
    val activeDevice: DeviceInfo? = null,
)

interface MainEnvironment {
    val sessions: Flow<MainSessionState>
    val actions: List<ActionSpec>

    fun activeDevice(): DeviceInfo?

    fun activeSessionId(): String?

    fun connectedDevices(): List<DeviceInfo>

    fun startSessionService(device: DeviceInfo)
}
