package com.optimizer.tools.app

import com.optimizer.tools.R
import com.optimizer.tools.core.ui.uiText
import com.optimizer.tools.testutil.runViewModelTest
import dev.adboptimizer.core.model.ActionSpec
import dev.adboptimizer.core.model.AppTarget
import dev.adboptimizer.core.model.DeviceInfo
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModelTest {
    @Test
    fun `initial state mirrors the active session`() =
        runViewModelTest {
            val environment =
                FakeMainEnvironment(
                    MainSessionState(devices = listOf(phone), activeDevice = phone),
                )
            val viewModel = MainViewModel(environment)
            val navigation = collectNavigation(viewModel)
            runCurrent()

            assertEquals(phone, viewModel.state.value.connected)
            assertEquals(listOf(phone), viewModel.state.value.connectedDevices)
            assertEquals(AppTarget.LOCAL, viewModel.state.value.activeTarget)
            assertEquals(listOf(phone), environment.startedSessions)
            assertTrue(navigation.isEmpty())
        }

    @Test
    fun `remote target without connection opens the connect screen`() =
        runViewModelTest {
            val viewModel = MainViewModel(FakeMainEnvironment())
            val navigation = collectNavigation(viewModel)
            runCurrent()

            viewModel.setActiveTarget(AppTarget.REMOTE)
            runCurrent()

            assertEquals(listOf(Screen.CONNECT), navigation)
            assertEquals(AppTarget.LOCAL, viewModel.state.value.activeTarget)
        }

    @Test
    fun `remote target with connection changes the active target`() =
        runViewModelTest {
            val viewModel =
                MainViewModel(
                    FakeMainEnvironment(MainSessionState(devices = listOf(phone), activeDevice = phone)),
                )
            val navigation = collectNavigation(viewModel)
            runCurrent()

            viewModel.setActiveTarget(AppTarget.REMOTE)
            runCurrent()

            assertEquals(AppTarget.REMOTE, viewModel.state.value.activeTarget)
            assertTrue(navigation.isEmpty())
        }

    @Test
    fun `adding a device while requested returns to device center`() =
        runViewModelTest {
            val environment = FakeMainEnvironment()
            val viewModel = MainViewModel(environment)
            val navigation = collectNavigation(viewModel)
            runCurrent()

            viewModel.openAddDevice()
            runCurrent()
            assertEquals(listOf(Screen.ADD_DEVICE), navigation)

            environment.sessionsFlow.value = MainSessionState(devices = listOf(phone), activeDevice = phone)
            runCurrent()

            assertEquals(phone, viewModel.state.value.connected)
            assertEquals(listOf(Screen.ADD_DEVICE, Screen.CONNECT), navigation)
            assertEquals(AppTarget.REMOTE, viewModel.state.value.activeTarget)
        }

    @Test
    fun `disconnecting the remote returns to local home`() =
        runViewModelTest {
            val environment =
                FakeMainEnvironment(
                    MainSessionState(devices = listOf(remote), activeDevice = remote),
                )
            val viewModel = MainViewModel(environment)
            val navigation = collectNavigation(viewModel)
            runCurrent()
            viewModel.setActiveTarget(AppTarget.REMOTE)
            runCurrent()

            environment.sessionsFlow.value = MainSessionState()
            runCurrent()

            assertNull(viewModel.state.value.connected)
            assertEquals(listOf(Screen.HOME), navigation)
            assertEquals(AppTarget.LOCAL, viewModel.state.value.activeTarget)
        }

    @Test
    fun `tv launcher requires a remote tv`() =
        runViewModelTest {
            val environment =
                FakeMainEnvironment(
                    MainSessionState(devices = listOf(phone), activeDevice = phone),
                )
            val viewModel = MainViewModel(environment)
            val navigation = collectNavigation(viewModel)
            runCurrent()
            viewModel.setActiveTarget(AppTarget.REMOTE)
            runCurrent()

            viewModel.openTvLauncher()
            runCurrent()

            assertEquals("error.network", viewModel.state.value.errorKey)
            assertEquals(uiText(R.string.app_error_launcher_tv_only), viewModel.state.value.errorDetail)
            assertTrue(navigation.isEmpty())
        }
}

@OptIn(ExperimentalCoroutinesApi::class)
private fun TestScope.collectNavigation(viewModel: MainViewModel): MutableList<Screen> {
    val screens = mutableListOf<Screen>()
    backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
        viewModel.navigation.collect { screens += it }
    }
    runCurrent()
    return screens
}

private class FakeMainEnvironment(
    initialState: MainSessionState = MainSessionState(),
) : MainEnvironment {
    val sessionsFlow = MutableStateFlow(initialState)
    val startedSessions = mutableListOf<DeviceInfo>()
    private val initialActiveId = initialState.activeDevice?.id

    override val sessions = sessionsFlow
    override val actions: List<ActionSpec> = emptyList()

    override fun activeDevice(): DeviceInfo? = sessionsFlow.value.activeDevice

    override fun activeSessionId(): String? = initialActiveId

    override fun connectedDevices(): List<DeviceInfo> = sessionsFlow.value.devices

    override fun startSessionService(device: DeviceInfo) {
        startedSessions += device
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

private val remote = phone.copy(id = "192.0.2.20:5555", model = "Remote", isTv = true)
