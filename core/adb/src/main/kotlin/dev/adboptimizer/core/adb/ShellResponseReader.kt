package dev.adboptimizer.core.adb

import java.io.ByteArrayOutputStream

/**
 * shell v2 has an explicit EXIT frame. Do not wait for another AdbStream.read after
 * EXIT: libadb 3.1.1 can leave that read waiting when CLSE arrives before queued data
 * has been consumed. Classic unframed output still terminates on transport EOF.
 */
internal fun readShellResponse(readChunk: (ByteArray) -> Int): ByteArray {
    val output = ByteArrayOutputStream()
    val chunk = ByteArray(8 * 1024)
    val completion = ShellExitTracker()
    while (!completion.completed) {
        val size = readChunk(chunk)
        if (size < 0) break
        if (size == 0) continue
        output.write(chunk, 0, size)
        completion.accept(chunk, size)
    }
    return output.toByteArray()
}

private class ShellExitTracker {
    var completed = false
        private set
    private val header = ByteArray(5)
    private var headerSize = 0
    private var remaining = 0
    private var exitFrame = false
    private var unframed = false

    fun accept(
        bytes: ByteArray,
        size: Int,
    ) {
        if (unframed) return
        var index = 0
        while (index < size && !completed) {
            if (remaining > 0) {
                val consumed = minOf(remaining, size - index)
                remaining -= consumed
                index += consumed
                if (remaining == 0 && exitFrame) completed = true
            } else {
                header[headerSize++] = bytes[index++]
                if (headerSize != header.size) continue
                headerSize = 0
                val id = header[0].toInt() and 0xff
                val payloadSize =
                    (header[1].toInt() and 0xff) or
                        ((header[2].toInt() and 0xff) shl 8) or
                        ((header[3].toInt() and 0xff) shl 16) or
                        ((header[4].toInt() and 0xff) shl 24)
                if (id > 4 || payloadSize < 0) {
                    unframed = true
                    return
                }
                remaining = payloadSize
                exitFrame = id == 3 && (payloadSize == 1 || payloadSize == 4)
            }
        }
    }
}
