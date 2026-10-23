package com.optimizer.tools.feature.install

import android.net.Uri
import com.optimizer.tools.R
import com.optimizer.tools.core.ui.asUiText
import com.optimizer.tools.core.ui.uiText
import com.optimizer.tools.domain.model.ShizukuLocalStatus
import com.optimizer.tools.testutil.runViewModelTest
import dev.adboptimizer.core.adb.RemoteApkInstallOutcome
import dev.adboptimizer.core.model.ManagedApp
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class InstallViewModelTest {
    @Test
    fun `install progress is reflected in state`() =
        runViewModelTest {
            val environment = FakeInstallEnvironment()
            val viewModel = InstallViewModel(environment)
            runCurrent()

            environment.progressFlow.value =
                InstallProgress(
                    running = true,
                    progress = "Instalando 1/2",
                    message = null,
                )
            runCurrent()

            assertTrue(viewModel.state.value.running)
            assertEquals("Instalando 1/2".asUiText(), viewModel.state.value.progress)
        }

    @Test
    fun `host apps are skipped when shizuku is not installed`() =
        runViewModelTest {
            val environment =
                FakeInstallEnvironment(
                    shizukuStatus = ShizukuLocalStatus.NOT_INSTALLED,
                    apps = listOf(app),
                )
            val viewModel = InstallViewModel(environment)

            viewModel.onEnter()
            runCurrent()

            assertFalse(viewModel.state.value.hostAppsLoading)
            assertEquals(emptyList<ManagedApp>(), viewModel.state.value.hostApps)
            assertEquals(
                uiText(R.string.install_error_no_host_apps),
                viewModel.state.value.hostAppsError,
            )
        }

    @Test
    fun `host apps are loaded when shizuku is available`() =
        runViewModelTest {
            val environment =
                FakeInstallEnvironment(
                    shizukuStatus = ShizukuLocalStatus.READY,
                    apps = listOf(app),
                )
            val viewModel = InstallViewModel(environment)

            viewModel.onEnter()
            runCurrent()

            assertFalse(viewModel.state.value.hostAppsLoading)
            assertEquals(listOf(app), viewModel.state.value.hostApps)
            assertEquals(null, viewModel.state.value.hostAppsError)
        }

    @Test
    fun `installing without a remote session emits a failure event`() =
        runViewModelTest {
            val environment = FakeInstallEnvironment(hasSession = false)
            val viewModel = InstallViewModel(environment)
            val events = mutableListOf<InstallEvent>()
            val collector = launch { viewModel.events.collect { events += it } }
            runCurrent()

            viewModel.installHostApps(listOf("com.example.app"))
            runCurrent()

            assertEquals(
                listOf(InstallEvent.Fail("error.notConnected", null)),
                events,
            )
            assertFalse(environment.startCalled)
            collector.cancel()
        }

    @Test
    fun `installing with a remote session starts the background operation`() =
        runViewModelTest {
            val environment = FakeInstallEnvironment(hasSession = true)
            val viewModel = InstallViewModel(environment)

            viewModel.installHostApps(listOf("com.example.app"))
            runCurrent()

            assertTrue(environment.startCalled)
            assertFalse(environment.installRunning)
        }

    @Test
    fun `an operation already running is not started again`() =
        runViewModelTest {
            val environment = FakeInstallEnvironment(hasSession = true, installRunning = true)
            val viewModel = InstallViewModel(environment)

            viewModel.installHostApps(listOf("com.example.app"))
            runCurrent()

            assertFalse(environment.startCalled)
        }
}

private class FakeInstallEnvironment(
    shizukuStatus: ShizukuLocalStatus = ShizukuLocalStatus.READY,
    private val apps: List<ManagedApp> = emptyList(),
    private val hasSession: Boolean = true,
    val installRunning: Boolean = false,
) : InstallEnvironment {
    var shizukuStatus = shizukuStatus
    val progressFlow = MutableStateFlow(InstallProgress())
    var startCalled = false

    override val progress = progressFlow

    override fun isInstallRunning(): Boolean = installRunning

    override fun startInstall(operation: suspend ((String) -> Unit) -> List<RemoteApkInstallOutcome>) {
        startCalled = true
    }

    override fun hasRemoteSession(): Boolean = hasSession

    override fun shizukuStatus(): ShizukuLocalStatus = shizukuStatus

    override suspend fun hostApps(): List<ManagedApp> = apps

    override suspend fun installApks(
        uris: List<Uri>,
        onProgress: (String) -> Unit,
    ): List<RemoteApkInstallOutcome> = emptyList()

    override suspend fun installHostApps(
        packageNames: List<String>,
        onProgress: (String) -> Unit,
    ): List<RemoteApkInstallOutcome> = emptyList()
}

private val app = ManagedApp(packageName = "com.example.app", label = "Example")
