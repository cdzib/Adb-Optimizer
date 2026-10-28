package com.optimizer.tools.feature.localadb.data

import android.content.Context
import android.os.SystemClock
import android.provider.Settings
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

/**
 * Sesión ADB contra el propio dispositivo en 127.0.0.1:5555.
 * Todo el I/O se ejecuta en [io]; la UI nunca bloquea el hilo principal.
 */
class DadbLocalAdbRepository(
    context: Context,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) : LocalAdbRepository {
    private val appContext = context.applicationContext
    private val keyStore = AdbLocalKeyStore(File(appContext.filesDir, KEY_DIRECTORY))
    private val preferences = LocalAdbPreferences(appContext)
    private val mutex = Mutex()
    private var client: Dadb? = null

    private val _connected = MutableStateFlow(false)
    val connected: StateFlow<Boolean> = _connected.asStateFlow()

    fun isConnected(): Boolean = _connected.value

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
                        throw LocalAdbException.AuthorizationTimeout(timeout)
                    } catch (error: IOException) {
                        throw mapConnectionError(error)
                    }

                // Dadb abre el socket de forma perezosa: el primer shell es quien
                // dispara la conexión y el diálogo RSA del dispositivo.
                try {
                    candidate.shell("echo ready")
                } catch (timeout: SocketTimeoutException) {
                    candidate.close()
                    throw LocalAdbException.AuthorizationTimeout(timeout)
                } catch (error: IOException) {
                    candidate.close()
                    throw mapConnectionError(error)
                }
                client = candidate
                _connected.value = true
                preferences.markAuthorized()
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
        try {
            Socket().use { socket ->
                socket.connect(InetSocketAddress(HOST, PORT), PORT_PROBE_TIMEOUT_MS)
            }
        } catch (error: IOException) {
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

        private const val KEY_DIRECTORY = "local-adb"
        private const val PORT_PROBE_TIMEOUT_MS = 1_200
        private const val CONNECT_TIMEOUT_MS = 2_500

        // El diálogo RSA puede tardar en aceptarse; no cerramos la sesión antes.
        private const val AUTHORIZATION_TIMEOUT_MS = 60_000
    }
}
