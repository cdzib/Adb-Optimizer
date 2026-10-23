package com.optimizer.tools.feature.shizuku

import com.optimizer.tools.domain.model.ShizukuLocalStatus
import com.optimizer.tools.testutil.runViewModelTest
import org.junit.Assert.assertEquals
import org.junit.Test

class ShizukuViewModelTest {
    @Test
    fun `refresh reads the current status`() =
        runViewModelTest {
            val environment = FakeShizukuEnvironment(ShizukuLocalStatus.PERMISSION_REQUIRED)
            val viewModel = ShizukuViewModel(environment)

            assertEquals(ShizukuLocalStatus.PERMISSION_REQUIRED, viewModel.status.value)

            environment.currentStatus = ShizukuLocalStatus.READY
            viewModel.refresh()

            assertEquals(ShizukuLocalStatus.READY, viewModel.status.value)
        }

    @Test
    fun `request permission delegates and refreshes`() =
        runViewModelTest {
            val environment = FakeShizukuEnvironment(ShizukuLocalStatus.STOPPED)
            val viewModel = ShizukuViewModel(environment)

            environment.currentStatus = ShizukuLocalStatus.PERMISSION_REQUIRED
            viewModel.requestPermission()

            assertEquals(1, environment.requestCount)
            assertEquals(ShizukuLocalStatus.PERMISSION_REQUIRED, viewModel.status.value)
        }

    @Test
    fun `denied permission result keeps the reported status`() =
        runViewModelTest {
            val environment = FakeShizukuEnvironment(ShizukuLocalStatus.PERMISSION_REQUIRED)
            val viewModel = ShizukuViewModel(environment)

            viewModel.onPermissionResult(granted = false)

            assertEquals(ShizukuLocalStatus.PERMISSION_REQUIRED, viewModel.status.value)
        }
}

private class FakeShizukuEnvironment(
    var currentStatus: ShizukuLocalStatus,
) : ShizukuEnvironment {
    var requestCount = 0

    override fun status(): ShizukuLocalStatus = currentStatus

    override fun requestPermission() {
        requestCount += 1
    }
}
