package com.optimizer.tools.feature.dpc

import android.net.Uri
import com.optimizer.tools.domain.dpc.DpcMode
import com.optimizer.tools.domain.dpc.DpcPreflight
import com.optimizer.tools.domain.dpc.DpcReceiver
import com.optimizer.tools.domain.dpc.DpcResult
import dev.adboptimizer.core.adb.RemoteApkInstallOutcome
import java.io.File

/** Puertos de la feature DPC: dispositivo remoto, APK y administración. */
interface DpcEnvironment {
    fun hasRemoteDevice(): Boolean

    fun activeSessionId(): String?

    suspend fun preflight(mode: DpcMode): DpcPreflight

    suspend fun receivers(packageName: String?): List<DpcReceiver>

    suspend fun setOwner(
        component: String,
        mode: DpcMode,
    ): DpcResult

    suspend fun removeOwner(component: String): DpcResult

    suspend fun copyToCache(
        uri: Uri,
        prefix: String,
    ): File

    suspend fun installFiles(
        files: List<File>,
        onProgress: (String) -> Unit,
    ): List<RemoteApkInstallOutcome>

    suspend fun installApks(
        uris: List<Uri>,
        onProgress: (String) -> Unit,
    ): List<RemoteApkInstallOutcome>

    suspend fun download(
        url: String,
        onProgress: (String) -> Unit,
    ): File

    fun checksum(file: File): String
}
