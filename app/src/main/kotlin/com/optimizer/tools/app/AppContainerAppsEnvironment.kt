package com.optimizer.tools.app

import com.optimizer.tools.data.AppContainer
import com.optimizer.tools.domain.model.AppSettings
import com.optimizer.tools.feature.apps.AppsEnvironment
import dev.adboptimizer.core.model.ActionSpec
import dev.adboptimizer.core.model.AppAction
import dev.adboptimizer.core.model.AppActionResult
import dev.adboptimizer.core.model.AppTarget
import dev.adboptimizer.core.model.DeviceInfo
import dev.adboptimizer.core.model.ManagedApp
import dev.adboptimizer.core.model.StepResult
import kotlinx.coroutines.flow.Flow

/** Adaptador de composición para la pantalla Apps. */
internal class AppContainerAppsEnvironment(
    private val container: AppContainer,
) : AppsEnvironment {
    override val settings: Flow<AppSettings> = container.settingsRepository.settings

    override val actions: List<ActionSpec> get() = container.actions

    override fun activeSessionId(): String? = container.sessionStore.activeId()

    override fun hasRemoteApps(): Boolean = container.remoteApps() != null

    override fun privileged(target: AppTarget): Boolean =
        when (target) {
            AppTarget.REMOTE -> hasRemoteApps()
            AppTarget.LOCAL -> container.localPrivilegeReady()
        }

    override suspend fun localDevice(): DeviceInfo? = container.localDevice.readInfo()

    override suspend fun listApps(target: AppTarget): List<ManagedApp> =
        when (target) {
            AppTarget.REMOTE -> container.remoteApps()?.list(includeSystem = true).orEmpty()
            AppTarget.LOCAL -> container.localApps.list(includeSystem = true)
        }

    override suspend fun appDetails(
        target: AppTarget,
        packageName: String,
    ): ManagedApp? =
        when (target) {
            AppTarget.REMOTE -> container.remoteApps()?.details(packageName)
            AppTarget.LOCAL -> container.localApps.details(packageName)
        }

    override suspend fun runAction(
        target: AppTarget,
        packageName: String,
        action: AppAction,
    ): AppActionResult =
        when (target) {
            AppTarget.REMOTE -> {
                container.remoteApps()?.run(packageName, action)
                    ?: AppActionResult(false, "No hay dispositivo remoto conectado.")
            }

            AppTarget.LOCAL -> {
                container.localApps.run(packageName, action)
            }
        }

    override suspend fun runAdvancedAction(
        target: AppTarget,
        action: ActionSpec,
        packageName: String,
    ): StepResult =
        when (target) {
            AppTarget.REMOTE -> {
                val device = container.device ?: error("No hay dispositivo remoto conectado.")
                container.engine?.runAction(action, device, packageName)
                    ?: error("No hay una sesión ADB activa.")
            }

            AppTarget.LOCAL -> {
                container.runLocalAction(action, packageName)
            }
        }
}
