package com.optimizer.tools.feature.termux

import com.optimizer.tools.feature.termux.presentation.TermuxOutputHint
import com.optimizer.tools.feature.termux.presentation.hintsFor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TermuxOutputHintsTest {
    @Test
    fun `detects a dpkg failure`() {
        val output = "E: Sub-process /data/data/com.termux/files/usr/bin/dpkg returned an error code (1)"

        assertEquals(listOf(TermuxOutputHint.DPKG), hintsFor(output))
    }

    @Test
    fun `detects missing storage`() {
        val output = "dpkg: error processing archive: No space left on device"

        assertTrue(TermuxOutputHint.NO_SPACE in hintsFor(output))
    }

    @Test
    fun `detects a broken mirror`() {
        val output = "E: Failed to fetch http://termux.dev Packages  Hash Sum mismatch"

        assertEquals(listOf(TermuxOutputHint.MIRROR), hintsFor(output))
    }

    @Test
    fun `detects a held apt lock`() {
        val output = "E: Could not get lock /data/data/com.termux/files/usr/var/lib/dpkg/lock-frontend"

        assertEquals(listOf(TermuxOutputHint.LOCK), hintsFor(output))
    }

    @Test
    fun `detects missing termux storage setup`() {
        val output = "Falta termux-setup-storage: ejecuta la receta Preparar almacenamiento"

        assertEquals(listOf(TermuxOutputHint.STORAGE), hintsFor(output))
    }

    @Test
    fun `successful output has no hints`() {
        val output = "Reading package lists... Done\nInstalling htop...\nDone"

        assertEquals(emptyList<TermuxOutputHint>(), hintsFor(output))
    }
}
