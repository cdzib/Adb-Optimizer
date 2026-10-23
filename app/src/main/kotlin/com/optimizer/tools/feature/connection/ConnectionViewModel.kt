package com.optimizer.tools.feature.connection

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.optimizer.tools.R
import com.optimizer.tools.core.ui.UiText
import com.optimizer.tools.core.ui.asUiText
import com.optimizer.tools.core.ui.uiText
import com.optimizer.tools.domain.model.KnownDevice
import dev.adboptimizer.core.adb.AdbDiscovery
import dev.adboptimizer.core.adb.DiscoveredService
import dev.adboptimizer.core.model.DeviceInfo
import dev.adboptimizer.core.shell.AdbErrorKind
import dev.adboptimizer.core.shell.AdbException
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/** Estado exclusivo del flujo de conexion. */
data class ConnectionState(
    val connectMode: ConnectMode = ConnectMode.NETWORK,
    val connecting: Boolean = false,
    val connectTimedOut: Boolean = false,
    val connectTarget: String? = null,
    val discoveryRunning: Boolean = false,
    val discovered: List<DiscoveredService> = emptyList(),
    val pairing: Boolean = false,
    val qrText: String? = null,
    val qrStatus: QrStatus? = null,
    val known: List<KnownDevice> = emptyList(),
    val wifiEnabled: Boolean? = null,
    val connected: DeviceInfo? = null,
    val connectedDevices: List<DeviceInfo> = emptyList(),
)

sealed interface ConnectionEvent {
    data class Fail(
        val key: String,
        val detail: UiText?,
    ) : ConnectionEvent

    /** La conexion iniciada por el usuario termino correctamente. */
    data class Connected(
        val deviceName: String,
    ) : ConnectionEvent
}

class ConnectionViewModel(
    private val environment: ConnectionEnvironment,
) : ViewModel() {
    private val _state = MutableStateFlow(ConnectionState())
    val state: StateFlow<ConnectionState> = _state.asStateFlow()

    private val _events = MutableSharedFlow<ConnectionEvent>(extraBufferCapacity = 8)
    val events: SharedFlow<ConnectionEvent> = _events.asSharedFlow()

    private var discoveryJob: Job? = null
    private var qrJob: Job? = null
    private var connectJob: Job? = null
    private var lastConnectAttempt: ConnectAttempt? = null

    init {
        refreshWifiStatus()
        _state.update { it.copy(known = environment.knownDevices()) }
        viewModelScope.launch {
            environment.sessions.collect { sessions ->
                _state.update {
                    it.copy(connected = sessions.activeDevice, connectedDevices = sessions.devices)
                }
            }
        }
        viewModelScope.launch { autoReconnectIfPossible() }
    }

    fun setConnectMode(mode: ConnectMode) {
        _state.update { it.copy(connectMode = mode) }
    }

    fun selectConnectedDevice(deviceId: String) {
        environment.selectDevice(deviceId)
        environment.refreshSessionNotification()
    }

    fun disconnectDevice(deviceId: String) {
        environment.disconnectDevice(deviceId)
        environment.refreshSessionNotification()
    }

    fun disconnectAll() {
        environment.stopSessionService()
        environment.clearActiveSession()
        environment.disconnect()
    }

    private suspend fun autoReconnectIfPossible() {
        val settings = environment.settings.first()
        if (!settings.autoReconnect) return
        if (!environment.wifiEnabled()) return
        val address = environment.lastConnectedAddress() ?: return
        val target = environment.knownDevices().firstOrNull { it.address == address } ?: return

        connectJob?.cancel()
        lastConnectAttempt = ConnectAttempt(target.host, target.port, target.secure)
        connectJob =
            viewModelScope.launch {
                _state.update {
                    it.copy(connecting = true, connectTimedOut = false, connectTarget = "${target.host}:${target.port}")
                }
                val info =
                    runCatching {
                        withTimeoutOrNull(AUTO_RECONNECT_TIMEOUT_MS) {
                            connectWithRecovery(target.host, target.port, target.secure)
                        }
                    }.getOrElse { error ->
                        if (error is kotlinx.coroutines.CancellationException) throw error
                        null
                    }
                if (info == null) {
                    runCatching { environment.disconnectDevice("${target.host}:${target.port}") }
                    _state.update { it.copy(connecting = false, connectTarget = null) }
                    return@launch
                }
                environment.startSessionService(info)
                _state.update { it.copy(connecting = false, connectTarget = null, known = environment.knownDevices()) }
            }
    }

    private fun emitFail(
        key: String,
        detail: UiText?,
    ) {
        _events.tryEmit(ConnectionEvent.Fail(key, detail))
    }

    fun refreshWifiStatus() {
        _state.update { it.copy(wifiEnabled = environment.wifiEnabled()) }
    }

    fun enableWifi(): Boolean {
        val enabled = environment.enableWifi()
        refreshWifiStatus()
        return enabled
    }

    fun startDiscovery() {
        refreshWifiStatus()
        if (_state.value.wifiEnabled == false) {
            emitFail("error.network", uiText(R.string.connection_error_wifi_discovery))
            return
        }
        discoveryJob?.cancel()
        _state.update { it.copy(discoveryRunning = true, discovered = emptyList()) }
        discoveryJob =
            viewModelScope.launch {
                withTimeoutOrNull(DISCOVERY_WINDOW_MS) {
                    environment.observeDiscovery(AdbDiscovery.CONNECT).collect { service ->
                        _state.update { current ->
                            current.copy(
                                discovered =
                                    (current.discovered + service)
                                        .distinctBy { it.address }
                                        .sortedBy { it.address },
                            )
                        }
                    }
                }
                _state.update { it.copy(discoveryRunning = false) }
            }
    }

    fun stopDiscovery() {
        discoveryJob?.cancel()
        discoveryJob = null
        _state.update { it.copy(discoveryRunning = false) }
    }

    fun connectDiscovered(service: DiscoveredService) {
        connectTo(service.host, service.port, secure = true)
    }

    fun pairWithCode(
        host: String,
        pairPort: Int,
        code: String,
        connectPort: Int?,
    ) {
        if (host.isBlank() || pairPort !in 1..65535 || !code.matches(Regex("\\d{6}"))) {
            emitFail("error.pairing", uiText(R.string.connection_error_pairing_input))
            return
        }
        viewModelScope.launch {
            environment.log("Pairing by code started for $host:$pairPort")
            _state.update { it.copy(pairing = true) }
            val result = environment.pair(host, pairPort, code)
            _state.update { it.copy(pairing = false) }

            if (result.isFailure) {
                environment.log("Pairing by code failed for $host:$pairPort", result.exceptionOrNull())
                emitFail("error.pairing", result.exceptionOrNull()?.message?.asUiText())
                return@launch
            }
            environment.log("Pairing by code succeeded for $host:$pairPort")

            // Si el usuario ya indico el puerto de conexion, no hay que esperar mDNS.
            val target =
                connectPort?.takeIf { it in 1..65535 } ?: withTimeoutOrNull(CONNECT_WAIT_MS) {
                    environment
                        .observeDiscovery(AdbDiscovery.CONNECT)
                        .first { it.host == host }
                        .port
                }
            if (target == null) {
                environment.log("Pairing succeeded but no TLS connect service was found for $host")
                emitFail(
                    "error.pairingPort",
                    uiText(R.string.connection_error_pairing_port),
                )
                return@launch
            }
            environment.log("Connecting to TLS service $host:$target")
            connectTo(host, target, secure = true)
        }
    }

    fun startQrPairing() {
        refreshWifiStatus()
        if (_state.value.wifiEnabled == false) {
            emitFail("error.network", uiText(R.string.connection_error_wifi_pairing))
            return
        }
        qrJob?.cancel()
        val name = "ADB_WIFI_${randomToken(6)}"
        val password = randomToken(8)
        _state.update {
            it.copy(
                qrText = "WIFI:T:ADB;S:$name;P:$password;;",
                qrStatus = QrStatus.WAITING,
            )
        }

        qrJob =
            viewModelScope.launch {
                // Android publica el campo S del QR como nombre del servicio de pairing.
                // Otros anuncios pueden corresponder a códigos o QR de otro controlador.
                var pairedService: DiscoveredService? = null
                val retryTracker = QrPairingRetryTracker(QR_PAIRING_RETRY_COOLDOWN_MS)
                var sawPairingService = false
                var lastPairingError: String? = null
                var discoveryError: Throwable? = null
                try {
                    environment.log("QR pairing started; waiting for ${AdbDiscovery.PAIRING}")
                    val discoveredServices = linkedMapOf<String, DiscoveredService>()
                    val serviceUpdates = Channel<Unit>(Channel.CONFLATED)
                    val discoveryJob =
                        launch {
                            try {
                                environment.observeDiscovery(AdbDiscovery.PAIRING).collect { service ->
                                    if (service.serviceName != name || service.host.isBlank() || service.port !in 1..65535) {
                                        environment.log("Ignoring pairing announcement that does not match the active QR")
                                        return@collect
                                    }
                                    sawPairingService = true
                                    discoveredServices[service.serviceName] = service
                                    environment.log("QR pairing service available: ${service.address}")
                                    serviceUpdates.trySend(Unit)
                                }
                            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                                throw cancelled
                            } catch (error: Exception) {
                                discoveryError = error
                                environment.log("QR mDNS discovery failed", error)
                                serviceUpdates.trySend(Unit)
                            }
                        }
                    try {
                        withTimeoutOrNull(QR_WAIT_MS) {
                            while (pairedService == null) {
                                val now = environment.elapsedRealtime()
                                val service =
                                    discoveredServices.values.firstOrNull {
                                        retryTracker.canAttempt(it.address, now)
                                    }
                                if (service == null) {
                                    withTimeoutOrNull(QR_RETRY_MS) { serviceUpdates.receive() }
                                } else {
                                    environment.log(
                                        "Trying QR pairing endpoint: service=${service.serviceName} " +
                                            "host=${service.host} port=${service.port}",
                                    )
                                    _state.update {
                                        it.copy(qrStatus = QrStatus.PAIRING)
                                    }
                                    val result = environment.pair(service.host, service.port, password)
                                    if (result.isSuccess) {
                                        pairedService = service
                                        environment.log(
                                            "QR pairing succeeded: service=${service.serviceName} " +
                                                "host=${service.host} port=${service.port}",
                                        )
                                    } else {
                                        val detail = result.exceptionOrNull()?.message ?: "Error desconocido de pairing"
                                        lastPairingError = "${service.address}: $detail"
                                        retryTracker.markFailed(service.address, environment.elapsedRealtime())
                                        environment.log(
                                            "QR pairing attempt failed at ${service.address}: $detail",
                                            result.exceptionOrNull(),
                                        )
                                    }
                                }
                            }
                        }
                    } finally {
                        discoveryJob.cancel()
                        serviceUpdates.close()
                    }
                    val pairingService = pairedService
                    if (pairingService == null) {
                        environment.log(
                            "QR pairing timed out: matchingServiceFound=$sawPairingService; lastPairingError=$lastPairingError",
                            discoveryError,
                        )
                        _state.update {
                            it.copy(qrStatus = QrStatus.TIMEOUT)
                        }
                        return@launch
                    }

                    // Termux deja que adbd termine de publicar el transporte tras el pairing
                    // antes de iniciar el segundo discovery.
                    environment.log("Pairing complete; waiting ${QR_ADBD_START_DELAY_MS}ms for adbd to publish TLS transport")
                    delay(QR_ADBD_START_DELAY_MS)
                    environment.log(
                        "Pairing endpoint selected: service=${pairingService.serviceName} " +
                            "host=${pairingService.host} port=${pairingService.port}; " +
                            "starting ${AdbDiscovery.CONNECT} discovery",
                    )
                    _state.update {
                        it.copy(qrStatus = QrStatus.CONNECTING)
                    }
                    val connect =
                        withTimeoutOrNull(CONNECT_WAIT_MS) {
                            environment
                                .observeDiscovery(AdbDiscovery.CONNECT)
                                .first { service ->
                                    val matchesPairingHost = service.host == pairingService.host
                                    environment.log(
                                        "QR TLS candidate: service=${service.serviceName} host=${service.host} " +
                                            "port=${service.port}; pairingHost=${pairingService.host}; " +
                                            "hostMatch=$matchesPairingHost",
                                    )
                                    matchesPairingHost
                                }
                        }
                    if (connect == null) {
                        environment.log("Pairing succeeded but TLS connect service was not found on ${pairingService.host}")
                        _state.update {
                            it.copy(qrStatus = QrStatus.ERROR)
                        }
                        return@launch
                    }
                    // NSD puede anunciar el puerto antes de que el socket TLS acepte conexiones.
                    // El cliente Termux también espera 2 s entre resolverlo y ejecutar adb connect.
                    environment.log(
                        "TLS endpoint selected: service=${connect.serviceName} host=${connect.host} " +
                            "port=${connect.port}; waiting ${QR_CONNECT_SETTLE_DELAY_MS}ms before connect",
                    )
                    delay(QR_CONNECT_SETTLE_DELAY_MS)
                    environment.log("Connecting to TLS service ${connect.address}")
                    connectTo(connect.host, connect.port, secure = true)
                } catch (cancelled: kotlinx.coroutines.CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    environment.log("QR pairing flow crashed", error)
                    _state.update {
                        it.copy(qrStatus = QrStatus.ERROR)
                    }
                }
            }
    }

    fun cancelQrPairing() {
        qrJob?.cancel()
        qrJob = null
        _state.update { it.copy(qrText = null, qrStatus = null) }
    }

    fun connectManual(address: String) {
        val parsed =
            parseAddress(address) ?: run {
                emitFail("error.network", uiText(R.string.connection_error_manual_ip))
                return
            }
        connectTo(parsed.first, parsed.second, secure = false)
    }

    fun connectSecure(address: String) {
        val parsed =
            parseAddress(address) ?: run {
                emitFail("error.network", uiText(R.string.connection_error_manual_tls))
                return
            }
        connectTo(parsed.first, parsed.second, secure = true)
    }

    fun connectKnown(device: KnownDevice) {
        connectTo(device.host, device.port, device.secure)
    }

    fun forgetKnown(device: KnownDevice) {
        environment.forgetDevice(device.address)
        _state.update { it.copy(known = environment.knownDevices()) }
    }

    fun cancelConnect() {
        connectJob?.cancel()
        connectJob = null
        lastConnectAttempt?.let { environment.disconnectDevice("${it.host}:${it.port}") }
        _state.update {
            it.copy(
                connecting = false,
                connectTimedOut = false,
                connectTarget = null,
            )
        }
    }

    fun retryConnect() {
        val attempt = lastConnectAttempt ?: return
        connectTo(attempt.host, attempt.port, attempt.secure)
    }

    private suspend fun connectWithRecovery(
        host: String,
        port: Int,
        secure: Boolean,
    ): DeviceInfo {
        try {
            return environment.connect(host, port, secure)
        } catch (error: AdbException) {
            if (!secure || error.kind != AdbErrorKind.OFFLINE) throw error
            environment.log("TLS endpoint $host:$port is offline; discovering a replacement on the same host", error)
        }

        val replacement =
            try {
                withTimeoutOrNull(TLS_REDISCOVERY_TIMEOUT_MS) {
                    environment.observeDiscovery(AdbDiscovery.CONNECT).first {
                        it.host == host && it.port in 1..65535 && it.port != port
                    }
                }
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                environment.log("TLS rediscovery failed; retrying the selected endpoint once", error)
                null
            }
        val retryPort = replacement?.port ?: port
        // A newly advertised adbd socket may not yet accept connections.
        delay(TLS_RETRY_SETTLE_MS)
        lastConnectAttempt = ConnectAttempt(host, retryPort, true)
        _state.update { it.copy(connectTarget = "$host:$retryPort (TLS)") }
        environment.log("Retrying TLS endpoint $host:$retryPort once")
        return environment.connect(host, retryPort, true)
    }

    private fun connectTo(
        host: String,
        port: Int,
        secure: Boolean,
    ) {
        connectJob?.cancel()
        lastConnectAttempt = ConnectAttempt(host, port, secure)
        connectJob =
            viewModelScope.launch {
                val startedAt = environment.elapsedRealtime()
                val target = "$host:$port${if (secure) " (TLS)" else ""}"
                environment.log("Connect flow started: $host:$port secure=$secure")
                _state.update {
                    it.copy(
                        connecting = true,
                        connectTimedOut = false,
                        connectTarget = target,
                    )
                }
                try {
                    val info =
                        withTimeoutOrNull(CONNECT_TIMEOUT_MS) {
                            connectWithRecovery(host, port, secure)
                        }
                    if (info == null) {
                        environment.log("Connect flow timed out after ${environment.elapsedRealtime() - startedAt}ms")
                        runCatching { environment.disconnectDevice("$host:$port") }
                        _state.update { it.copy(connecting = false, connectTimedOut = true) }
                        emitFail(
                            "error.timeout",
                            uiText(R.string.connection_error_timeout, target, CONNECT_TIMEOUT_MS / 1000),
                        )
                        return@launch
                    }
                    environment.log("Connect flow succeeded: ${info.displayName} in ${environment.elapsedRealtime() - startedAt}ms")
                    environment.startSessionService(info)
                    _state.update {
                        it.copy(
                            connecting = false,
                            connectTimedOut = false,
                            connectTarget = null,
                            discovered = emptyList(),
                            known = environment.knownDevices(),
                            qrText = null,
                            qrStatus = null,
                        )
                    }
                    _events.tryEmit(ConnectionEvent.Connected(info.displayName))
                } catch (cancelled: kotlinx.coroutines.CancellationException) {
                    _state.update { it.copy(connecting = false) }
                    throw cancelled
                } catch (error: AdbException) {
                    environment.log(
                        "Connect flow failed (${error.kind}) after ${environment.elapsedRealtime() - startedAt}ms: ${error.message}",
                        error,
                    )
                    _state.update { it.copy(connecting = false) }
                    if (_state.value.qrText != null) {
                        _state.update { it.copy(qrStatus = QrStatus.ERROR) }
                    }
                    emitFail(keyFor(error.kind), error.message?.asUiText())
                } catch (error: Exception) {
                    environment.log("Connect flow failed after ${environment.elapsedRealtime() - startedAt}ms: ${error.message}", error)
                    _state.update { it.copy(connecting = false) }
                    if (_state.value.qrText != null) {
                        _state.update { it.copy(qrStatus = QrStatus.ERROR) }
                    }
                    emitFail("error.network", error.message?.asUiText())
                }
            }
    }

    private fun randomToken(length: Int): String {
        val alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        return (1..length).map { alphabet.random() }.joinToString("")
    }

    companion object {
        private const val DISCOVERY_WINDOW_MS = 45_000L
        private const val CONNECT_WAIT_MS = 20_000L
        private const val CONNECT_TIMEOUT_MS = 25_000L
        private const val AUTO_RECONNECT_TIMEOUT_MS = 15_000L
        private const val TLS_REDISCOVERY_TIMEOUT_MS = 5_000L
        private const val TLS_RETRY_SETTLE_MS = 750L
        private const val QR_WAIT_MS = 120_000L
        private const val QR_RETRY_MS = 1_200L
        private const val QR_PAIRING_RETRY_COOLDOWN_MS = 8_000L
        private const val QR_ADBD_START_DELAY_MS = 2_000L
        private const val QR_CONNECT_SETTLE_DELAY_MS = 2_000L

        fun factory(environment: ConnectionEnvironment): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T = ConnectionViewModel(environment) as T
            }

        fun parseAddress(raw: String): Pair<String, Int>? {
            val value = raw.trim().removePrefix("adb://")
            if (value.isEmpty()) return null
            val parts = value.split(":")
            val host = parts.firstOrNull()?.trim().orEmpty()
            if (host.isEmpty() || host.count { it == '.' } != 3) return null
            val port = parts.getOrNull(1)?.toIntOrNull() ?: 5555
            if (port !in 1..65535) return null
            return host to port
        }

        private fun keyFor(kind: AdbErrorKind): String =
            when (kind) {
                AdbErrorKind.UNAUTHORIZED -> "error.unauthorized"
                AdbErrorKind.TIMEOUT -> "error.timeout"
                AdbErrorKind.SHELL -> "error.shellFailed"
                AdbErrorKind.PAIRING -> "error.pairing"
                AdbErrorKind.NOT_FOUND, AdbErrorKind.OFFLINE -> "error.network"
            }
    }
}
