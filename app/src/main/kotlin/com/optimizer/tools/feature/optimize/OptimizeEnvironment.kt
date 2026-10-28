package com.optimizer.tools.feature.optimize

import com.optimizer.tools.domain.model.ShizukuLocalStatus
import dev.adboptimizer.core.model.DeviceInfo
import kotlinx.coroutines.flow.Flow

/**
 * Dependencias estrechas de Optimize. La composicion (`app/`) la implementa
 * sobre `AppContainer`; el ViewModel no conoce el contenedor.
 */
interface OptimizeEnvironment {
    val remoteAvailable: Flow<Boolean>

    fun hasRemoteSession(): Boolean

    fun remoteDevice(): DeviceInfo?

    fun remoteEngine(): OptimizeEngine?

    fun localEngine(): OptimizeEngine

    suspend fun localDevice(): DeviceInfo?

    fun localShizukuStatus(): ShizukuLocalStatus

    fun localPrivilegeReady(): Boolean

    /** READY si Shizuku o la sesión ADB local pueden ejecutar recetas. */
    fun localPrivilegeStatus(): ShizukuLocalStatus
}
