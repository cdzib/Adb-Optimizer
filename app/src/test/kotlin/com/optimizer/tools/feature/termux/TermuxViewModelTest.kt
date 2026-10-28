package com.optimizer.tools.feature.termux

import com.optimizer.tools.feature.termux.domain.TermuxAvailability
import com.optimizer.tools.feature.termux.domain.TermuxErrorKind
import com.optimizer.tools.feature.termux.domain.TermuxException
import com.optimizer.tools.feature.termux.domain.TermuxResult
import com.optimizer.tools.feature.termux.presentation.TermuxEnvironment
import com.optimizer.tools.feature.termux.presentation.TermuxPreset
import com.optimizer.tools.feature.termux.presentation.TermuxViewModel
import com.optimizer.tools.testutil.runViewModelTest
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TermuxViewModelTest {
    @Test
    fun `preset appends the command output`() =
        runViewModelTest {
            val environment = FakeTermuxEnvironment()
            val viewModel = TermuxViewModel(environment)

            viewModel.usePreset(TermuxPreset.TOOLS)
            advanceUntilIdle()

            val entry =
                viewModel.state.value.entries
                    .single()
            assertEquals(TermuxPreset.TOOLS.command, entry.command)
            assertEquals("ok", entry.stdout)
            assertEquals(1, environment.runs)
        }

    @Test
    fun `missing permission blocks the command`() =
        runViewModelTest {
            val environment =
                FakeTermuxEnvironment(
                    availability = TermuxAvailability(installed = true, permissionGranted = false),
                )
            val viewModel = TermuxViewModel(environment)

            viewModel.setCommand("pkg install -y htop")
            viewModel.run()
            advanceUntilIdle()

            assertEquals(TermuxErrorKind.PERMISSION_DENIED, viewModel.state.value.errorKind)
            assertEquals(0, environment.runs)
        }

    @Test
    fun `empty command is ignored`() =
        runViewModelTest {
            val environment = FakeTermuxEnvironment()
            val viewModel = TermuxViewModel(environment)

            viewModel.run()
            advanceUntilIdle()

            assertEquals(0, environment.runs)
            assertNull(viewModel.state.value.errorKind)
        }

    @Test
    fun `start failure surfaces the error kind`() =
        runViewModelTest {
            val environment = FakeTermuxEnvironment(failure = TermuxException.StartFailed())
            val viewModel = TermuxViewModel(environment)

            viewModel.setCommand("sshd")
            viewModel.run()
            advanceUntilIdle()

            assertEquals(TermuxErrorKind.START_FAILED, viewModel.state.value.errorKind)
            assertEquals("sshd", viewModel.state.value.command)
        }

    @Test
    fun `timeout surfaces the error kind`() =
        runViewModelTest {
            val environment = FakeTermuxEnvironment(failure = TermuxException.Timeout())
            val viewModel = TermuxViewModel(environment)

            viewModel.setCommand("pkg install -y python")
            viewModel.run()
            advanceUntilIdle()

            assertEquals(TermuxErrorKind.TIMEOUT, viewModel.state.value.errorKind)
        }
}

private class FakeTermuxEnvironment(
    var availability: TermuxAvailability = TermuxAvailability(installed = true, permissionGranted = true),
    private val failure: TermuxException? = null,
) : TermuxEnvironment {
    var runs = 0
    var result = TermuxResult(executionId = 1, stdout = "ok", stderr = "", exitCode = 0)

    override fun availability(): TermuxAvailability = availability

    override suspend fun run(
        command: String,
        label: String,
    ): TermuxResult {
        runs += 1
        failure?.let { throw it }
        return result
    }

    override suspend fun hasRoot(): Boolean = false

    override suspend fun configureWithRoot(): Boolean = availability.permissionGranted

    override suspend fun grantPermission(): Boolean = availability.permissionGranted

    override fun configureExternalApps(command: String): Boolean = true

    override fun openTermux(): Boolean = true

    override fun openInstallPage() = Unit

    override fun openAppSettings() = Unit
}
