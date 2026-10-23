package com.optimizer.tools.feature.connection

import org.junit.Assert.assertEquals
import org.junit.Test

class PairingCodeInputTest {
    @Test fun `pasted code keeps only six ASCII digits`() {
        assertEquals("012345", normalizePairingCode(" 01-23 456x"))
        assertEquals("123", normalizePairingCode("a1٢2３3"))
    }

    @Test fun `deleting digits and clearing code are allowed`() {
        assertEquals("12345", normalizePairingCode("12345"))
        assertEquals("", normalizePairingCode(""))
    }
}
