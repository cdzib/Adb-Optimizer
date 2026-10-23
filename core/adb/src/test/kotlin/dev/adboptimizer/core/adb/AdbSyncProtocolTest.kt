package dev.adboptimizer.core.adb

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets

class AdbSyncProtocolTest {
    @Test
    fun `encodes sync messages with little endian payload length`() {
        val output = ByteArrayOutputStream()
        AdbSyncProtocol.writeMessage(output, "SEND", "/data/local/tmp/app.apk,0644".toByteArray())

        val bytes = output.toByteArray()
        assertEquals("SEND", String(bytes.copyOfRange(0, 4), StandardCharsets.US_ASCII))
        assertEquals(28, readLittleEndianInt(bytes, 4))
        assertEquals(
            "/data/local/tmp/app.apk,0644",
            String(bytes.copyOfRange(8, bytes.size), StandardCharsets.US_ASCII),
        )
    }

    @Test
    fun `encodes done message with modification timestamp`() {
        val output = ByteArrayOutputStream()
        AdbSyncProtocol.writeDone(output, 0x01020304)

        val bytes = output.toByteArray()
        assertEquals("DONE", String(bytes.copyOfRange(0, 4), StandardCharsets.US_ASCII))
        assertEquals(0x01020304, readLittleEndianInt(bytes, 4))
        assertTrue(bytes.size == 8)
    }

    @Test
    fun `accepts okay sync response`() {
        AdbSyncProtocol.readResponse(ByteArrayInputStream("OKAY".toByteArray(StandardCharsets.US_ASCII)))
    }

    @Test
    fun `reports sync failure response text`() {
        val error =
            runCatching {
                AdbSyncProtocol.readResponse(
                    ByteArrayInputStream(
                        "FAIL".toByteArray(StandardCharsets.US_ASCII) +
                            byteArrayOf(6, 0, 0, 0) +
                            "denied".toByteArray(StandardCharsets.UTF_8),
                    ),
                )
            }.exceptionOrNull()

        assertTrue(error?.message.orEmpty().contains("denied"))
    }

    private fun readLittleEndianInt(
        bytes: ByteArray,
        offset: Int,
    ): Int =
        (bytes[offset].toInt() and 0xff) or
            ((bytes[offset + 1].toInt() and 0xff) shl 8) or
            ((bytes[offset + 2].toInt() and 0xff) shl 16) or
            ((bytes[offset + 3].toInt() and 0xff) shl 24)
}
