package com.optimizer.tools.feature.dashboard

import com.optimizer.tools.domain.model.AppSettings
import com.optimizer.tools.domain.model.DevicePowerAction
import dev.adboptimizer.core.model.AppTarget
import dev.adboptimizer.core.model.DeviceInfo
import dev.adboptimizer.core.model.DeviceMetrics
import kotlinx.coroutines.flow.Flow

/** Sesiones y datos de dispositivo que consume el inicio. */
data class DashboardSessionState(
    val devices: List<DeviceInfo> = emptyList(),
    val activeDevice: DeviceInfo? = null,
)

interface DashboardEnvironment {
    val sessions: Flow<DashboardSessionState>
    val settings: Flow<AppSettings>

    /** Pulso periódico que dispara la actualización automática cuando está activa. */
    val ticks: Flow<Unit>

    fun connectedDevices(): List<DeviceInfo>

    fun activeDevice(): DeviceInfo?

    suspend fun localDevice(): DeviceInfo?

    suspend fun localMetrics(): DeviceMetrics

    suspend fun remoteMetrics(): DeviceMetrics?

    suspend fun executePowerAction(
        target: AppTarget,
        deviceId: String,
        action: DevicePowerAction,
    )
}
