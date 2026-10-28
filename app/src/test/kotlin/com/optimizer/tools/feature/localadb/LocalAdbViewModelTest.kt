package com.optimizer.tools.feature.localadb

import com.optimizer.tools.feature.localadb.domain.LocalAdbErrorKind
import com.optimizer.tools.feature.localadb.domain.LocalAdbException
import com.optimizer.tools.feature.localadb.domain.LocalAdbPhase
import com.optimizer.tools.feature.localadb.domain.ShellCommandResult
import com.optimizer.tools.feature.localadb.presentation.LocalAdbEnvironment
import com.optimizer.tools.feature.localadb.presentation.LocalAdbViewModel
import com.optimizer.tools.testutil.runViewModelTest
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LocalAdbViewModelTest {
    @Test
    fun `app resume does not prompt when the key was never authorized`() =
        runViewModelTest {
            val environment = FakeLocalAdbEnvironment(enabled = true, authorized = false)
            val viewModel = LocalAdbViewModel(environment)

            viewModel.onAppResumed()
            advanceUntilIdle()

            assertEquals(0, environment.connectCalls)
        }

    @Test
    fun `app resume reconnects silently after authorization`() =
        runViewModelTest {
            val environment = FakeLocalAdbEnvironment(enabled = true, authorized = true)
            val viewModel = LocalAdbViewModel(environment)
            advanceUntilIdle()
            viewModel.onAppResumed()
            advanceUntilIdle()

            assertEquals(LocalAdbPhase.CONNECTED, viewModel.state.value.phase)
        }

    @Test
    fun `debugging off keeps the guidance state`() =
        runViewModelTest {
            val environment = FakeLocalAdbEnvironment(enabled = false)
            val viewModel = LocalAdbViewModel(environment)
            viewModel.onResume()

            advanceUntilIdle()

            assertEquals(LocalAdbPhase.DEBUGGING_OFF, viewModel.state.value.phase)
            assertEquals(0, environment.connectCalls)
        }

    @Test
    fun `previously authorized session reconnects automatically`() =
        runViewModelTest {
            val environment = FakeLocalAdbEnvironment(authorized = true)
            val viewModel = LocalAdbViewModel(environment)

            advanceUntilIdle()

            assertEquals(LocalAdbPhase.CONNECTED, viewModel.state.value.phase)
            assertEquals(1, environment.connectCalls)
        }

    @Test
    fun `successful connection reaches connected`() =
        runViewModelTest {
            val environment = FakeLocalAdbEnvironment()
            val viewModel = LocalAdbViewModel(environment)
            viewModel.onResume()

            advanceUntilIdle()

            assertEquals(LocalAdbPhase.CONNECTED, viewModel.state.value.phase)
            assertEquals(1, environment.connectCalls)
            assertEquals(0, viewModel.state.value.attempt)
        }

    @Test
    fun `closed port is reported without retries`() =
        runViewModelTest {
            val environment = FakeLocalAdbEnvironment(connectError = { LocalAdbException.PortClosed() })
            val viewModel = LocalAdbViewModel(environment)
            viewModel.onResume()

            advanceUntilIdle()

            assertEquals(LocalAdbPhase.ERROR, viewModel.state.value.phase)
            assertEquals(LocalAdbErrorKind.PORT_CLOSED, viewModel.state.value.errorKind)
            assertEquals(1, environment.connectCalls)
        }

    @Test
    fun `authorization is retried with backoff until connected`() =
        runViewModelTest {
            val environment =
                FakeLocalAdbEnvironment(
                    connectError = { LocalAdbException.AuthorizationTimeout() },
                    failConnectTimes = 2,
                )
            val viewModel = LocalAdbViewModel(environment)
            viewModel.onResume()

            advanceUntilIdle()

            assertEquals(LocalAdbPhase.CONNECTED, viewModel.state.value.phase)
            assertEquals(3, environment.connectCalls)
        }

    @Test
    fun `authorization exhausted ends in error`() =
        runViewModelTest {
            val environment =
                FakeLocalAdbEnvironment(
                    connectError = { LocalAdbException.AuthorizationTimeout() },
                    failConnectTimes = 5,
                )
            val viewModel = LocalAdbViewModel(environment)
            viewModel.onResume()

            advanceUntilIdle()

            assertEquals(LocalAdbPhase.ERROR, viewModel.state.value.phase)
            assertEquals(LocalAdbErrorKind.AUTHORIZATION, viewModel.state.value.errorKind)
            assertEquals(3, environment.connectCalls)
        }

    @Test
    fun `shell result is appended with exit code`() =
        runViewModelTest {
            val environment = FakeLocalAdbEnvironment()
            val viewModel = LocalAdbViewModel(environment)
            viewModel.onResume()
            advanceUntilIdle()

            viewModel.setCommand("pm list packages -3")
            viewModel.run()
            advanceUntilIdle()

            val entry =
                viewModel.state.value.entries
                    .single()
            assertEquals("pm list packages -3", entry.command)
            assertEquals(0, entry.exitCode)
            assertEquals("com.example\n", entry.stdout)
            assertFalse(viewModel.state.value.running)
            assertEquals("", viewModel.state.value.command)
        }
}

private class FakeLocalAdbEnvironment(
    private val enabled: Boolean = true,
    private val authorized: Boolean = false,
    private val connectError: () -> LocalAdbException? = { null },
    private val failConnectTimes: Int = Int.MAX_VALUE,
) : LocalAdbEnvironment {
    var connectCalls = 0
    var disconnected = false

    override fun isAdbEnabled(): Boolean = enabled

    override fun isDeveloperOptionsEnabled(): Boolean = false

    override fun isAuthorized(): Boolean = authorized

    override suspend fun connect() {
        connectCalls += 1
        if (connectCalls <= failConnectTimes) {
            connectError()?.let { throw it }
        }
    }

    override suspend fun run(command: String): ShellCommandResult =
        ShellCommandResult(
            command = command,
            stdout = "com.example\n",
            stderr = "",
            exitCode = 0,
            durationMs = 12,
        )

    override fun disconnect() {
        disconnected = true
    }
}
