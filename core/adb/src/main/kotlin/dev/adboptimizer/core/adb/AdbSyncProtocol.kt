package dev.adboptimizer.core.adb

import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.nio.charset.StandardCharsets

internal object AdbSyncProtocol {
    private const val MAX_RESPONSE_SIZE = 1024 * 1024

    fun writeMessage(
        output: OutputStream,
        id: String,
        payload: ByteArray,
    ) {
        require(id.length == 4) { "ADB sync message id must be four characters." }
        output.write(id.toByteArray(StandardCharsets.US_ASCII))
        writeLittleEndianInt(output, payload.size)
        output.write(payload)
    }

    fun writeData(
        output: OutputStream,
        payload: ByteArray,
        length: Int,
    ) {
        output.write("DATA".toByteArray(StandardCharsets.US_ASCII))
        writeLittleEndianInt(output, length)
        output.write(payload, 0, length)
    }

    fun writeDone(
        output: OutputStream,
        modifiedAtSeconds: Int,
    ) {
        output.write("DONE".toByteArray(StandardCharsets.US_ASCII))
        writeLittleEndianInt(output, modifiedAtSeconds)
    }

    fun readResponse(input: InputStream) {
        val id = String(readFully(input, 4), StandardCharsets.US_ASCII)
        when (id) {
            "OKAY" -> {
                Unit
            }

            "FAIL" -> {
                val length = readLittleEndianInt(input)
                if (length !in 0..MAX_RESPONSE_SIZE) throw IOException("Invalid ADB sync failure response length: $length")
                val message = String(readFully(input, length), StandardCharsets.UTF_8)
                throw IOException("ADB file transfer failed: $message")
            }

            else -> {
                throw IOException("Unexpected ADB sync response: $id")
            }
        }
    }

    private fun writeLittleEndianInt(
        output: OutputStream,
        value: Int,
    ) {
        output.write(value and 0xff)
        output.write((value ushr 8) and 0xff)
        output.write((value ushr 16) and 0xff)
        output.write((value ushr 24) and 0xff)
    }

    private fun readLittleEndianInt(input: InputStream): Int {
        val bytes = readFully(input, 4)
        return (bytes[0].toInt() and 0xff) or
            ((bytes[1].toInt() and 0xff) shl 8) or
            ((bytes[2].toInt() and 0xff) shl 16) or
            ((bytes[3].toInt() and 0xff) shl 24)
    }

    private fun readFully(
        input: InputStream,
        length: Int,
    ): ByteArray {
        val result = ByteArray(length)
        var offset = 0
        while (offset < length) {
            val count = input.read(result, offset, length - offset)
            if (count < 0) throw IOException("Unexpected end of ADB sync response.")
            offset += count
        }
        return result
    }
}
