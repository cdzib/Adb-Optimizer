package com.optimizer.tools.feature.connection

import com.optimizer.tools.R
import com.optimizer.tools.core.ui.uiText
import com.optimizer.tools.domain.model.AppSettings
import com.optimizer.tools.domain.model.KnownDevice
import com.optimizer.tools.testutil.runViewModelTest
import dev.adboptimizer.core.adb.AdbDiscovery
import dev.adboptimizer.core.adb.DiscoveredService
import dev.adboptimizer.core.model.DeviceInfo
import dev.adboptimizer.core.shell.AdbErrorKind
import dev.adboptimizer.core.shell.AdbException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ConnectionViewModelTest {
    @Test
    fun `regenerated QR rejects old announcement and cancellation stops the active flow`() =
        runViewModelTest {
            val environment = FakeConnectionEnvironment()
            val viewModel = ConnectionViewModel(environment)
            viewModel.startQrPairing()
            val oldName = requireNotNull(viewModel.state.value.qrText).substringAfter(";S:").substringBefore(';')
            viewModel.startQrPairing()
            val newName = requireNotNull(viewModel.state.value.qrText).substringAfter(";S:").substringBefore(';')
            runCurrent()
            environment.discoveryPairing.emit(service.copy(port = 37000, serviceName = oldName))
            runCurrent()
            assertTrue(environment.pairCalls.isEmpty())
            environment.discoveryPairing.emit(service.copy(port = 38000, serviceName = newName))
            runCurrent()
            assertEquals(1, environment.pairCalls.size)
            viewModel.cancelQrPairing()
            advanceTimeBy(25_000L)
            runCurrent()
            environment.discoveryConnect.emit(service.copy(port = 47000))
            runCurrent()
            assertTrue(environment.connectCalls.isEmpty())
            assertNull(viewModel.state.value.qrText)
            assertNull(viewModel.state.value.qrStatus)
        }

    @Test
    fun `failed QR pairing waits and follows replacement pairing port`() =
        runViewModelTest {
            val environment = FakeConnectionEnvironment()
            environment.pairFailures += AdbException("not ready", AdbErrorKind.PAIRING)
            val viewModel = ConnectionViewModel(environment)
            viewModel.startQrPairing()
            runCurrent()
            val name = requireNotNull(viewModel.state.value.qrText).substringAfter(";S:").substringBefore(';')
            environment.discoveryPairing.emit(service.copy(port = 37000, serviceName = name))
            runCurrent()
            assertEquals(1, environment.pairCalls.size)
            assertTrue(environment.connectCalls.isEmpty())
            environment.discoveryPairing.emit(service.copy(port = 38000, serviceName = name))
            runCurrent()
            assertEquals(listOf(37000, 38000), environment.pairCalls.map { it.port })
            assertTrue(environment.connectCalls.isEmpty())
            viewModel.cancelQrPairing()
        }

    @Test
    fun `QR ignores unrelated pairing services and connects only after matching pairing succeeds`() =
        runViewModelTest {
            val environment = FakeConnectionEnvironment()
            val viewModel = ConnectionViewModel(environment)
            viewModel.startQrPairing()
            runCurrent()
            val qr = requireNotNull(viewModel.state.value.qrText)
            val name = qr.substringAfter(";S:").substringBefore(';')
            val password = qr.substringAfter(";P:").substringBefore(';')
            environment.discoveryPairing.emit(service.copy(port = 37000, serviceName = "another-phone"))
            runCurrent()
            assertTrue(environment.pairCalls.isEmpty())
            assertEquals(QrStatus.WAITING, viewModel.state.value.qrStatus)
            environment.discoveryPairing.emit(service.copy(port = 38000, serviceName = name))
            runCurrent()
            assertEquals(listOf(PairCall(service.host, 38000, password)), environment.pairCalls)
            assertTrue(environment.connectCalls.isEmpty())
            advanceTimeBy(2_000L)
            runCurrent()
            environment.discoveryConnect.emit(service.copy(host = "192.168.1.41", port = 46000))
            environment.discoveryConnect.emit(service.copy(port = 47000))
            advanceTimeBy(2_000L)
            runCurrent()
            assertEquals(listOf(ConnectCall(service.host, 47000, true)), environment.connectCalls)
            assertEquals(listOf(phone), environment.startedSessions)
            assertNull(viewModel.state.value.qrText)
            assertEquals(QrStatus.CONNECTED, viewModel.state.value.qrStatus)
        }

    @Test
    fun `offline TLS retries original port only once when discovery has no replacement`() =
        runViewModelTest {
            val environment = FakeConnectionEnvironment()
            repeat(2) { environment.connectFailures += AdbException("refused", AdbErrorKind.OFFLINE) }
            val viewModel = ConnectionViewModel(environment)
            val events = mutableListOf<ConnectionEvent>()
            val collector = launch { viewModel.events.collect { events += it } }
            runCurrent()
            viewModel.connectSecure("192.168.1.40:44559")
            advanceTimeBy(5_751L)
            runCurrent()
            assertEquals(List(2) { ConnectCall("192.168.1.40", 44559, true) }, environment.connectCalls)
            assertTrue(environment.startedSessions.isEmpty())
            assertEquals(1, events.size)
            assertFalse(viewModel.state.value.connecting)
            advanceTimeBy(30_000L)
            runCurrent()
            assertEquals(2, environment.connectCalls.size)
            collector.cancel()
        }

    @Test
    fun `classic offline and TLS authorization errors do not retry`() =
        runViewModelTest {
            listOf(
                false to AdbErrorKind.OFFLINE,
                true to AdbErrorKind.UNAUTHORIZED,
                true to AdbErrorKind.PAIRING,
            ).forEach { (secure, kind) ->
                val environment = FakeConnectionEnvironment()
                environment.connectFailures += AdbException("failure", kind)
                val viewModel = ConnectionViewModel(environment)
                if (secure) {
                    viewModel.connectSecure("192.168.1.40:44559")
                } else {
                    viewModel.connectManual("192.168.1.40:5555")
                }
                advanceTimeBy(6_000L)
                runCurrent()
                assertEquals(1, environment.connectCalls.size)
                assertTrue(environment.startedSessions.isEmpty())
                assertFalse(viewModel.state.value.connecting)
            }
        }

    @Test
    fun `cancel during rediscovery prevents retry and session creation`() =
        runViewModelTest {
            val environment = FakeConnectionEnvironment()
            environment.connectFailures += AdbException("refused", AdbErrorKind.OFFLINE)
            val viewModel = ConnectionViewModel(environment)
            viewModel.connectSecure("192.168.1.40:44559")
            runCurrent()
            viewModel.cancelConnect()
            environment.discoveryConnect.emit(service.copy(port = 46000))
            advanceTimeBy(6_000L)
            runCurrent()
            assertEquals(1, environment.connectCalls.size)
            assertTrue(environment.startedSessions.isEmpty())
            assertFalse(viewModel.state.value.connecting)
            assertNull(viewModel.state.value.connectTarget)
        }

    @Test
    fun `auto reconnect recovers stale saved TLS port`() =
        runViewModelTest {
            val saved = known.copy(port = 44559, secure = true)
            val environment =
                FakeConnectionEnvironment(
                    known = listOf(saved),
                    settings = AppSettings(autoReconnect = true),
                    lastAddress = saved.address,
                )
            environment.connectFailures += AdbException("refused", AdbErrorKind.OFFLINE)
            val viewModel = ConnectionViewModel(environment)
            runCurrent()
            environment.discoveryConnect.emit(service.copy(port = 46000))
            advanceTimeBy(750L)
            runCurrent()
            assertEquals(listOf(44559, 46000), environment.connectCalls.map { it.port })
            assertEquals(listOf(phone), environment.startedSessions)
            assertFalse(viewModel.state.value.connecting)
        }

    @Test
    fun `offline TLS connection discovers new port on same host`() =
        runViewModelTest {
            val environment = FakeConnectionEnvironment()
            environment.connectFailures += AdbException("refused", AdbErrorKind.OFFLINE)
            val viewModel = ConnectionViewModel(environment)
            runCurrent()
            viewModel.connectSecure("192.168.1.40:44559")
            runCurrent()
            environment.discoveryConnect.emit(service.copy(host = "192.168.1.41", port = 46000))
            environment.discoveryConnect.emit(service.copy(port = 44559))
            environment.discoveryConnect.emit(service.copy(port = 0))
            environment.discoveryConnect.emit(service.copy(port = 46000))
            advanceTimeBy(750L)
            runCurrent()
            assertEquals(
                listOf(ConnectCall("192.168.1.40", 44559, true), ConnectCall("192.168.1.40", 46000, true)),
                environment.connectCalls,
            )
            assertEquals(listOf(phone), environment.startedSessions)
            assertFalse(viewModel.state.value.connecting)
            viewModel.retryConnect()
            runCurrent()
            assertEquals(46000, environment.connectCalls.last().port)
        }

    @Test
    fun `initial state loads known devices wifi and sessions`() =
        runViewModelTest {
            val environment =
                FakeConnectionEnvironment(
                    sessionsState = ConnectionSessionState(listOf(phone), phone),
                    known = listOf(known),
                    wifi = true,
                )
            val viewModel = ConnectionViewModel(environment)
            runCurrent()

            assertEquals(listOf(known), viewModel.state.value.known)
            assertEquals(true, viewModel.state.value.wifiEnabled)
            assertEquals(phone, viewModel.state.value.connected)
            assertEquals(listOf(phone), viewModel.state.value.connectedDevices)
        }

    @Test
    fun `manual connect starts the session service`() =
        runViewModelTest {
            val environment = FakeConnectionEnvironment(connectResult = phone)
            val viewModel = ConnectionViewModel(environment)
            runCurrent()

            viewModel.connectManual("192.168.1.40:5555")
            runCurrent()

            assertEquals(listOf(ConnectCall("192.168.1.40", 5555, false)), environment.connectCalls)
            assertEquals(listOf(phone), environment.startedSessions)
            assertEquals(false, viewModel.state.value.connecting)
            assertNull(viewModel.state.value.connectTarget)
        }

    @Test
    fun `invalid manual address emits a network failure`() =
        runViewModelTest {
            val environment = FakeConnectionEnvironment()
            val viewModel = ConnectionViewModel(environment)
            val events = mutableListOf<ConnectionEvent>()
            val collector = launch { viewModel.events.collect { events += it } }
            runCurrent()

            viewModel.connectManual("sin-ip")
            runCurrent()

            assertEquals(
                listOf(ConnectionEvent.Fail("error.network", uiText(R.string.connection_error_manual_ip))),
                events,
            )
            assertEquals(0, environment.connectCalls.size)
            collector.cancel()
        }

    @Test
    fun `discovery requires wifi`() =
        runViewModelTest {
            val environment = FakeConnectionEnvironment(wifi = false)
            val viewModel = ConnectionViewModel(environment)
            val events = mutableListOf<ConnectionEvent>()
            val collector = launch { viewModel.events.collect { events += it } }
            runCurrent()

            viewModel.startDiscovery()
            runCurrent()

            assertEquals(
                listOf(ConnectionEvent.Fail("error.network", uiText(R.string.connection_error_wifi_discovery))),
                events,
            )
            assertFalse(viewModel.state.value.discoveryRunning)
            collector.cancel()
        }

    @Test
    fun `discovery collects services and stops after the window`() =
        runViewModelTest {
            val environment = FakeConnectionEnvironment(wifi = true)
            val viewModel = ConnectionViewModel(environment)
            runCurrent()

            viewModel.startDiscovery()
            runCurrent()
            environment.discoveryConnect.emit(service)
            runCurrent()
            assertEquals(listOf(service), viewModel.state.value.discovered)

            advanceTimeBy(45_001L)
            runCurrent()

            assertFalse(viewModel.state.value.discoveryRunning)
        }

    @Test
    fun `pair with code connects to the explicit port`() =
        runViewModelTest {
            val environment = FakeConnectionEnvironment(connectResult = phone)
            val viewModel = ConnectionViewModel(environment)
            runCurrent()

            viewModel.pairWithCode(
                host = "192.168.1.40",
                pairPort = 37000,
                code = "123456",
                connectPort = 5555,
            )
            runCurrent()

            assertEquals(listOf(PairCall("192.168.1.40", 37000, "123456")), environment.pairCalls)
            assertEquals(listOf(ConnectCall("192.168.1.40", 5555, true)), environment.connectCalls)
            assertEquals(listOf(phone), environment.startedSessions)
            assertFalse(viewModel.state.value.pairing)
        }

    @Test
    fun `forget known updates the stored list`() =
        runViewModelTest {
            val environment = FakeConnectionEnvironment(known = listOf(known))
            val viewModel = ConnectionViewModel(environment)
            runCurrent()

            viewModel.forgetKnown(known)

            assertEquals(emptyList<KnownDevice>(), viewModel.state.value.known)
            assertEquals(listOf(known.address), environment.forgottenAddresses)
        }

    @Test
    fun `disconnect all stops the service and clears the session`() =
        runViewModelTest {
            val environment = FakeConnectionEnvironment()
            val viewModel = ConnectionViewModel(environment)

            viewModel.disconnectAll()

            assertTrue(environment.stopSessionServiceCalled)
            assertTrue(environment.clearActiveSessionCalled)
            assertTrue(environment.disconnectCalled)
        }
}

private class FakeConnectionEnvironment(
    sessionsState: ConnectionSessionState = ConnectionSessionState(),
    settings: AppSettings = AppSettings(autoReconnect = false),
    var known: List<KnownDevice> = emptyList(),
    var wifi: Boolean = true,
    private val connectResult: DeviceInfo = phone,
    private val pairResult: Result<Unit> = Result.success(Unit),
    private val lastAddress: String? = null,
) : ConnectionEnvironment {
    val sessionsFlow = MutableStateFlow(sessionsState)
    val settingsFlow = MutableStateFlow(settings)
    val discoveryConnect = MutableSharedFlow<DiscoveredService>(extraBufferCapacity = 8)
    val discoveryPairing = MutableSharedFlow<DiscoveredService>(extraBufferCapacity = 8)
    val connectCalls = mutableListOf<ConnectCall>()
    val connectFailures = ArrayDeque<AdbException>()
    val pairCalls = mutableListOf<PairCall>()
    val pairFailures = ArrayDeque<AdbException>()
    val startedSessions = mutableListOf<DeviceInfo>()
    val forgottenAddresses = mutableListOf<String>()
    var stopSessionServiceCalled = false
    var clearActiveSessionCalled = false
    var disconnectCalled = false
    private var clock = 0L

    override val sessions = sessionsFlow
    override val settings = settingsFlow

    override fun knownDevices(): List<KnownDevice> = known

    override fun wifiEnabled(): Boolean = wifi

    override fun enableWifi(): Boolean {
        wifi = true
        return true
    }

    override fun lastConnectedAddress(): String? = lastAddress

    override fun elapsedRealtime(): Long {
        clock += 1_000L
        return clock
    }

    override fun log(
        message: String,
        error: Throwable?,
    ) = Unit

    override fun observeDiscovery(serviceType: String): Flow<DiscoveredService> =
        if (serviceType == AdbDiscovery.PAIRING) discoveryPairing else discoveryConnect

    override suspend fun connect(
        host: String,
        port: Int,
        secure: Boolean,
    ): DeviceInfo {
        connectCalls += ConnectCall(host, port, secure)
        if (connectFailures.isNotEmpty()) throw connectFailures.removeFirst()
        return connectResult
    }

    override suspend fun pair(
        host: String,
        port: Int,
        code: String,
    ): Result<Unit> {
        pairCalls += PairCall(host, port, code)
        if (pairFailures.isNotEmpty()) return Result.failure(pairFailures.removeFirst())
        return pairResult
    }

    override fun disconnectDevice(deviceId: String) = Unit

    override fun selectDevice(deviceId: String) = Unit

    override fun refreshSessionNotification() = Unit

    override fun stopSessionService() {
        stopSessionServiceCalled = true
    }

    override fun clearActiveSession() {
        clearActiveSessionCalled = true
    }

    override fun disconnect() {
        disconnectCalled = true
    }

    override fun startSessionService(device: DeviceInfo) {
        startedSessions += device
    }

    override fun forgetDevice(address: String) {
        forgottenAddresses += address
        known = known.filterNot { it.address == address }
    }
}

private data class ConnectCall(
    val host: String,
    val port: Int,
    val secure: Boolean,
)

private data class PairCall(
    val host: String,
    val port: Int,
    val code: String,
)

private val phone =
    DeviceInfo(
        id = "192.168.1.40:5555",
        model = "Pixel",
        manufacturer = "Google",
        sdk = 35,
        androidVersion = "15",
        isTv = false,
        hasRoot = false,
        abi = "arm64-v8a",
    )

private val known =
    KnownDevice(
        host = "192.168.1.40",
        port = 5555,
        secure = false,
        model = "Pixel",
    )

private val service =
    DiscoveredService(
        host = "192.168.1.40",
        port = 5555,
        serviceName = "adb-test",
    )
