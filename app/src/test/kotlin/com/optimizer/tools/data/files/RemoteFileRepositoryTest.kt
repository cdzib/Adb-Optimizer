package com.optimizer.tools.data.files

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RemoteFileRepositoryTest {
    private val listing =
        """
        total 24
        drwxrws--- 2 root sdcard_rw 4096 2026-05-01 12:00 Download
        -rw-rw---- 1 u0_a123 sdcard_rw 5242880 2024-01-02 08:30 backup.apk
        lrwxrwxrwx 1 root root 21 2024-01-03 09:00 sdcard -> /storage/self/primary
        -rw-rw---- 1 u0_a123 sdcard_rw 812 May  1 12:00 notes.txt
        """.trimIndent()

    @Test
    fun `convierte ls en entradas y ordena carpetas primero`() {
        val entries = RemoteFileRepository.parseListing("/sdcard", listing)

        assertEquals(listOf("Download", "backup.apk", "notes.txt", "sdcard"), entries.map { it.name })
        assertTrue(entries.first().isDirectory)
        assertFalse(entries[1].isDirectory)
        assertEquals(5_242_880, entries[1].sizeBytes)
        assertEquals("/sdcard/backup.apk", entries[1].path)
        assertTrue(entries.last().isLink)
        assertEquals("/sdcard/sdcard", entries.last().path)
    }

    @Test
    fun `normaliza rutas y calcula padres y segmentos`() {
        assertEquals("/sdcard/Download", RemoteFileRepository.normalize("/sdcard//Download/./"))
        assertEquals("/sdcard", RemoteFileRepository.normalize("/sdcard/Download/.."))
        assertEquals("/", RemoteFileRepository.normalize(".."))
        assertEquals("/sdcard", RemoteFileRepository.parent("/sdcard/Download"))
        assertEquals("/", RemoteFileRepository.parent("/sdcard"))
        assertEquals(listOf("sdcard" to "/sdcard", "Download" to "/sdcard/Download"), RemoteFileRepository.segments("/sdcard/Download"))
    }

    @Test
    fun `no permite borrar rutas protegidas`() {
        assertTrue(RemoteFileRepository.isProtected("/"))
        assertTrue(RemoteFileRepository.isProtected("/sdcard/"))
        assertFalse(RemoteFileRepository.isProtected("/sdcard/Download"))
    }
}
