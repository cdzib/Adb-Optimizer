package com.optimizer.tools.app

import com.optimizer.tools.data.AppContainer
import com.optimizer.tools.feature.inspector.AppInspection
import com.optimizer.tools.feature.inspector.InspectorEnvironment
import dev.adboptimizer.core.model.DeviceInfo
import java.io.IOException

/** Adaptador de composición para el inspector de apps. */
internal class AppContainerInspectorEnvironment(
    private val container: AppContainer,
) : InspectorEnvironment {
    override fun activeDevice(): DeviceInfo? = container.device

    override suspend fun inspect(packageName: String): AppInspection =
        container.appInspector?.inspect(packageName) ?: throw IOException("No hay un dispositivo remoto conectado.")
}
