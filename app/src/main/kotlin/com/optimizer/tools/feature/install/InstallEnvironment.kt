package com.optimizer.tools.feature.install

import android.net.Uri
import com.optimizer.tools.domain.apk.InstallOptions
import com.optimizer.tools.domain.model.ShizukuLocalStatus
import dev.adboptimizer.core.adb.RemoteApkInstallOutcome
import dev.adboptimizer.core.model.ManagedApp
import kotlinx.coroutines.flow.Flow

/** Progreso de una instalación que puede seguir viva fuera del ViewModel. */
data class InstallProgress(
    val running: Boolean = false,
    val progress: String? = null,
    val message: String? = null,
)

interface InstallEnvironment {
    val progress: Flow<InstallProgress>

    fun isInstallRunning(): Boolean

    fun startInstall(operation: suspend ((String) -> Unit) -> List<RemoteApkInstallOutcome>)

    fun hasRemoteSession(): Boolean

    fun shizukuStatus(): ShizukuLocalStatus

    suspend fun hostApps(): List<ManagedApp>

    suspend fun installApks(
        uris: List<Uri>,
        options: InstallOptions,
        onProgress: (String) -> Unit,
    ): List<RemoteApkInstallOutcome>

    suspend fun installHostApps(
        packageNames: List<String>,
        onProgress: (String) -> Unit,
    ): List<RemoteApkInstallOutcome>
}
