package com.optimizer.tools.data

import dev.adboptimizer.core.shell.AdbShell
import dev.adboptimizer.core.shell.ShellResult
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LogcatRepositoryTest {
    @Test
    fun `pide las ultimas lineas cuando no hay marca de tiempo`() =
        runTest {
            val shell = FakeShell()
            LogcatRepository(shell).read(limit = 50)

            assertEquals("logcat -d -v threadtime -t 50", shell.commands.single())
        }

    @Test
    fun `pide solo lo posterior cuando hay marca de tiempo`() =
        runTest {
            val shell = FakeShell()
            LogcatRepository(shell).read(limit = 50, since = "01-02 03:04:05.678")

            assertEquals("logcat -d -v threadtime -T '01-02 03:04:05.678'", shell.commands.single())
        }

    @Test
    fun `interpreta las lineas del dispositivo`() =
        runTest {
            val shell = FakeShell("01-02 03:04:05.678  1  2 I Tag : hola")
            val entries = LogcatRepository(shell).read(limit = 10)

            assertEquals(1, entries.size)
            assertTrue(entries.single().message.contains("hola"))
        }

    private class FakeShell(
        private val output: String = "",
    ) : AdbShell {
        val commands = mutableListOf<String>()

        override suspend fun exec(
            command: String,
            timeoutMs: Long,
        ): ShellResult {
            commands += command
            return ShellResult(0, output, "", 1)
        }
    }
}
