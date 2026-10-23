package com.optimizer.tools.app

import com.optimizer.tools.data.AppContainer
import com.optimizer.tools.data.DevicePowerRepository
import com.optimizer.tools.domain.model.AppSettings
import com.optimizer.tools.domain.model.DevicePowerAction
import com.optimizer.tools.feature.dashboard.DashboardEnvironment
import com.optimizer.tools.feature.dashboard.DashboardSessionState
import dev.adboptimizer.core.model.AppTarget
import dev.adboptimizer.core.model.DeviceInfo
import dev.adboptimizer.core.model.DeviceMetrics
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map

/** Adaptador de composición para el inicio. */
internal class AppContainerDashboardEnvironment(
    private val container: AppContainer,
) : DashboardEnvironment {
    override val sessions: Flow<DashboardSessionState> =
        container.sessionStore.state
            .map { sessions ->
                DashboardSessionState(
                    devices = sessions.devices,
                    activeDevice = sessions.devices.firstOrNull { it.id == sessions.activeId },
                )
            }.distinctUntilChanged()

    override val settings: Flow<AppSettings> = container.settingsRepository.settings

    override val ticks: Flow<Unit> =
        flow {
            while (true) {
                delay(1_000L)
                emit(Unit)
            }
        }

    override fun connectedDevices(): List<DeviceInfo> = container.connectedDevices()

    override fun activeDevice(): DeviceInfo? = container.device

    override suspend fun localDevice(): DeviceInfo? = container.localDevice.readInfo()

    override suspend fun localMetrics(): DeviceMetrics = container.localDevice.readMetrics()

    override suspend fun remoteMetrics(): DeviceMetrics? = container.metrics()

    override suspend fun executePowerAction(
        target: AppTarget,
        deviceId: String,
        action: DevicePowerAction,
    ) {
        val shell =
            when (target) {
                AppTarget.LOCAL -> {
                    check(container.localDevice.readInfo().id == deviceId) { "El dispositivo local cambió." }
                    container.shizukuLocalAccess.requireCommandPermission()
                    container.localShell
                }

                AppTarget.REMOTE -> {
                    container.sessionStore.session(deviceId)?.shell
                        ?: error("La sesión ADB ya no está disponible.")
                }
            }
        DevicePowerRepository(shell).execute(action)
        if (target == AppTarget.REMOTE) {
            container.disconnectDevice(deviceId)
            if (container.sessionStore.isEmpty()) container.stopSessionService() else container.refreshSessionNotification()
        }
    }
}
