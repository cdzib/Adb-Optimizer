package com.optimizer.tools.feature.tvlauncher

import com.optimizer.tools.core.ui.asUiText
import com.optimizer.tools.testutil.runViewModelTest
import dev.adboptimizer.core.model.DeviceInfo
import dev.adboptimizer.core.model.RemoteLauncher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TvLauncherViewModelTest {
    @Test
    fun `non tv device does not query launchers`() =
        runViewModelTest {
            val environment = FakeTvLauncherEnvironment(activeDeviceInfo = phone)
            val viewModel = TvLauncherViewModel(environment)

            viewModel.onEnter()
            advanceUntilIdle()

            assertFalse(viewModel.state.value.loading)
            assertEquals(emptyList<RemoteLauncher>(), viewModel.state.value.launchers)
            assertEquals(0, environment.homeLauncherCalls)
        }

    @Test
    fun `tv device loads launchers and current launcher`() =
        runViewModelTest {
            val environment =
                FakeTvLauncherEnvironment(
                    activeDeviceInfo = tv,
                    launchers = listOf(launcherA, launcherB),
                    current = launcherA,
                )
            val viewModel = TvLauncherViewModel(environment)

            viewModel.onEnter()
            advanceUntilIdle()

            assertEquals(listOf(launcherA, launcherB), viewModel.state.value.launchers)
            assertEquals(launcherA, viewModel.state.value.current)
            assertFalse(viewModel.state.value.loading)
        }

    @Test
    fun `confirm change updates the current launcher`() =
        runViewModelTest {
            val environment =
                FakeTvLauncherEnvironment(
                    activeDeviceInfo = tv,
                    launchers = listOf(launcherA, launcherB),
                    current = launcherA,
                    newCurrent = launcherB,
                )
            val viewModel = TvLauncherViewModel(environment)
            viewModel.onEnter()
            advanceUntilIdle()

            viewModel.requestChange(launcherB.packageName)
            assertEquals(launcherB.packageName, viewModel.state.value.pending)

            viewModel.confirmChange()
            advanceUntilIdle()

            assertEquals(launcherB.packageName, environment.lastSetPackage)
            assertEquals(launcherB, viewModel.state.value.current)
            assertNull(viewModel.state.value.pending)
            assertNull(viewModel.state.value.updating)
        }

    @Test
    fun `query failure is surfaced without losing the current launcher`() =
        runViewModelTest {
            val environment =
                FakeTvLauncherEnvironment(
                    activeDeviceInfo = tv,
                    launchers = listOf(launcherA),
                    current = launcherA,
                )
            val viewModel = TvLauncherViewModel(environment)
            viewModel.onEnter()
            advanceUntilIdle()
            assertEquals(launcherA, viewModel.state.value.current)

            environment.failHomeLaunchers = true
            viewModel.refresh()
            advanceUntilIdle()

            assertEquals("No se pudieron consultar los launchers de la TV.".asUiText(), viewModel.state.value.error)
            assertEquals(launcherA, viewModel.state.value.current)
            assertFalse(viewModel.state.value.loading)
        }
}

private class FakeTvLauncherEnvironment(
    private val activeDeviceInfo: DeviceInfo?,
    private val launchers: List<RemoteLauncher> = emptyList(),
    private val current: RemoteLauncher? = null,
    private val newCurrent: RemoteLauncher? = null,
    var failHomeLaunchers: Boolean = false,
) : TvLauncherEnvironment {
    var homeLauncherCalls = 0
    var lastSetPackage: String? = null

    override fun activeDevice(): DeviceInfo? = activeDeviceInfo

    override suspend fun homeLaunchers(): List<RemoteLauncher> {
        homeLauncherCalls += 1
        if (failHomeLaunchers) throw IllegalStateException("No se pudieron consultar los launchers de la TV.")
        return launchers
    }

    override suspend fun currentHomeLauncher(): RemoteLauncher? = current

    override suspend fun setHomeLauncher(packageName: String): RemoteLauncher {
        lastSetPackage = packageName
        return newCurrent ?: error("No hay launcher nuevo")
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

private val tv = phone.copy(id = "192.0.2.20:5555", model = "TV", isTv = true)
private val launcherA = RemoteLauncher("com.example.a", "com.example.a.Home")
private val launcherB = RemoteLauncher("com.example.b", "com.example.b.Home")
