package com.optimizer.tools.data.remote

import com.optimizer.tools.domain.model.RemoteKey
import dev.adboptimizer.core.shell.AdbShell
import dev.adboptimizer.core.shell.ShellResult
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test
import java.io.IOException

class RemoteControlRepositoryTest {
    private class FakeShell(
        private val exitCode: Int = 0,
        private val output: String = "",
    ) : AdbShell {
        val commands = mutableListOf<String>()

        override suspend fun exec(
            command: String,
            timeoutMs: Long,
        ): ShellResult {
            commands += command
            return ShellResult(exitCode, output, "", 1)
        }
    }

    @Test
    fun `envia el keyevent correspondiente al boton`() =
        runTest {
            val shell = FakeShell()

            RemoteControlRepository(shell).press(RemoteKey.OK)

            assertEquals(listOf("input keyevent KEYCODE_DPAD_CENTER"), shell.commands)
        }

    @Test
    fun `informa el error del shell cuando el comando falla`() =
        runTest {
            val shell = FakeShell(exitCode = 1, output = "permission denied")

            try {
                RemoteControlRepository(shell).press(RemoteKey.HOME)
                fail("Se esperaba IOException")
            } catch (error: IOException) {
                assertEquals("permission denied", error.message)
            }
        }
}
