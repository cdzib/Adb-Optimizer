package com.optimizer.tools.feature.logcat

/** Puertos del visor de registro. */
interface LogcatEnvironment {
    fun hasDevice(): Boolean

    suspend fun read(
        limit: Int,
        since: String? = null,
    ): List<LogcatEntry>
}
