package com.optimizer.tools.feature.localadb.data

import android.content.Context
import android.os.SystemClock
import android.provider.Settings
import com.optimizer.tools.domain.logging.AppLogger
import com.optimizer.tools.feature.localadb.domain.LocalAdbException
import com.optimizer.tools.feature.localadb.domain.LocalAdbRepository
import com.optimizer.tools.feature.localadb.domain.ShellCommandResult
import dadb.Dadb
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketTimeoutException
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/**
 * Sesión ADB contra el propio dispositivo en 127.0.0.1:5555.
 * Todo el I/O se ejecuta en [io]; la UI nunca bloquea el hilo principal.
 */
class DadbLocalAdbRepository(
    context: Context,
    private val io: CoroutineDispatcher = Dispatchers.IO,
    private val logger: AppLogger? = null,
) : LocalAdbRepository {
    private val appContext = context.applicationContext
    private val keyStore = AdbLocalKeyStore(File(appContext.filesDir, KEY_DIRECTORY))
    private val preferences = LocalAdbPreferences(appContext)
    private val mutex = Mutex()
    private var client: Dadb? = null

    private val _connected = MutableStateFlow(false)
    val connected: StateFlow<Boolean> = _connected.asStateFlow()

    private val _logs = MutableStateFlow<List<String>>(emptyList())

    /** Pasos de la última conexion, para mostrarlos en la pantalla de ADB local. */
    val logs: StateFlow<List<String>> = _logs.asStateFlow()

    fun isConnected(): Boolean = _connected.value

    /** Anade una linea al registro visible y, en debug, a Logcat. */
    fun appendLog(
        message: String,
        error: Throwable? = null,
    ) {
        val stamp = LocalTime.now().format(LOG_TIME_FORMAT)
        _logs.value = (_logs.value + "$stamp  $message").takeLast(MAX_LOG_LINES)
        if (error == null) {
            logger?.debug(TAG, message)
        } else {
            logger?.error(TAG, message, error)
        }
    }

    override fun isAdbEnabled(): Boolean =
        runCatching {
            Settings.Global.getInt(appContext.contentResolver, Settings.Global.ADB_ENABLED, 0) == 1
        }.getOrDefault(false)

    override fun isDeveloperOptionsEnabled(): Boolean =
        runCatching {
            Settings.Global.getInt(appContext.contentResolver, Settings.Global.DEVELOPMENT_SETTINGS_ENABLED, 0) == 1
        }.getOrDefault(false)

    override suspend fun connect() {
        mutex.withLock {
            if (client != null) return
            withContext(io) {
                appendLog(
                    "estado adb_enabled=${isAdbEnabled()} opciones_desarrollador=${isDeveloperOptionsEnabled()} " +
                        "autorizado_antes=${isAuthorized()}",
                )
                probePort()
                val candidate =
                    try {
                        Dadb.create(
                            host = HOST,
                            port = PORT,
                            keyPair = keyStore.read(),
                            connectTimeout = CONNECT_TIMEOUT_MS,
                            socketTimeout = AUTHORIZATION_TIMEOUT_MS,
                        )
                    } catch (timeout: SocketTimeoutException) {
                        appendLog("timeout creando la sesion dadb", timeout)
                        throw LocalAdbException.AuthorizationTimeout(timeout)
                    } catch (error: IOException) {
                        appendLog("fallo creando la sesion dadb: ${error.message}", error)
                        throw mapConnectionError(error)
                    }

                // Dadb abre el socket de forma perezosa: el primer shell es quien
                // dispara la conexión y el diálogo RSA del dispositivo.
                appendLog("enviando 'echo ready' (dialogo RSA)")
                try {
                    candidate.shell("echo ready")
                } catch (timeout: SocketTimeoutException) {
                    appendLog("timeout esperando la autorizacion RSA", timeout)
                    candidate.close()
                    throw LocalAdbException.AuthorizationTimeout(timeout)
                } catch (error: IOException) {
                    appendLog("fallo en 'echo ready': ${error.message}", error)
                    candidate.close()
                    throw mapConnectionError(error)
                }
                client = candidate
                _connected.value = true
                preferences.markAuthorized()
                appendLog("sesion ADB local autorizada")
            }
        }
    }

    override suspend fun runShell(command: String): ShellCommandResult =
        withContext(io) {
            val active = mutex.withLock { client } ?: throw LocalAdbException.NotConnected()
            val started = SystemClock.elapsedRealtime()
            try {
                val response = active.shell(command)
                ShellCommandResult(
                    command = command,
                    stdout = response.output,
                    stderr = response.errorOutput,
                    exitCode = response.exitCode,
                    durationMs = SystemClock.elapsedRealtime() - started,
                )
            } catch (error: IOException) {
                appendLog("shell interrumpido: ${error.message}", error)
                mutex.withLock {
                    runCatching { active.close() }
                    client = null
                    _connected.value = false
                }
                if (error is SocketTimeoutException) {
                    throw LocalAdbException.AuthorizationTimeout(error)
                }
                throw LocalAdbException.Disconnected(error)
            }
        }

    override fun disconnect() {
        val active = client
        client = null
        _connected.value = false
        runCatching { active?.close() }
    }

    fun isAuthorized(): Boolean = preferences.isAuthorized()

    private fun probePort() {
        appendLog("probando $HOST:$PORT (timeout $PORT_PROBE_TIMEOUT_MS ms)")
        try {
            Socket().use { socket ->
                socket.connect(InetSocketAddress(HOST, PORT), PORT_PROBE_TIMEOUT_MS)
                // localPort es el puerto efímero que el sistema asigna a este socket;
                // no debe confundirse con el puerto destino (PORT).
                appendLog("conexion abierta desde puerto local ${socket.localPort} hacia $HOST:${socket.port}")
            }
            appendLog("puerto $PORT acepta conexiones")
        } catch (error: IOException) {
            appendLog("puerto $PORT rechaza la conexion: ${error.message}", error)
            throw LocalAdbException.PortClosed(error)
        }
    }

    private fun mapConnectionError(error: IOException): LocalAdbException {
        val message = error.message.orEmpty()
        return if (message.contains("auth", ignoreCase = true) ||
            message.contains("connection failed", ignoreCase = true)
        ) {
            LocalAdbException.AuthorizationTimeout(error)
        } else if (message.contains("refused", ignoreCase = true)) {
            LocalAdbException.PortClosed(error)
        } else {
            LocalAdbException.Unknown(error)
        }
    }

    companion object {
        const val HOST = "127.0.0.1"
        const val PORT = 5555

        private const val TAG = "LocalAdb"
        private const val MAX_LOG_LINES = 80
        private val LOG_TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm:ss")

        private const val KEY_DIRECTORY = "local-adb"
        private const val PORT_PROBE_TIMEOUT_MS = 1_200
        private const val CONNECT_TIMEOUT_MS = 2_500

        // El diálogo RSA puede tardar en aceptarse; no cerramos la sesión antes.
        private const val AUTHORIZATION_TIMEOUT_MS = 60_000
    }
}
