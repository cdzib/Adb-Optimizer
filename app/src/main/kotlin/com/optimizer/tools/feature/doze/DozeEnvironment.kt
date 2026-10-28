package com.optimizer.tools.feature.doze

/** Puertos de la pantalla Doze y energia. */
interface DozeEnvironment {
    fun hasDevice(): Boolean

    suspend fun status(): DozeStatus

    suspend fun run(action: DozeAction): DozeStatus
}
