package dev.adboptimizer.core.adb

import org.junit.Assert.assertArrayEquals
import org.junit.Test

class ShellResponseReaderTest {
    private fun frame(
        id: Int,
        payload: ByteArray,
    ): ByteArray =
        byteArrayOf(
            id.toByte(),
            payload.size.toByte(),
            (payload.size shr 8).toByte(),
            (payload.size shr 16).toByte(),
            (payload.size shr 24).toByte(),
        ) + payload

    @Test fun `returns after exit without waiting for a transport EOF`() {
        val response = frame(1, ByteArray(48_000) { 'x'.code.toByte() }) + frame(3, byteArrayOf(0))
        var offset = 0
        val actual =
            readShellResponse { buffer ->
                check(offset < response.size) { "Would block forever waiting for EOF after EXIT" }
                val size = minOf(buffer.size, response.size - offset)
                response.copyInto(buffer, 0, offset, offset + size)
                offset += size
                size
            }
        assertArrayEquals(response, actual)
    }

    @Test fun `handles fragmented headers and exit payloads without treating stdout as exit`() {
        val response = frame(1, byteArrayOf(3, 1, 0, 0, 0, 0)) + frame(3, byteArrayOf(42, 0, 0, 0))
        var offset = 0
        val actual =
            readShellResponse { buffer ->
                check(offset < response.size) { "Must stop at EXIT even when fragmented" }
                buffer[0] = response[offset++]
                1
            }
        assertArrayEquals(response, actual)
    }

    @Test fun `classic output still reads until EOF`() {
        val response = "classic shell output".toByteArray()
        var sent = false
        assertArrayEquals(
            response,
            readShellResponse { buffer ->
                if (sent) {
                    -1
                } else {
                    sent = true
                    response.copyInto(buffer)
                    response.size
                }
            },
        )
    }
}
