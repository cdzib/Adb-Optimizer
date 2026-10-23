package dev.adboptimizer.core.adb

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.ByteArrayOutputStream

class ShellV2ParserTest {
    @Test
    fun `reads Android single byte exit status`() {
        val parsed = ShellV2Parser.parse(packet(1, "error".toByteArray()) + packet(3, byteArrayOf(42)))
        assertEquals("error", parsed?.stdout)
        assertEquals(42, parsed?.exitCode)
    }

    private fun packet(
        id: Int,
        payload: ByteArray,
    ): ByteArray {
        val out = ByteArrayOutputStream()
        out.write(id)
        // Cabecera real de shell v2: 4 bytes de longitud little-endian.
        out.write(payload.size and 0xFF)
        out.write((payload.size shr 8) and 0xFF)
        out.write((payload.size shr 16) and 0xFF)
        out.write((payload.size shr 24) and 0xFF)
        out.write(payload)
        return out.toByteArray()
    }

    @Test
    fun `usa la cabecera de 5 bytes de AOSP`() {
        // 01 04 00 00 00 "hola": id=stdout, longitud=4 little-endian.
        val bytes = byteArrayOf(0x01, 0x04, 0x00, 0x00, 0x00, 0x68, 0x6F, 0x6C, 0x61)
        val parsed = ShellV2Parser.parse(bytes)
        assertEquals("hola", parsed?.stdout)
        assertEquals(0, parsed?.exitCode)
    }

    @Test
    fun `separa stdout, stderr y codigo de salida`() {
        val bytes =
            packet(1, "hola".toByteArray()) +
                packet(2, "aviso".toByteArray()) +
                packet(3, byteArrayOf(42, 0, 0, 0))

        val parsed = ShellV2Parser.parse(bytes)
        assertEquals("hola", parsed?.stdout)
        assertEquals("aviso", parsed?.stderr)
        assertEquals(42, parsed?.exitCode)
    }

    @Test
    fun `soporta payloads mayores a 127 bytes`() {
        val payload = ByteArray(300) { 'a'.code.toByte() }
        val parsed = ShellV2Parser.parse(packet(1, payload) + packet(3, byteArrayOf(0, 0, 0, 0)))
        assertEquals(300, parsed?.stdout?.length)
        assertEquals(0, parsed?.exitCode)
    }

    @Test
    fun `devuelve null cuando la salida no tiene framing (shell clasico)`() {
        assertNull(ShellV2Parser.parse("Applications Memory Usage".toByteArray()))
        assertNull(ShellV2Parser.parse(packet(1, "ok".toByteArray()).copyOfRange(0, 3)))
    }

    @Test
    fun `salida vacia con solo paquete de exit`() {
        val parsed = ShellV2Parser.parse(packet(3, byteArrayOf(1, 0, 0, 0)))
        assertEquals("", parsed?.stdout)
        assertEquals(1, parsed?.exitCode)
    }
}
