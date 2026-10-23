package com.optimizer.tools.domain.usecase

import com.optimizer.tools.domain.apk.ApkDescriptor
import com.optimizer.tools.domain.port.ApkInstaller
import com.optimizer.tools.domain.port.HostAppsSource
import dev.adboptimizer.core.adb.RemoteApkInstallOutcome
import java.io.File

/**
 * Extrae los APK base y splits de apps instaladas en este telefono y los envia al remoto.
 * La limpieza de archivos temporales queda garantizada aunque falle la instalacion.
 */
class InstallHostAppsUseCase(
    private val hostApps: HostAppsSource,
    private val installer: ApkInstaller,
) {
    suspend operator fun invoke(
        packageNames: List<String>,
        onProgress: (String) -> Unit,
    ): List<RemoteApkInstallOutcome> {
        require(packageNames.isNotEmpty()) { "Selecciona al menos una aplicacion de este telefono." }
        val stagedFiles = mutableListOf<File>()
        return try {
            val descriptors =
                packageNames.flatMapIndexed { index, packageName ->
                    onProgress("Copiando app local ${index + 1}/${packageNames.size}: $packageName")
                    hostApps.stageApks(packageName).also { staged ->
                        stagedFiles += staged.map(ApkDescriptor::file)
                    }
                }
            installer.installDescriptors(descriptors, onProgress)
        } finally {
            stagedFiles.forEach(File::delete)
        }
    }
}
