package com.optimizer.tools.feature.inspector

import dev.adboptimizer.core.model.DeviceInfo

/** Puertos del inspector de apps. */
interface InspectorEnvironment {
    fun activeDevice(): DeviceInfo?

    suspend fun inspect(packageName: String): AppInspection
}
