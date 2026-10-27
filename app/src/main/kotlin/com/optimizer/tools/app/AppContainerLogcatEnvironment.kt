package com.optimizer.tools.app

import com.optimizer.tools.data.AppContainer
import com.optimizer.tools.feature.logcat.LogcatEntry
import com.optimizer.tools.feature.logcat.LogcatEnvironment
import dev.adboptimizer.core.model.DeviceInfo
import java.io.IOException

/** Adaptador de composición para el visor de registro. */
internal class AppContainerLogcatEnvironment(
    private val container: AppContainer,
) : LogcatEnvironment {
    override fun activeDevice(): DeviceInfo? = container.device

    override suspend fun read(limit: Int): List<LogcatEntry> =
        container.logcat?.read(limit) ?: throw IOException("No hay un dispositivo remoto conectado.")
}
