package com.optimizer.tools.feature.doze

import com.optimizer.tools.data.DozeRepository
import dev.adboptimizer.core.shell.AdbShell
import dev.adboptimizer.core.shell.ShellResult
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DozeParserTest {
    @Test
    fun `lee el token suelto que imprime get deep y get light`() {
        assertEquals("ACTIVE", DozeParser.token("ACTIVE\n", light = false))
        assertEquals("IDLE_MAINTENANCE", DozeParser.token("IDLE_MAINTENANCE", light = false))
        assertEquals("IDLE", DozeParser.token("IDLE", light = true))
    }

    @Test
    fun `lee el estado dentro del volcado completo`() {
        assertEquals("IDLE_PENDING", DozeParser.token("Stepped to deep: IDLE_PENDING", light = false))
        assertEquals("ACTIVE", DozeParser.token("Light state: ACTIVE, Deep state: SENSING", light = true))
        assertEquals("SENSING", DozeParser.token("Light state: ACTIVE, Deep state: SENSING", light = false))
        assertNull(DozeParser.token("error: no such service", light = false))
    }

    @Test
    fun `interpreta el forzado y ubica la fase`() {
        assertEquals(true, DozeParser.bool("true\nresto"))
        assertEquals(false, DozeParser.bool("false"))
        assertNull(DozeParser.bool("quizas"))

        assertEquals(DozePhase.ACTIVE, DozeParser.phase("ACTIVE"))
        assertEquals(DozePhase.INACTIVE, DozeParser.phase("QUICK_DOZE_DELAY"))
        assertEquals(DozePhase.PENDING, DozeParser.phase("LOCATING"))
        assertEquals(DozePhase.IDLE, DozeParser.phase("IDLE"))
        assertEquals(DozePhase.MAINTENANCE, DozeParser.phase("IDLE_MAINTENANCE"))
        assertEquals(DozePhase.UNKNOWN, DozeParser.phase(null))
    }

    @Test
    fun `forzar solo se da por bueno cuando get force lo confirma`() =
        runTest {
            val shell = FakeShell(force = listOf("false", "true"))
            val status = DozeRepository(shell, pause = {}).run(DozeAction.FORCE)

            assertTrue(shell.commands.contains("dumpsys deviceidle force-idle deep"))
            assertEquals(true, status.forced)
        }

    @Test
    fun `salir de doze se confirma con get force en false`() =
        runTest {
            val shell = FakeShell(force = listOf("true", "false"))
            val status = DozeRepository(shell, pause = {}).run(DozeAction.UNFORCE)

            assertTrue(shell.commands.contains("dumpsys deviceidle unforce"))
            assertFalse(status.forced!!)
        }

    private class FakeShell(
        force: List<String> = emptyList(),
    ) : AdbShell {
        val commands = mutableListOf<String>()
        private val forced = ArrayDeque(force)
        private var last = force.lastOrNull() ?: "false"

        override suspend fun exec(
            command: String,
            timeoutMs: Long,
        ): ShellResult {
            commands += command
            val output =
                when {
                    command.endsWith("get force") -> {
                        val next = forced.removeFirstOrNull() ?: last
                        last = next
                        next
                    }

                    command.endsWith("get deep") -> {
                        "IDLE"
                    }

                    command.endsWith("get light") -> {
                        "IDLE"
                    }

                    else -> {
                        ""
                    }
                }
            return ShellResult(0, output, "", 1)
        }
    }
}
