package com.optimizer.tools.app

import com.optimizer.tools.data.AppContainer
import com.optimizer.tools.feature.doze.DozeAction
import com.optimizer.tools.feature.doze.DozeEnvironment
import com.optimizer.tools.feature.doze.DozeStatus
import java.io.IOException

/** Adaptador de composición para Doze y energía. */
internal class AppContainerDozeEnvironment(
    private val container: AppContainer,
) : DozeEnvironment {
    override fun hasDevice(): Boolean = container.hasPrivilegedDevice()

    override suspend fun status(): DozeStatus = container.doze?.status() ?: throw IOException("No hay un dispositivo remoto conectado.")

    override suspend fun run(action: DozeAction): DozeStatus =
        container.doze?.run(action) ?: throw IOException("No hay un dispositivo remoto conectado.")
}
