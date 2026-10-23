package com.optimizer.tools.app

import com.optimizer.tools.data.AppContainer
import com.optimizer.tools.data.remote.RemoteControlRepository
import com.optimizer.tools.domain.model.RemoteKey
import com.optimizer.tools.feature.remote.RemoteControlEnvironment
import dev.adboptimizer.core.model.DeviceInfo
import java.io.IOException

/** Adaptador de composición para Control remoto. */
internal class AppContainerRemoteControlEnvironment(
    private val container: AppContainer,
) : RemoteControlEnvironment {
    override fun activeDevice(): DeviceInfo? = container.device

    override suspend fun sendKey(key: RemoteKey) {
        val shell = container.shell ?: throw IOException("No hay un dispositivo remoto conectado.")
        RemoteControlRepository(shell).press(key)
    }

    override suspend fun probeMirror(onLog: (String) -> Unit): String = container.probeScrcpyVideo(onLog)
}
