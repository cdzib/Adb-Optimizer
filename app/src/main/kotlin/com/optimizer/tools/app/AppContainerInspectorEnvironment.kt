package com.optimizer.tools.app

import com.optimizer.tools.data.AppContainer
import com.optimizer.tools.feature.inspector.AppInspection
import com.optimizer.tools.feature.inspector.InspectorEnvironment
import java.io.IOException

/** Adaptador de composición para el inspector de apps. */
internal class AppContainerInspectorEnvironment(
    private val container: AppContainer,
) : InspectorEnvironment {
    override fun hasDevice(): Boolean = container.hasPrivilegedDevice()

    override suspend fun inspect(packageName: String): AppInspection =
        container.appInspector?.inspect(packageName) ?: throw IOException("No hay un dispositivo remoto conectado.")
}
