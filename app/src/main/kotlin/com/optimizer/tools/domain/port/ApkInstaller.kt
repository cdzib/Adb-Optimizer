package com.optimizer.tools.domain.port

import com.optimizer.tools.domain.apk.ApkDescriptor
import dev.adboptimizer.core.adb.RemoteApkInstallOutcome

/** Envia APKs ya preparados al dispositivo remoto. */
interface ApkInstaller {
    suspend fun installDescriptors(
        descriptors: List<ApkDescriptor>,
        onProgress: (String) -> Unit,
    ): List<RemoteApkInstallOutcome>
}
