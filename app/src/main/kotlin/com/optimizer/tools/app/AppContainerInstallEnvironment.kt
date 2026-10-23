package com.optimizer.tools.app

import android.net.Uri
import com.optimizer.tools.data.AppContainer
import com.optimizer.tools.domain.model.ShizukuLocalStatus
import com.optimizer.tools.feature.install.InstallEnvironment
import com.optimizer.tools.feature.install.InstallProgress
import dev.adboptimizer.core.adb.RemoteApkInstallOutcome
import dev.adboptimizer.core.model.ManagedApp
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Adaptador de composición para Instalar aplicaciones. */
internal class AppContainerInstallEnvironment(
    private val container: AppContainer,
) : InstallEnvironment {
    override val progress: Flow<InstallProgress> =
        container.apkInstallManager.state.map { state ->
            InstallProgress(
                running = state.running,
                progress = state.progress,
                message = state.message,
            )
        }

    override fun isInstallRunning(): Boolean = container.apkInstallManager.isRunning()

    override fun startInstall(operation: suspend ((String) -> Unit) -> List<RemoteApkInstallOutcome>) {
        container.apkInstallManager.start(operation)
    }

    override fun hasRemoteSession(): Boolean = container.sessionStore.activeId() != null

    override fun shizukuStatus(): ShizukuLocalStatus = container.shizukuLocalAccess.status()

    override suspend fun hostApps(): List<ManagedApp> = container.localApps.list(includeSystem = false)

    override suspend fun installApks(
        uris: List<Uri>,
        onProgress: (String) -> Unit,
    ): List<RemoteApkInstallOutcome> = container.installApks(uris, onProgress)

    override suspend fun installHostApps(
        packageNames: List<String>,
        onProgress: (String) -> Unit,
    ): List<RemoteApkInstallOutcome> = container.installHostAppsUseCase(packageNames, onProgress)
}
