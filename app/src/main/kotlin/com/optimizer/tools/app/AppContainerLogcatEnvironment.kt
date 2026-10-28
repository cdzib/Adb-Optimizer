package com.optimizer.tools.app

import com.optimizer.tools.data.AppContainer
import com.optimizer.tools.feature.logcat.LogcatEntry
import com.optimizer.tools.feature.logcat.LogcatEnvironment
import java.io.IOException

/** Adaptador de composición para el visor de registro. */
internal class AppContainerLogcatEnvironment(
    private val container: AppContainer,
) : LogcatEnvironment {
    override fun hasDevice(): Boolean = container.hasPrivilegedDevice()

    override suspend fun read(
        limit: Int,
        since: String?,
    ): List<LogcatEntry> = container.logcat?.read(limit, since) ?: throw IOException("No hay un dispositivo remoto conectado.")
}
