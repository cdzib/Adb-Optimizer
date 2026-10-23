package com.optimizer.tools.feature.apps

import com.optimizer.tools.domain.model.AppSettings
import dev.adboptimizer.core.model.ActionSpec
import dev.adboptimizer.core.model.AppAction
import dev.adboptimizer.core.model.AppActionResult
import dev.adboptimizer.core.model.AppTarget
import dev.adboptimizer.core.model.DeviceInfo
import dev.adboptimizer.core.model.ManagedApp
import dev.adboptimizer.core.model.StepResult
import kotlinx.coroutines.flow.Flow

/** Puertos de la pantalla Apps para el dispositivo local o remoto. */
interface AppsEnvironment {
    val settings: Flow<AppSettings>
    val actions: List<ActionSpec>

    fun activeSessionId(): String?

    fun hasRemoteApps(): Boolean

    suspend fun localDevice(): DeviceInfo?

    suspend fun listApps(target: AppTarget): List<ManagedApp>

    suspend fun appDetails(
        target: AppTarget,
        packageName: String,
    ): ManagedApp?

    suspend fun runAction(
        target: AppTarget,
        packageName: String,
        action: AppAction,
    ): AppActionResult

    suspend fun runAdvancedAction(
        target: AppTarget,
        action: ActionSpec,
        packageName: String,
    ): StepResult
}
