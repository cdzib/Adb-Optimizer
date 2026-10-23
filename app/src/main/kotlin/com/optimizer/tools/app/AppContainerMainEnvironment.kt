package com.optimizer.tools.app

import com.optimizer.tools.data.AppContainer
import dev.adboptimizer.core.model.ActionSpec
import dev.adboptimizer.core.model.DeviceInfo
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/** Adaptador de composición para el estado global de la app. */
internal class AppContainerMainEnvironment(
    private val container: AppContainer,
) : MainEnvironment {
    override val sessions: Flow<MainSessionState> =
        container.sessionStore.state
            .map { sessions ->
                MainSessionState(
                    devices = sessions.devices,
                    activeDevice = sessions.devices.firstOrNull { it.id == sessions.activeId },
                )
            }.distinctUntilChanged()

    override val actions: List<ActionSpec> get() = container.actions

    override fun activeDevice(): DeviceInfo? = container.device

    override fun activeSessionId(): String? = container.sessionStore.activeId()

    override fun connectedDevices(): List<DeviceInfo> = container.connectedDevices()

    override fun startSessionService(device: DeviceInfo) = container.startSessionService(device)
}
