package dev.adboptimizer.core.adb

import dev.adboptimizer.core.adb.scrcpy.ScrcpyProtocol
import dev.adboptimizer.core.adb.scrcpy.ScrcpyVideoStream
import dev.adboptimizer.core.shell.AdbErrorKind
import dev.adboptimizer.core.shell.AdbException
import dev.adboptimizer.core.shell.AdbShell
import dev.adboptimizer.core.shell.ShellQuote
import dev.adboptimizer.core.shell.ShellResult
import io.github.muntashirakon.adb.AbsAdbConnectionManager
import io.github.muntashirakon.adb.AdbAuthenticationFailedException
import io.github.muntashirakon.adb.AdbPairingRequiredException
import io.github.muntashirakon.adb.AdbStream
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.Closeable
import java.io.DataInputStream
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.io.InputStream
import java.security.PrivateKey
import java.security.cert.Certificate
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

internal class AdbManager(
    private val keys: AdbKeyMaterial,
) : AbsAdbConnectionManager() {
    override fun getPrivateKey(): PrivateKey = keys.privateKey

    override fun getCertificate(): Certificate = keys.certificate

    override fun getDeviceName(): String = "adb-optimizer"

    /**
     * `AbsAdbConnectionManager.close()` destruye `getPrivateKey()`. Como la identidad
     * RSA se comparte entre el pairing y la conexión, cerrar cualquiera de los dos
     * managers invalidaba la clave para el siguiente handshake TLS.
     * Cerramos solo el socket y conservamos la identidad.
     */
    override fun close() {
        runCatching { disconnect() }
    }
}

data class RemoteApkInstallGroup(
    val packageName: String,
    val files: List<File>,
    val installMultiple: Boolean,
    /** Opciones de `pm install`, ya validadas por quien construye el grupo. */
    val flags: List<String> = listOf("-r"),
)

data class RemoteApkInstallOutcome(
    val packageName: String,
    val success: Boolean,
    val output: String,
)

/**
 * Transporte ADB moderno basado en libadb-android:
 * - `secure = true`  → `adb-tls-connect` (depuracion inalambrica de Android 11+).
 * - `secure = false` → ADB clasico en el puerto 5555.
 *
 * El emparejamiento previo (codigo o QR) lo hace [AdbPairing] con el mismo [AdbManager].
 */
internal class LibAdbShell(
    private val manager: AdbManager,
    private val host: String,
    private val port: Int,
    private val secure: Boolean,
    private val io: CoroutineDispatcher = Dispatchers.IO,
    private val onConnectionLog: (String, Throwable?) -> Unit = { _, _ -> },
) : AdbShell,
    ApkInstallTransport,
    ScrcpyTransport,
    Closeable {
    override suspend fun installApkGroups(
        groups: List<RemoteApkInstallGroup>,
        onProgress: (String) -> Unit,
    ): List<RemoteApkInstallOutcome> =
        withContext(io) {
            require(groups.isNotEmpty()) { "No APKs selected for installation." }
            ensureConnected(INSTALL_TIMEOUT_MS)

            val outcomes = mutableListOf<RemoteApkInstallOutcome>()
            groups.forEachIndexed { groupIndex, group ->
                require(group.files.isNotEmpty()) { "No APK files supplied for ${group.packageName}." }
                val remotePaths =
                    group.files.map {
                        "/data/local/tmp/adboptimizer-${java.util.UUID.randomUUID()}.apk"
                    }
                try {
                    group.files.forEachIndexed { fileIndex, file ->
                        onProgress("Transfiriendo ${group.packageName}: APK ${fileIndex + 1}/${group.files.size}")
                        pushApk(file, remotePaths[fileIndex], group.packageName, onProgress)
                    }

                    onProgress("Instalando ${group.packageName} (${groupIndex + 1}/${groups.size})")
                    val (successful, output) =
                        if (group.installMultiple) {
                            installSplitGroup(group, remotePaths)
                        } else {
                            val installCommand =
                                "pm install ${group.flags.joinToString(" ")} " + ShellQuote.of(remotePaths.single())
                            val result = exec(installCommand, INSTALL_TIMEOUT_MS)
                            val text = result.output.trim().ifBlank { "Sin respuesta del Package Manager." }
                            val ok =
                                result.exitCode == 0 &&
                                    text.contains("Success", ignoreCase = true) &&
                                    !text.contains("Failure", ignoreCase = true)
                            ok to text
                        }
                    outcomes += RemoteApkInstallOutcome(group.packageName, successful, output)
                    onProgress(if (successful) "Instalado: ${group.packageName}" else "Falló: ${group.packageName} — $output")
                } finally {
                    runCatching {
                        exec("rm -f ${remotePaths.joinToString(" ") { ShellQuote.of(it) }}", 10_000)
                    }
                }
            }
            outcomes
        }

    /**
     * Instala base + splits con una sesion del Package Manager.
     * `pm install-multiple` no existe en el shell de todas las versiones de Android.
     */
    private suspend fun installSplitGroup(
        group: RemoteApkInstallGroup,
        remotePaths: List<String>,
    ): Pair<Boolean, String> {
        val totalBytes = group.files.sumOf(File::length)
        val create = exec("cmd package install-create ${group.flags.joinToString(" ")} -S $totalBytes", INSTALL_TIMEOUT_MS)
        val sessionId =
            SESSION_ID_REGEX.find(create.output)?.groupValues?.getOrNull(1)
                ?: return false to
                    create.output.trim().ifBlank {
                        "El Package Manager no creo la sesion de instalacion."
                    }

        var committed = false
        try {
            group.files.forEachIndexed { index, file ->
                val splitName = "${index}_${file.name.replace(INVALID_SESSION_NAME, "_")}"
                val write =
                    exec(
                        "cmd package install-write -S ${file.length()} $sessionId " +
                            "${ShellQuote.of(splitName)} ${ShellQuote.of(remotePaths[index])}",
                        INSTALL_TIMEOUT_MS,
                    )
                val writeOutput = write.output.trim()
                if (write.exitCode != 0 || writeOutput.contains("Failure", ignoreCase = true)) {
                    return false to
                        writeOutput.ifBlank {
                            "El Package Manager rechazo el APK ${index + 1} de ${group.files.size}."
                        }
                }
            }

            val commit = exec("cmd package install-commit $sessionId", INSTALL_TIMEOUT_MS)
            committed = commit.exitCode == 0
            val output = commit.output.trim().ifBlank { "Sin respuesta del Package Manager." }
            val ok =
                committed &&
                    output.contains("Success", ignoreCase = true) &&
                    !output.contains("Failure", ignoreCase = true)
            return ok to output
        } finally {
            if (!committed) {
                runCatching { exec("cmd package install-abandon $sessionId", 10_000) }
            }
        }
    }

    private fun pushApk(
        file: File,
        remotePath: String,
        packageName: String,
        onProgress: (String) -> Unit,
    ) {
        if (!file.isFile || file.length() <= 0L) throw IOException("APK file is empty or unavailable.")
        val totalBytes = file.length()
        val modifiedAtSeconds = (file.lastModified() / 1_000L).toInt()
        val buffer = ByteArray(SYNC_DATA_CHUNK_SIZE)
        var transferredBytes = 0L
        var lastReportedPercent = -1

        manager.openStream("sync:").use { stream ->
            val output = stream.openOutputStream()
            val inputStream = stream.openInputStream()
            AdbSyncProtocol.writeMessage(
                output,
                "SEND",
                "$remotePath,0644".toByteArray(Charsets.UTF_8),
            )
            FileInputStream(file).use { input ->
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    AdbSyncProtocol.writeData(output, buffer, read)
                    transferredBytes += read
                    val percent = (transferredBytes * 100 / totalBytes).toInt()
                    if (percent != lastReportedPercent) {
                        lastReportedPercent = percent
                        onProgress("Transfiriendo $packageName: $percent%")
                    }
                }
            }
            AdbSyncProtocol.writeDone(output, modifiedAtSeconds)
            output.flush()
            AdbSyncProtocol.readResponse(inputStream)
        }
    }

    /**
     * Sube scrcpy-server.jar, lanza el servidor con `tunnel_forward=true` y abre el socket de video
     * por `localabstract:`. El servidor sigue activo mientras la sesion este abierta.
     */
    override suspend fun openScrcpyVideo(
        serverJar: File,
        scid: Int,
        onLog: (String) -> Unit,
    ): ScrcpyVideoStream =
        withContext(io) {
            ensureConnected(SCRCPY_TIMEOUT_MS)
            pushApk(serverJar, SCRCPY_REMOTE_JAR, "scrcpy-server") {}

            val scidHex = "%08x".format(scid)
            val serverStream =
                manager.openStream(
                    "shell:CLASSPATH=$SCRCPY_REMOTE_JAR app_process / com.genymobile.scrcpy.Server $SCRCPY_VERSION " +
                        "scid=$scidHex log_level=info tunnel_forward=true audio=false control=true clipboard_autosync=false max_size=$SCRCPY_MAX_SIZE",
                )
            drainServerLogs(serverStream, onLog)

            val (videoStream, videoInput) =
                try {
                    connectScrcpyVideo("scrcpy_$scidHex", onLog)
                } catch (error: Exception) {
                    runCatching { serverStream.close() }
                    throw error
                }
            val controlStream =
                try {
                    manager.openStream("localabstract:scrcpy_$scidHex")
                } catch (error: Exception) {
                    runCatching { videoStream.close() }
                    runCatching { serverStream.close() }
                    throw error
                }
            manager.setTimeout(0, TimeUnit.MILLISECONDS)
            val input = DataInputStream(videoInput)
            try {
                val info = ScrcpyProtocol.readVideoInfo(input)
                onLog("Video ${info.codec} ${info.width}x${info.height} · ${info.deviceName}")
                ScrcpyVideoStream(info, input, listOf(videoStream, controlStream, serverStream), controlStream.openOutputStream())
            } catch (error: IOException) {
                runCatching { videoStream.close() }
                runCatching { controlStream.close() }
                runCatching { serverStream.close() }
                throw mapError(error)
            }
        }

    /**
     * El servidor envía un byte dummy solo cuando su socket ya escucha. Hasta entonces adbd
     * rechaza la apertura o cierra el stream, así que se reintenta como hace el cliente oficial.
     * Devuelve el stream y su InputStream: el byte dummy ya fue consumido de ese InputStream.
     */
    private fun connectScrcpyVideo(
        socketName: String,
        onLog: (String) -> Unit,
    ): Pair<AdbStream, InputStream> {
        var lastError: IOException? = null
        repeat(SCRCPY_CONNECT_ATTEMPTS) { attempt ->
            try {
                val stream = manager.openStream("localabstract:$socketName")
                val input = stream.openInputStream()
                if (input.read() >= 0) return stream to input
                stream.close()
            } catch (error: IOException) {
                lastError = error
            }
            onLog("Esperando socket de video de scrcpy (intento ${attempt + 1})")
            Thread.sleep(SCRCPY_CONNECT_DELAY_MS)
        }
        throw AdbException(
            "El servidor scrcpy no aceptó la conexión de video.",
            AdbErrorKind.SHELL,
            lastError,
        )
    }

    private fun drainServerLogs(
        stream: AdbStream,
        onLog: (String) -> Unit,
    ) {
        thread(name = "scrcpy-server-log", isDaemon = true) {
            runCatching {
                stream.openInputStream().bufferedReader().forEachLine { line -> onLog("server: $line") }
            }
        }
    }

    private fun sanitizeCommand(command: String): String {
        val trimmed = command.trim()
        val firstWord = trimmed.substringBefore(' ')
        val secondWord = trimmed.substringAfter(' ').substringBefore(' ').takeIf { it.isNotBlank() }
        val detail = if (secondWord != null && !secondWord.startsWith('-')) "$firstWord $secondWord" else firstWord
        return "$detail (len=${command.length})"
    }

    override suspend fun exec(
        command: String,
        timeoutMs: Long,
    ): ShellResult =
        runAdbBlockingOperation(timeoutMs, io, ::close) {
            ensureConnected(timeoutMs)
            val startedAt = System.nanoTime()
            onConnectionLog("Executing remote command: ${sanitizeCommand(command)} (timeout=${timeoutMs}ms)", null)

            val raw =
                try {
                    manager.openStream("shell,v2,raw:$command").use { stream ->
                        readShellResponse { chunk ->
                            try {
                                stream.read(chunk, 0, chunk.size)
                            } catch (error: IOException) {
                                // libadb senala el EOF normal con IOException("Stream closed.")
                                // en lugar de devolver -1. No es un fallo de la conexion.
                                if (error.message?.contains("Stream closed", ignoreCase = true) == true) {
                                    -1
                                } else {
                                    throw error
                                }
                            }
                        }
                    }
                } catch (error: IOException) {
                    onConnectionLog("Remote command failed: ${sanitizeCommand(command)} (${error.message})", error)
                    close()
                    throw mapError(error)
                } catch (error: AdbPairingRequiredException) {
                    onConnectionLog("Remote command requires pairing: ${sanitizeCommand(command)}", error)
                    close()
                    throw AdbException("El dispositivo requiere vinculacion.", AdbErrorKind.PAIRING, error)
                }

            val parsed = ShellV2Parser.parse(raw)
            val elapsed = (System.nanoTime() - startedAt) / 1_000_000

            // Si el dispositivo no soporta shell v2, la salida llega sin framing.
            val result =
                if (parsed == null) {
                    ShellResult(0, String(raw, Charsets.UTF_8), "", elapsed)
                } else {
                    ShellResult(parsed.exitCode, parsed.stdout, parsed.stderr, elapsed)
                }
            onConnectionLog(
                "Remote command completed: ${sanitizeCommand(
                    command,
                )} exit=${result.exitCode} elapsed=${elapsed}ms stdout=${result.stdout.length} chars stderr=${result.stderr.length} chars",
                null,
            )
            result
        }

    private fun ensureConnected(timeoutMs: Long) {
        manager.setTimeout(timeoutMs, TimeUnit.MILLISECONDS)
        if (manager.isConnected) {
            onConnectionLog("ADB transport already connected", null)
            return
        }

        try {
            onConnectionLog("Opening ${if (secure) "TLS" else "classic ADB"} transport to $host:$port", null)
            // Importante: connectTls/connectTcp de libadb hacen autoConnect por mDNS e
            // ignoran la IP/puerto elegidos. connect(host, port) abre directamente el
            // destino; el upgrade a TLS lo negocia adbd con STLS.
            manager.setApi(if (secure) 30 else 1)
            val connected = manager.connect(host, port)
            if (!connected) {
                onConnectionLog("ADB transport returned false for $host:$port", null)
                throw AdbException("No se pudo conectar a $host:$port", AdbErrorKind.OFFLINE)
            }
            onConnectionLog("ADB transport connected to $host:$port", null)
        } catch (error: AdbAuthenticationFailedException) {
            onConnectionLog("ADB authentication rejected by $host:$port", error)
            throw AdbException(
                "Autoriza la depuracion en la pantalla del dispositivo.",
                AdbErrorKind.UNAUTHORIZED,
                error,
            )
        } catch (error: AdbPairingRequiredException) {
            onConnectionLog("Pairing is required by $host:$port", error)
            throw AdbException(
                "El dispositivo requiere vinculacion (pairing).",
                AdbErrorKind.PAIRING,
                error,
            )
        } catch (error: IOException) {
            onConnectionLog("ADB I/O failed for $host:$port: ${rootCauseMessage(error)}", error)
            throw mapError(error)
        } catch (error: InterruptedException) {
            onConnectionLog("ADB connection interrupted for $host:$port", error)
            Thread.currentThread().interrupt()
            throw AdbException("Conexion interrumpida.", AdbErrorKind.TIMEOUT, error)
        }
    }

    private fun mapError(error: IOException): AdbException {
        val message = rootCauseMessage(error)
        val kind =
            when {
                message.contains("unauthorized", true) -> AdbErrorKind.UNAUTHORIZED
                message.contains("pair", true) -> AdbErrorKind.PAIRING
                message.contains("timed out", true) -> AdbErrorKind.TIMEOUT
                else -> AdbErrorKind.OFFLINE
            }
        val userMessage =
            when {
                message.contains("ECONNREFUSED", true) || message.contains("Connection refused", true) -> {
                    "El dispositivo rechazó la conexión en $host:$port. El puerto TLS pudo cambiar o el servicio de depuración dejó de estar activo; vuelve a descubrirlo."
                }

                message.isNotBlank() -> {
                    message
                }

                else -> {
                    "No se pudo hablar con el dispositivo en $host:$port."
                }
            }
        return AdbException(userMessage, kind, error)
    }

    private fun rootCauseMessage(error: Throwable): String =
        generateSequence(error) { it.cause }
            .mapNotNull { it.message?.takeIf(String::isNotBlank) }
            .lastOrNull()
            .orEmpty()

    override fun close() {
        runCatching { manager.disconnect() }
        runCatching { manager.close() }
    }

    private companion object {
        const val INSTALL_TIMEOUT_MS = 180_000L
        val SESSION_ID_REGEX = Regex("\\[(\\d+)]")
        val INVALID_SESSION_NAME = Regex("[^A-Za-z0-9._-]")
        const val SYNC_DATA_CHUNK_SIZE = 32 * 1024
        const val SCRCPY_VERSION = "5.0.1"
        const val SCRCPY_REMOTE_JAR = "/data/local/tmp/scrcpy-server.jar"
        const val SCRCPY_MAX_SIZE = 1280
        const val SCRCPY_CONNECT_ATTEMPTS = 100
        const val SCRCPY_CONNECT_DELAY_MS = 100L
        const val SCRCPY_TIMEOUT_MS = 60_000L
    }
}
