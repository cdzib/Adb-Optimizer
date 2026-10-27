package com.optimizer.tools.app

import android.net.Uri
import com.optimizer.tools.data.AppContainer
import com.optimizer.tools.data.dpc.ApkChecksum
import com.optimizer.tools.domain.dpc.DpcMode
import com.optimizer.tools.domain.dpc.DpcPreflight
import com.optimizer.tools.domain.dpc.DpcReceiver
import com.optimizer.tools.domain.dpc.DpcResult
import com.optimizer.tools.feature.dpc.DpcEnvironment
import dev.adboptimizer.core.adb.RemoteApkInstallOutcome
import java.io.File

/** Adaptador de composición para la feature DPC. */
internal class AppContainerDpcEnvironment(
    private val container: AppContainer,
) : DpcEnvironment {
    private val dpc get() = container.dpc ?: error("No hay un dispositivo remoto conectado.")

    override fun hasRemoteDevice(): Boolean = container.dpc != null

    override fun activeSessionId(): String? = container.sessionStore.activeId()

    override suspend fun preflight(mode: DpcMode): DpcPreflight = dpc.preflight(mode)

    override suspend fun receivers(packageName: String?): List<DpcReceiver> = dpc.receivers(packageName)

    override suspend fun setOwner(
        component: String,
        mode: DpcMode,
    ): DpcResult = dpc.setOwner(component, mode)

    override suspend fun removeOwner(component: String): DpcResult = dpc.removeOwner(component)

    override suspend fun copyToCache(
        uri: Uri,
        prefix: String,
    ): File = container.copyToCache(uri, prefix)

    override suspend fun installFiles(
        files: List<File>,
        onProgress: (String) -> Unit,
    ): List<RemoteApkInstallOutcome> = container.installApkFiles(files, onProgress = onProgress)

    override suspend fun installApks(
        uris: List<Uri>,
        onProgress: (String) -> Unit,
    ): List<RemoteApkInstallOutcome> = container.installApks(uris, onProgress = onProgress)

    override suspend fun download(
        url: String,
        onProgress: (String) -> Unit,
    ): File = container.apkDownloader.download(url, onProgress)

    override fun checksum(file: File): String = ApkChecksum.sha256Base64(file)
}
