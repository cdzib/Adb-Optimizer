package com.optimizer.tools.feature.logcat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LogcatParserTest {
    private val raw =
        """
        --------- beginning of main
        I/ActivityManager( 1042): Start proc com.example.player
        W/PackageManager( 1042): Package no debuggable
        E/AndroidRuntime( 2210): FATAL EXCEPTION: main
        lineas sueltas que no son del log
        """.trimIndent()

    @Test
    fun `separa nivel, etiqueta, pid y mensaje`() {
        val entries = LogcatParser.parse(raw)

        assertEquals(3, entries.size)
        assertEquals('I', entries[0].level)
        assertEquals("ActivityManager", entries[0].tag)
        assertEquals("1042", entries[0].pid)
        assertEquals("Start proc com.example.player", entries[0].message)
        assertTrue(entries[2].line.startsWith("E/AndroidRuntime"))
    }

    @Test
    fun `descarta lo que no tiene formato de logcat`() {
        val entries = LogcatParser.parse("basura\n\n  \nD/Ok(1): vale")

        assertEquals(1, entries.size)
        assertEquals("vale", entries.single().message)
    }

    @Test
    fun `filtra por nivel minimo y por texto`() {
        val entries = LogcatParser.parse(raw)

        assertEquals(listOf('W', 'E'), LogcatParser.filter(entries, 'W', "").map { it.level })
        assertEquals(1, LogcatParser.filter(entries, 'V', "runtime").size)
        assertEquals(1, LogcatParser.filter(entries, 'V', "PLAYER").size)
        assertTrue(LogcatParser.filter(entries, 'V', "inexistente").isEmpty())
    }

    @Test
    fun `un nivel desconocido no rompe el filtro`() {
        val entries = LogcatParser.parse(raw)

        assertEquals(entries.size, LogcatParser.filter(entries, 'Z', "").size)
    }
}
