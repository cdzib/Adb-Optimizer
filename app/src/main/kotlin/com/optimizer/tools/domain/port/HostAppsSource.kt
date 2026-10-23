package com.optimizer.tools.domain.port

import com.optimizer.tools.domain.apk.ApkDescriptor
import dev.adboptimizer.core.model.ManagedApp

/** Fuente de aplicaciones instaladas en este telefono. */
interface HostAppsSource {
    suspend fun list(includeSystem: Boolean): List<ManagedApp>

    suspend fun stageApks(packageName: String): List<ApkDescriptor>
}
