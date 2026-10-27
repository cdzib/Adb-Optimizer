package com.optimizer.tools.feature.doze

import dev.adboptimizer.core.model.DeviceInfo

/** Puertos de la pantalla Doze y energia. */
interface DozeEnvironment {
    fun activeDevice(): DeviceInfo?

    suspend fun status(): DozeStatus

    suspend fun run(action: DozeAction): DozeStatus
}
