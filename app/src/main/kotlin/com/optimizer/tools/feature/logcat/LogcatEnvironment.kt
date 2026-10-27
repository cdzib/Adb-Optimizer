package com.optimizer.tools.feature.logcat

import dev.adboptimizer.core.model.DeviceInfo

/** Puertos del visor de registro. */
interface LogcatEnvironment {
    fun activeDevice(): DeviceInfo?

    suspend fun read(limit: Int): List<LogcatEntry>
}
