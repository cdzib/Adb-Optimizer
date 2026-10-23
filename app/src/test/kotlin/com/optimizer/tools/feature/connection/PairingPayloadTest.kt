package com.optimizer.tools.feature.connection

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PairingPayloadTest {
    @Test
    fun `parsea el esquema adb con codigo en query`() {
        val parsed = parsePairingPayload("adb://192.168.1.40:41021?code=123456")
        assertEquals("192.168.1.40", parsed?.first)
        assertEquals(41021, parsed?.second)
        assertEquals("123456", parsed?.third)
    }

    @Test
    fun `parsea el codigo en el fragmento`() {
        val parsed = parsePairingPayload("adb://192.168.1.40:41021#123456")
        assertEquals("123456", parsed?.third)
    }

    @Test
    fun `parsea ip y puerto sin esquema`() {
        val parsed = parsePairingPayload("192.168.1.40:5555")
        assertEquals("192.168.1.40", parsed?.first)
        assertEquals(5555, parsed?.second)
        assertNull(parsed?.third)
    }

    @Test
    fun `acepta ip sin puerto`() {
        val parsed = parsePairingPayload("10.0.0.7")
        assertEquals("10.0.0.7", parsed?.first)
        assertNull(parsed?.second)
    }

    @Test
    fun `rechaza texto que no es una conexion`() {
        assertNull(parsePairingPayload("https://example.com"))
        assertNull(parsePairingPayload("WIFI:T:ADB;S:foo;P:bar;;"))
        assertNull(parsePairingPayload(""))
    }
}
