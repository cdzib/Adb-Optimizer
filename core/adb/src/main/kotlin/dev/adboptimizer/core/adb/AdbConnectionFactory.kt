package dev.adboptimizer.core.adb

import android.content.Context
import dev.adboptimizer.core.adb.scrcpy.ScrcpyVideoStream
import dev.adboptimizer.core.shell.AdbShell
import java.io.Closeable
import java.io.File
import java.io.IOException

interface ApkInstallTransport {
    suspend fun installApkGroups(
        groups: List<RemoteApkInstallGroup>,
        onProgress: (String) -> Unit = {},
    ): List<RemoteApkInstallOutcome>
}

interface ScrcpyTransport {
    suspend fun openScrcpyVideo(
        serverJar: File,
        scid: Int,
        onLog: (String) -> Unit = {},
    ): ScrcpyVideoStream
}

/**
 * Fachada publica de core:adb. Evita que los consumidores dependan de libadb
 * o construyan [AdbManager] directamente.
 */
object AdbConnectionFactory {
    fun createShell(
        keys: AdbKeyMaterial,
        host: String,
        port: Int,
        secure: Boolean,
        onLog: (String, Throwable?) -> Unit = { _, _ -> },
    ): AdbShell =
        LibAdbShell(
            manager = AdbManager(keys),
            host = host,
            port = port,
            secure = secure,
            onConnectionLog = onLog,
        )

    suspend fun installApkGroups(
        shell: AdbShell,
        groups: List<RemoteApkInstallGroup>,
        onProgress: (String) -> Unit = {},
    ): List<RemoteApkInstallOutcome> {
        val transport =
            shell as? ApkInstallTransport
                ?: throw IOException("La sesion ADB no admite instalacion de APKs.")
        return transport.installApkGroups(groups, onProgress)
    }

    suspend fun openScrcpyVideo(
        shell: AdbShell,
        serverJar: File,
        scid: Int,
        onLog: (String) -> Unit = {},
    ): ScrcpyVideoStream {
        val transport =
            shell as? ScrcpyTransport
                ?: throw IOException("La sesion ADB no admite scrcpy.")
        return transport.openScrcpyVideo(serverJar, scid, onLog)
    }

    fun createPairing(
        context: Context,
        keys: AdbKeyMaterial,
    ): PairingSession {
        val manager = AdbManager(keys)
        return PairingSession(manager, AdbPairing(context))
    }
}

/** Vinculacion con codigo o password de QR; cierra el manager al terminar. */
class PairingSession internal constructor(
    private val manager: AdbManager,
    private val pairing: AdbPairing,
) : Closeable {
    suspend fun pair(
        host: String,
        port: Int,
        password: String,
    ): Result<Unit> = pairing.pair(manager, host, port, password)

    override fun close() {
        runCatching { manager.close() }
    }
}
