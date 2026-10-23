package com.optimizer.tools.data.remote

import dev.adboptimizer.core.shell.AdbShell
import dev.adboptimizer.core.shell.ShellResult
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RemoteInputCapabilitiesRepositoryTest {
    private fun shell(
        permission: String,
        features: String = "",
    ) = object : AdbShell {
        override suspend fun exec(
            command: String,
            timeoutMs: Long,
        ) = ShellResult(
            0,
            if (command == "pm list features") {
                features
            } else {
                when (permission) {
                    "granted" -> "      android.permission.INJECT_EVENTS: granted=true"
                    "denied" -> "      android.permission.INJECT_EVENTS: granted=false"
                    else -> permission
                }
            },
            "",
            1,
        )
    }

    @Test fun `granted permission allows attempting input without claiming verified control`() =
        runTest {
            val result = RemoteInputCapabilitiesRepository(shell("granted", "feature:android.hardware.touchscreen\n")).readCapabilities()
            assertEquals(true, result.inputPermissionGranted)
            assertEquals(true, result.hasTouchscreen)
        }

    @Test fun `TV without touchscreen does not imply input permission is denied`() =
        runTest {
            val result = RemoteInputCapabilitiesRepository(shell("granted", "feature:android.software.leanback\n")).readCapabilities()
            assertEquals(true, result.inputPermissionGranted)
            assertEquals(false, result.hasTouchscreen)
        }

    @Test fun `unsupported permission command remains unknown`() =
        runTest {
            val result = RemoteInputCapabilitiesRepository(shell("Unknown command: check-permission")).readCapabilities()
            assertNull(result.inputPermissionGranted)
        }

    @Test fun `denied permission is reported explicitly`() =
        runTest {
            assertEquals(false, RemoteInputCapabilitiesRepository(shell("denied")).readCapabilities().inputPermissionGranted)
        }
}
