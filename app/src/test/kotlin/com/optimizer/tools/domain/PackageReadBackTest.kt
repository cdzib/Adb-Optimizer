package com.optimizer.tools.domain

import com.optimizer.tools.domain.apk.PackageReadBack
import dev.adboptimizer.core.shell.AdbShell
import dev.adboptimizer.core.shell.ShellResult
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PackageReadBackTest {
    @Test
    fun `un bucket vale por nombre o por numero`() {
        val readBack = PackageReadBack.standby("com.example.player")

        assertTrue(readBack.matches("restricted"))
        assertTrue(readBack.matches("45"))
        assertFalse(readBack.matches("40"))
        assertFalse(readBack.matches(""))
    }

    @Test
    fun `el estado de compilacion acepta speed y speed-profile`() {
        val readBack = PackageReadBack.aot("com.example.player")

        assertTrue(readBack.matches("arm64: [status=speed] [reason=cmdline]"))
        assertTrue(readBack.matches("arm64: [status=speed-profile] [reason=bg-dexopt]"))
        assertFalse(readBack.matches("arm64: [status=verify] [reason=install]"))
    }

    @Test
    fun `reintenta la lectura hasta que el dispositivo confirma`() =
        runTest {
            val shell = RecordingShell(listOf("No operations.", "RUN_IN_BACKGROUND: ignore"))
            val readBack = PackageReadBack.backgroundOp("com.example.player")

            assertTrue(readBack.confirm(shell, attempts = 3, pauseMs = 1))
            assertEquals(2, shell.commands.size)
        }

    @Test
    fun `un valor que nunca aparece no se da por bueno`() =
        runTest {
            val shell = RecordingShell(listOf("No operations.", "No operations.", "No operations."))
            val readBack = PackageReadBack.backgroundOp("com.example.player")

            assertFalse(readBack.confirm(shell, attempts = 2, pauseMs = 1))
            assertEquals(2, shell.commands.size)
        }

    @Test
    fun `un fallo de lectura no se confunde con un cambio pendiente`() =
        runTest {
            val shell = RecordingShell(emptyList(), failWith = IllegalStateException("shell cerrado"))
            val readBack = PackageReadBack.standby("com.example.player")

            assertFalse(readBack.confirm(shell, attempts = 2, pauseMs = 1))
            assertEquals(2, shell.commands.size)
        }

    private class RecordingShell(
        private val outputs: List<String>,
        private val failWith: Exception? = null,
    ) : AdbShell {
        val commands = mutableListOf<String>()

        override suspend fun exec(
            command: String,
            timeoutMs: Long,
        ): ShellResult {
            commands += command
            failWith?.let { throw it }
            return ShellResult(0, outputs.getOrElse(commands.size - 1) { outputs.lastOrNull() ?: "" }, "", 1)
        }
    }
}
