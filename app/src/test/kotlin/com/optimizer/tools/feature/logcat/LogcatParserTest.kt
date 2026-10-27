package com.optimizer.tools.feature.logcat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LogcatParserTest {
    private val raw =
        """
        --------- beginning of main
        01-02 03:04:05.678  1042  1042 I ActivityManager: Start proc com.example.player
        01-02 03:04:06.001  1042  1100 W PackageManager: Package no debuggable
        01-02 03:04:07.250  2210  2210 E AndroidRuntime: FATAL EXCEPTION: main
        	at com.example.player.MainActivity.onCreate(MainActivity.kt:42)
        """.trimIndent()

    @Test
    fun `separa hora, pid, tid, nivel, etiqueta y mensaje`() {
        val entries = LogcatParser.parse(raw)

        assertEquals(3, entries.size)
        val first = entries[0]
        assertEquals("01-02 03:04:05.678", first.time)
        assertEquals("1042", first.pid)
        assertEquals("1042", first.tid)
        assertEquals('I', first.level)
        assertEquals("ActivityManager", first.tag)
        assertEquals("Start proc com.example.player", first.message)
    }

    @Test
    fun `anexa las lineas de continuacion al mensaje anterior`() {
        val entries = LogcatParser.parse(raw)

        assertTrue(entries[2].message.contains("FATAL EXCEPTION"))
        assertTrue(entries[2].message.contains("MainActivity.onCreate"))
        assertEquals(3, entries.size)
    }

    @Test
    fun `descarta lo que no tiene formato de threadtime`() {
        val entries = LogcatParser.parse("basura\n01-02 03:04:05.678  1  2 D Ok : vale")

        assertEquals(1, entries.size)
        assertEquals("vale", entries.single().message)
    }

    @Test
    fun `anexa lecturas en vivo sin duplicar la ultima linea`() {
        val existing = LogcatParser.parse("01-02 03:04:05.678  1  2 D A : uno")
        val incoming =
            LogcatParser.parse(
                "01-02 03:04:05.678  1  2 D A : uno\n01-02 03:04:06.000  1  2 D A : dos",
            )

        val merged = LogcatParser.append(existing, incoming, max = 10)

        assertEquals(2, merged.size)
        assertEquals("dos", merged.last().message)
    }

    @Test
    fun `recorta el historial al maximo configurado`() {
        val existing = LogcatParser.parse("01-02 03:04:05.678  1  2 D A : uno")
        val incoming = LogcatParser.parse("01-02 03:04:06.000  1  2 D A : dos")

        val merged = LogcatParser.append(existing, incoming, max = 1)

        assertEquals(listOf("dos"), merged.map { it.message })
    }

    @Test
    fun `filtra por nivel minimo y por texto`() {
        val entries = LogcatParser.parse(raw)

        assertEquals(listOf('W', 'E'), LogcatParser.filter(entries, 'W', "").map { it.level })
        assertEquals(1, LogcatParser.filter(entries, 'V', "runtime").size)
        assertEquals(1, LogcatParser.filter(entries, 'V', "Start proc").size)
        assertTrue(LogcatParser.filter(entries, 'V', "inexistente").isEmpty())
    }

    @Test
    fun `un nivel desconocido no rompe el filtro`() {
        val entries = LogcatParser.parse(raw)

        assertEquals(entries.size, LogcatParser.filter(entries, 'Z', "").size)
    }
}
