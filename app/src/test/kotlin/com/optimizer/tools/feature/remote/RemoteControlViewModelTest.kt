package com.optimizer.tools.feature.remote

import com.optimizer.tools.core.ui.asUiText
import com.optimizer.tools.domain.model.RemoteKey
import com.optimizer.tools.testutil.runViewModelTest
import dev.adboptimizer.core.model.DeviceInfo
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RemoteControlViewModelTest {
    @Test
    fun `enter exposes the active device name`() =
        runViewModelTest {
            val environment = FakeRemoteControlEnvironment(device = tv)
            val viewModel = RemoteControlViewModel(environment)

            viewModel.onEnter()

            assertEquals("Google TV", viewModel.state.value.deviceName)
        }

    @Test
    fun `presses are sent in order`() =
        runViewModelTest {
            val environment = FakeRemoteControlEnvironment()
            val viewModel = RemoteControlViewModel(environment)

            viewModel.press(RemoteKey.UP)
            viewModel.press(RemoteKey.OK)
            viewModel.press(RemoteKey.BACK)
            advanceUntilIdle()

            assertEquals(listOf(RemoteKey.UP, RemoteKey.OK, RemoteKey.BACK), environment.sent)
        }

    @Test
    fun `disabled controls drop pending presses`() =
        runViewModelTest {
            val environment = FakeRemoteControlEnvironment()
            val viewModel = RemoteControlViewModel(environment)

            viewModel.setControlsEnabled(false)
            viewModel.press(RemoteKey.UP)
            advanceUntilIdle()

            assertEquals(emptyList<RemoteKey>(), environment.sent)
        }

    @Test
    fun `send failure clears device and exposes error`() =
        runViewModelTest {
            val environment = FakeRemoteControlEnvironment(failSend = true)
            val viewModel = RemoteControlViewModel(environment)
            viewModel.onEnter()

            viewModel.press(RemoteKey.OK)
            advanceUntilIdle()

            assertNull(viewModel.state.value.deviceName)
            assertEquals("No se pudo enviar la tecla.".asUiText(), viewModel.state.value.error)
        }

    @Test
    fun `probe mirror collects log and result`() =
        runViewModelTest {
            val environment = FakeRemoteControlEnvironment(probeResult = "Video 1080x1920")
            val viewModel = RemoteControlViewModel(environment)

            viewModel.probeMirror()
            advanceUntilIdle()

            assertEquals(listOf("linea de prueba"), viewModel.state.value.mirrorLog)
            assertEquals("Video 1080x1920", viewModel.state.value.mirrorResult)
            assertFalse(viewModel.state.value.mirrorProbing)
        }
}

private class FakeRemoteControlEnvironment(
    private val device: DeviceInfo? = tv,
    private val failSend: Boolean = false,
    private val probeResult: String = "ok",
) : RemoteControlEnvironment {
    val sent = mutableListOf<RemoteKey>()

    override fun activeDevice(): DeviceInfo? = device

    override suspend fun sendKey(key: RemoteKey) {
        if (failSend) throw IllegalStateException("No se pudo enviar la tecla.")
        sent += key
    }

    override suspend fun probeMirror(onLog: (String) -> Unit): String {
        onLog("linea de prueba")
        return probeResult
    }
}

private val tv =
    DeviceInfo(
        id = "192.0.2.20:5555",
        model = "TV",
        manufacturer = "Google",
        sdk = 35,
        androidVersion = "15",
        isTv = true,
        hasRoot = false,
        abi = "arm64-v8a",
    )
