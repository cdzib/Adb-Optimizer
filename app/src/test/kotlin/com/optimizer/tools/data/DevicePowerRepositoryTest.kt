package com.optimizer.tools.data

import com.optimizer.tools.domain.model.DevicePowerAction
import dev.adboptimizer.core.shell.AdbShell
import dev.adboptimizer.core.shell.ShellResult
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class DevicePowerRepositoryTest {
    @Test
    fun `sends only the selected power command without retrying`() =
        runTest {
            val calls = mutableListOf<String>()
            val shell =
                object : AdbShell {
                    override suspend fun exec(
                        command: String,
                        timeoutMs: Long,
                    ): ShellResult {
                        calls += command
                        return ShellResult(0, "", "", 1)
                    }
                }
            val repository = DevicePowerRepository(shell)
            DevicePowerAction.entries.forEach { repository.execute(it) }
            assertEquals(listOf("svc power reboot", "svc power shutdown", "svc power reboot recovery"), calls)
        }
}
