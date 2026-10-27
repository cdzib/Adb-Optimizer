package com.optimizer.tools.data

import dev.adboptimizer.core.shell.AdbShell
import dev.adboptimizer.core.shell.ShellResult
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsDbRepositoryTest {
    @Test
    fun `separa clave y valor cortando por el primer igual`() {
        val entries =
            SettingsDbParser.parse(
                """
                screen_brightness=128
                some_key=a=b=c
                sin_igual
                =sin_clave
                """.trimIndent(),
            )

        assertEquals(listOf("screen_brightness", "some_key"), entries.map { it.key })
        assertEquals("a=b=c", entries[1].value)
    }

    @Test
    fun `la lectura de vuelta entiende null y espacios`() {
        assertTrue(SettingsDbParser.readBackMatches("null", ""))
        assertTrue(SettingsDbParser.readBackMatches(" 128\n", "128"))
        assertFalse(SettingsDbParser.readBackMatches("0", "1"))
    }

    @Test
    fun `no confirma cuando el dispositivo nunca conserva el valor`() =
        runTest {
            val shell = FakeShell(reads = listOf("0", "0", "0"))
            val repository = SettingsDbRepository(shell, pause = {})

            assertFalse(repository.save("global", "demo", "1", attempts = 2))
            assertEquals(2, shell.history.count { it.startsWith("settings get") })
        }

    @Test
    fun `confirma cuando el valor aparece en la segunda lectura`() =
        runTest {
            val shell = FakeShell(reads = listOf("0", "1"))
            val repository = SettingsDbRepository(shell, pause = {})

            assertTrue(repository.save("global", "demo", "1"))
        }

    @Test(expected = IllegalArgumentException::class)
    fun `rechaza tablas que no existen`() =
        runTest {
            SettingsDbRepository(FakeShell(), pause = {}).save("personal", "demo", "1")
        }

    private class FakeShell(
        private val reads: List<String> = emptyList(),
    ) : AdbShell {
        val history = mutableListOf<String>()

        override suspend fun exec(
            command: String,
            timeoutMs: Long,
        ): ShellResult {
            history += command
            if (command.startsWith("settings get")) {
                val index = history.count { it.startsWith("settings get") } - 1
                return ShellResult(0, reads.getOrElse(index) { reads.lastOrNull() ?: "null" }, "", 1)
            }
            return ShellResult(0, "", "", 1)
        }
    }
}
