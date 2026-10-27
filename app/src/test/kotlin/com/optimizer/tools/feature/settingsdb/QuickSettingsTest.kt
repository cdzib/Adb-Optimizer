package com.optimizer.tools.feature.settingsdb

import com.optimizer.tools.data.SettingsDbRepository
import dev.adboptimizer.core.shell.AdbShell
import dev.adboptimizer.core.shell.ShellResult
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class QuickSettingsTest {
    @Test
    fun `lee el modo noche del reporte del dispositivo`() {
        assertEquals(NightMode.YES, QuickSettingsParser.nightMode("Night mode: yes"))
        assertEquals(NightMode.NO, QuickSettingsParser.nightMode("Night mode: no\n"))
        assertEquals(NightMode.AUTO, QuickSettingsParser.nightMode("AUTO"))
        assertNull(QuickSettingsParser.nightMode("error"))
    }

    @Test
    fun `lee numeros y booleanos con los formatos del dispositivo`() {
        assertEquals(30000, QuickSettingsParser.int("30000"))
        assertEquals(128, QuickSettingsParser.int(" 128 "))
        assertNull(QuickSettingsParser.int("null"))

        assertEquals(true, QuickSettingsParser.bool("1"))
        assertEquals(false, QuickSettingsParser.bool("0"))
        assertNull(QuickSettingsParser.bool("quizas"))
    }

    @Test
    fun `aplica modo noche, tiempo, brillo y ahorro y los confirma`() =
        runTest {
            val shell = FakeSettingsShell()
            val repository = SettingsDbRepository(shell, pause = {})

            assertTrue(repository.setNightMode(NightMode.YES))
            assertTrue(repository.setScreenTimeout(30_000))
            assertTrue(repository.setBrightness(64))
            assertTrue(repository.setBatterySaver(true))

            val quick = repository.quickSettings()
            assertEquals(NightMode.YES, quick.nightMode)
            assertEquals(30_000, quick.timeoutMs)
            assertEquals(64, quick.brightness)
            assertEquals(true, quick.batterySaver)
        }

    @Test
    fun `no confirma cuando el dispositivo conserva el valor anterior`() =
        runTest {
            val shell = FakeSettingsShell().apply { rejectWrites = true }
            val repository = SettingsDbRepository(shell, pause = {})

            assertFalse(repository.setScreenTimeout(30_000))
            assertFalse(repository.setBrightness(200))
        }

    private class FakeSettingsShell : AdbShell {
        var rejectWrites = false
        private val values =
            mutableMapOf(
                "system/screen_off_timeout" to "15000",
                "system/screen_brightness" to "128",
                "global/low_power" to "0",
            )
        private var night = "no"

        override suspend fun exec(
            command: String,
            timeoutMs: Long,
        ): ShellResult {
            val output =
                when {
                    command == "cmd uimode night" -> {
                        "Night mode: $night"
                    }

                    command.startsWith("cmd uimode night ") -> {
                        if (!rejectWrites) night = command.removePrefix("cmd uimode night ")
                        ""
                    }

                    command.startsWith("settings put ") -> {
                        if (!rejectWrites) {
                            val parts = command.removePrefix("settings put ").split(" ")
                            values["${parts[0]}/${parts[1].trim('\'')}"] = parts[2].trim('\'')
                        }
                        ""
                    }

                    command.startsWith("settings get ") -> {
                        val parts = command.removePrefix("settings get ").split(" ")
                        values["${parts[0]}/${parts[1].trim('\'')}"] ?: "null"
                    }

                    else -> {
                        ""
                    }
                }
            return ShellResult(0, output, "", 1)
        }
    }
}
