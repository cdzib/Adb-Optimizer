package com.optimizer.tools.feature.dashboard

import com.optimizer.tools.R
import com.optimizer.tools.core.ui.uiText
import com.optimizer.tools.domain.model.AppSettings
import com.optimizer.tools.domain.model.DevicePowerAction
import com.optimizer.tools.testutil.runViewModelTest
import dev.adboptimizer.core.model.AppTarget
import dev.adboptimizer.core.model.DeviceInfo
import dev.adboptimizer.core.model.DeviceMetrics
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runCurrent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DashboardViewModelTest {
    @Test
    fun `switching to remote clears local hardware and battery diagnostics`() =
        runViewModelTest {
            val local =
                phone.copy(
                    hardwareInfo =
                        dev.adboptimizer.core.model
                            .DeviceHardwareInfo(socModel = "Local SoC"),
                )
            val environment =
                FakeDashboardEnvironment(
                    sessionsState = DashboardSessionState(devices = listOf(remote), activeDevice = remote),
                    localDeviceInfo = local,
                    localMetricsValue =
                        metrics.copy(
                            batteryDiagnostics =
                                dev.adboptimizer.core.model
                                    .BatteryDiagnostics(voltageVolts = 4.1f),
                        ),
                    remoteMetricsValue = metrics,
                )
            val viewModel = DashboardViewModel(environment)
            runCurrent()
            viewModel.setTarget(AppTarget.LOCAL)
            runCurrent()
            assertEquals(
                "Local SoC",
                viewModel.state.value.device
                    ?.hardwareInfo
                    ?.socModel,
            )
            assertEquals(
                4.1f,
                viewModel.state.value.metrics
                    ?.batteryDiagnostics
                    ?.voltageVolts,
            )
            viewModel.setTarget(AppTarget.REMOTE)
            assertNull(
                viewModel.state.value.metrics
                    ?.batteryDiagnostics,
            )
            runCurrent()
            assertEquals(
                remote.id,
                viewModel.state.value.device
                    ?.id,
            )
            assertNull(
                viewModel.state.value.device
                    ?.hardwareInfo,
            )
            assertNull(
                viewModel.state.value.metrics
                    ?.batteryDiagnostics,
            )
        }

    @Test
    fun `local target loads device and metrics`() =
        runViewModelTest {
            val environment =
                FakeDashboardEnvironment(
                    sessionsState = DashboardSessionState(devices = listOf(phone)),
                    localDeviceInfo = phone,
                    localMetricsValue = metrics,
                )
            val viewModel = DashboardViewModel(environment)
            runCurrent()

            viewModel.setTarget(AppTarget.LOCAL)
            runCurrent()

            assertEquals(AppTarget.LOCAL, viewModel.state.value.target)
            assertEquals(phone, viewModel.state.value.device)
            assertEquals(metrics, viewModel.state.value.metrics)
            assertFalse(viewModel.state.value.loading)
            assertNull(viewModel.state.value.error)
        }

    @Test
    fun `remote target without session reports an error and keeps remote target`() =
        runViewModelTest {
            val environment = FakeDashboardEnvironment()
            val viewModel = DashboardViewModel(environment)
            runCurrent()

            viewModel.setTarget(AppTarget.REMOTE)
            runCurrent()

            assertEquals(AppTarget.REMOTE, viewModel.state.value.target)
            assertNull(viewModel.state.value.device)
            assertNull(viewModel.state.value.metrics)
            assertEquals(
                uiText(R.string.dashboard_error_connect),
                viewModel.state.value.error,
            )
        }

    @Test
    fun `remote target uses active device and remote metrics`() =
        runViewModelTest {
            val environment =
                FakeDashboardEnvironment(
                    sessionsState =
                        DashboardSessionState(
                            devices = listOf(remote),
                            activeDevice = remote,
                        ),
                    remoteMetricsValue = metrics.copy(cpuBusyPercent = 42f),
                )
            val viewModel = DashboardViewModel(environment)
            runCurrent()

            viewModel.setTarget(AppTarget.REMOTE)
            runCurrent()

            assertEquals(AppTarget.REMOTE, viewModel.state.value.target)
            assertEquals(remote, viewModel.state.value.device)
            assertEquals(
                42f,
                viewModel.state.value.metrics
                    ?.cpuBusyPercent,
            )
        }

    @Test
    fun `auto refresh ticks request metrics after the configured interval`() =
        runViewModelTest {
            val environment =
                FakeDashboardEnvironment(
                    localDeviceInfo = phone,
                    localMetricsValue = metrics,
                )
            val viewModel = DashboardViewModel(environment)
            runCurrent()
            viewModel.setTarget(AppTarget.LOCAL)
            environment.settingsFlow.value =
                AppSettings(
                    autoRefreshMetrics = true,
                    metricsRefreshSeconds = 10,
                )
            runCurrent()
            val initialCalls = environment.localMetricsCalls

            repeat(9) {
                environment.ticksFlow.emit(Unit)
                runCurrent()
            }
            assertEquals(initialCalls, environment.localMetricsCalls)

            environment.ticksFlow.emit(Unit)
            runCurrent()
            assertEquals(initialCalls + 1, environment.localMetricsCalls)
        }

    @Test
    fun `power action asks for the selected device and runs only on confirmation`() =
        runViewModelTest {
            val environment =
                FakeDashboardEnvironment(
                    sessionsState = DashboardSessionState(devices = listOf(remote), activeDevice = remote),
                )
            val viewModel = DashboardViewModel(environment)
            runCurrent()
            viewModel.setTarget(AppTarget.REMOTE)
            runCurrent()

            viewModel.requestPowerAction(DevicePowerAction.REBOOT)
            val request = requireNotNull(viewModel.state.value.pendingPowerAction)
            assertEquals(remote.id, request.deviceId)
            assertEquals(AppTarget.REMOTE, request.target)
            assertEquals(DevicePowerAction.REBOOT, request.action)
            assertTrue(environment.powerCalls.isEmpty())

            viewModel.confirmPowerAction()
            runCurrent()
            assertEquals(
                listOf(PowerCall(AppTarget.REMOTE, remote.id, DevicePowerAction.REBOOT)),
                environment.powerCalls,
            )
            assertEquals(uiText(R.string.power_request_sent, remote.displayName), viewModel.state.value.powerMessage)
            assertNull(viewModel.state.value.pendingPowerAction)
            assertFalse(viewModel.state.value.executingPowerAction)
        }

    @Test
    fun `power action cannot be sent twice while executing`() =
        runViewModelTest {
            val environment = FakeDashboardEnvironment()
            environment.powerGate = CompletableDeferred()
            val viewModel = DashboardViewModel(environment)
            runCurrent()
            viewModel.setTarget(AppTarget.LOCAL)
            runCurrent()
            viewModel.requestPowerAction(DevicePowerAction.SHUTDOWN)
            viewModel.confirmPowerAction()
            assertTrue(viewModel.state.value.executingPowerAction)
            viewModel.requestPowerAction(DevicePowerAction.RECOVERY)
            viewModel.confirmPowerAction()
            assertNull(viewModel.state.value.pendingPowerAction)
            environment.powerGate?.complete(Unit)
            runCurrent()
            assertEquals(1, environment.powerCalls.size)
            assertFalse(viewModel.state.value.executingPowerAction)
        }

    @Test
    fun `changing target or device invalidates a pending power action`() =
        runViewModelTest {
            val environment =
                FakeDashboardEnvironment(
                    sessionsState = DashboardSessionState(devices = listOf(remote), activeDevice = remote),
                )
            val viewModel = DashboardViewModel(environment)
            runCurrent()
            viewModel.setTarget(AppTarget.REMOTE)
            runCurrent()
            viewModel.requestPowerAction(DevicePowerAction.RECOVERY)
            viewModel.setTarget(AppTarget.LOCAL)
            assertNull(viewModel.state.value.pendingPowerAction)

            viewModel.setTarget(AppTarget.REMOTE)
            runCurrent()
            viewModel.requestPowerAction(DevicePowerAction.RECOVERY)
            environment.sessionsFlow.value = DashboardSessionState(devices = emptyList())
            runCurrent()
            assertNull(viewModel.state.value.pendingPowerAction)
            assertTrue(environment.powerCalls.isEmpty())
        }

    @Test
    fun `failed power request is reported without retrying or claiming success`() =
        runViewModelTest {
            val environment = FakeDashboardEnvironment(powerFailure = IllegalStateException("shell denied"))
            val viewModel = DashboardViewModel(environment)
            runCurrent()
            viewModel.setTarget(AppTarget.LOCAL)
            runCurrent()
            viewModel.requestPowerAction(DevicePowerAction.REBOOT)
            viewModel.confirmPowerAction()
            runCurrent()

            assertEquals(1, environment.powerCalls.size)
            assertEquals(
                uiText(R.string.power_request_unconfirmed, phone.displayName, "shell denied"),
                viewModel.state.value.powerMessage,
            )
            assertFalse(viewModel.state.value.executingPowerAction)
        }

    @Test
    fun `settings update auto refresh state`() =
        runViewModelTest {
            val environment = FakeDashboardEnvironment()
            val viewModel = DashboardViewModel(environment)
            runCurrent()

            environment.settingsFlow.value =
                AppSettings(
                    autoRefreshMetrics = true,
                    metricsRefreshSeconds = 15,
                )
            runCurrent()

            assertTrue(viewModel.state.value.autoRefresh)
            assertEquals(15, viewModel.state.value.refreshSeconds)
        }
}

private data class PowerCall(
    val target: AppTarget,
    val deviceId: String,
    val action: DevicePowerAction,
)

private class FakeDashboardEnvironment(
    sessionsState: DashboardSessionState = DashboardSessionState(),
    settings: AppSettings = AppSettings(),
    var localDeviceInfo: DeviceInfo? = phone,
    var localMetricsValue: DeviceMetrics = metrics,
    var remoteMetricsValue: DeviceMetrics? = metrics,
    var powerFailure: Exception? = null,
) : DashboardEnvironment {
    val sessionsFlow = MutableStateFlow(sessionsState)
    val settingsFlow = MutableStateFlow(settings)
    val ticksFlow = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    var localMetricsCalls = 0
    val powerCalls = mutableListOf<PowerCall>()
    var powerGate: CompletableDeferred<Unit>? = null

    override val sessions = sessionsFlow
    override val settings = settingsFlow
    override val ticks = ticksFlow

    override fun connectedDevices(): List<DeviceInfo> = sessionsFlow.value.devices

    override fun activeDevice(): DeviceInfo? = sessionsFlow.value.activeDevice

    override suspend fun localDevice(): DeviceInfo? = localDeviceInfo

    override suspend fun localMetrics(): DeviceMetrics {
        localMetricsCalls += 1
        return localMetricsValue
    }

    override suspend fun remoteMetrics(): DeviceMetrics? = remoteMetricsValue

    override suspend fun executePowerAction(
        target: AppTarget,
        deviceId: String,
        action: DevicePowerAction,
    ) {
        powerCalls += PowerCall(target, deviceId, action)
        powerGate?.await()
        powerFailure?.let { throw it }
    }
}

private val phone =
    DeviceInfo(
        id = "local:Pixel",
        model = "Pixel",
        manufacturer = "Google",
        sdk = 35,
        androidVersion = "15",
        isTv = false,
        hasRoot = false,
        abi = "arm64-v8a",
    )

private val remote = phone.copy(id = "192.0.2.10:5555", model = "Remote")

private val metrics =
    DeviceMetrics(
        totalRamKb = 8_000,
        usedRamKb = 4_000,
        freeRamKb = 4_000,
    )
