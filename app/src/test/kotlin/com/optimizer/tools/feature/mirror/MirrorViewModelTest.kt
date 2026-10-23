package com.optimizer.tools.feature.mirror

import android.view.Surface
import com.optimizer.tools.R
import com.optimizer.tools.core.ui.uiText
import com.optimizer.tools.testutil.runViewModelTest
import dev.adboptimizer.core.adb.scrcpy.ScrcpyVideoStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class MirrorViewModelTest {
    @Test
    fun `start without a surface does not open a video stream`() =
        runViewModelTest {
            val environment = FakeMirrorEnvironment()
            val viewModel = MirrorViewModel(environment)

            viewModel.start()

            assertFalse(environment.openCalled)
            assertEquals(uiText(R.string.video_status_connecting), viewModel.state.value.status)
            assertFalse(viewModel.state.value.live)
        }

    @Test
    fun `press key before a stream is ignored`() =
        runViewModelTest {
            val environment = FakeMirrorEnvironment()
            val viewModel = MirrorViewModel(environment)

            viewModel.pressKey(4)

            assertFalse(viewModel.state.value.live)
            assertEquals(null, viewModel.state.value.error)
        }

    @Test
    fun `touch before a stream is ignored`() =
        runViewModelTest {
            val environment = FakeMirrorEnvironment()
            val viewModel = MirrorViewModel(environment)

            viewModel.sendTouch(action = 0, x = 0.5f, y = 0.5f)

            assertFalse(viewModel.state.value.live)
            assertEquals(null, viewModel.state.value.error)
        }

    @Test
    fun `clearing the surface without a stream is safe`() =
        runViewModelTest {
            val viewModel = MirrorViewModel(FakeMirrorEnvironment())

            viewModel.onSurfaceChanged(null)

            assertFalse(viewModel.state.value.live)
        }
}

private class FakeMirrorEnvironment : MirrorEnvironment {
    var openCalled = false

    override fun hasDevice(): Boolean = true

    override fun isTv(): Boolean = false

    override suspend fun readInputCapabilities(): MirrorInputCapabilities = MirrorInputCapabilities(inputPermissionGranted = true)

    override suspend fun openVideo(onLog: (String) -> Unit): ScrcpyVideoStream {
        openCalled = true
        error("El test no debe abrir video sin una superficie.")
    }

    override fun createDecoder(
        surface: Surface,
        width: Int,
        height: Int,
    ): MirrorDecoder = error("El test no debe crear un decoder sin una superficie.")
}
