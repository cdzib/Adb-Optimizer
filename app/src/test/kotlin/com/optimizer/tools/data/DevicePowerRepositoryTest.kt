package com.optimizer.tools.data

import com.optimizer.tools.domain.model.DevicePowerAction
import dev.adboptimizer.core.shell.AdbException
import dev.adboptimizer.core.shell.AdbShell
import dev.adboptimizer.core.shell.ShellResult
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DevicePowerRepositoryTest {
    @Test
    fun `sends only the selected power command without retrying`() =
        runTest {
            val shell = FakeShell()
            val repository = DevicePowerRepository(shell, pause = {})

            listOf(
                DevicePowerAction.REBOOT,
                DevicePowerAction.SHUTDOWN,
                DevicePowerAction.RECOVERY,
                DevicePowerAction.BOOTLOADER,
            ).forEach { repository.execute(it) }

            assertEquals(
                listOf(
                    "svc power reboot",
                    "svc power shutdown",
                    "svc power reboot recovery",
                    "svc power reboot bootloader",
                ),
                shell.commands,
            )
        }

    @Test
    fun `reiniciar la interfaz se confirma con un PID nuevo`() =
        runTest {
            val shell = FakeShell(pids = listOf("100", "200"))
            val repository = DevicePowerRepository(shell, pause = {})

            repository.execute(DevicePowerAction.RESTART_SYSTEM_UI)

            assertEquals("am crash com.android.systemui", shell.commands[1])
            assertEquals(2, shell.commands.count { it.startsWith("pidof") })
        }

    @Test
    fun `reiniciar la interfaz falla si el PID no cambia`() =
        runTest {
            val shell = FakeShell(pids = listOf("100"))
            val repository = DevicePowerRepository(shell, pause = {})

            val error = runCatching { repository.execute(DevicePowerAction.RESTART_SYSTEM_UI) }.exceptionOrNull()

            assertTrue(error is AdbException)
            assertTrue(error?.message.orEmpty().contains("PID"))
        }

    private class FakeShell(
        pids: List<String> = emptyList(),
    ) : AdbShell {
        val commands = mutableListOf<String>()
        private val queue = ArrayDeque(pids)
        private var lastPid: String = pids.lastOrNull() ?: "100"

        override suspend fun exec(
            command: String,
            timeoutMs: Long,
        ): ShellResult {
            commands += command
            if (command.startsWith("pidof")) {
                val next = queue.removeFirstOrNull() ?: lastPid
                lastPid = next
                return ShellResult(0, next, "", 1)
            }
            return ShellResult(0, "", "", 1)
        }
    }
}
