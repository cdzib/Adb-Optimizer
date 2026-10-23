package dev.adboptimizer.core.adb

import java.io.ByteArrayOutputStream

/**
 * Formato de respuesta de `shell,v2,raw:<cmd>` según `shell_protocol.h` de AOSP:
 * cabecera de 5 bytes = 1 byte de id + 4 bytes de longitud little-endian,
 * seguida del payload.
 *
 * ids: 0 = stdin, 1 = stdout, 2 = stderr, 3 = exit, 4 = close-stdin.
 */
object ShellV2Parser {
    private const val HEADER_SIZE = 5
    private const val EXIT_ID = 3

    data class Parsed(
        val stdout: String,
        val stderr: String,
        val exitCode: Int,
    )

    fun parse(bytes: ByteArray): Parsed? {
        if (bytes.isEmpty()) return Parsed("", "", 0)

        val firstId = bytes[0].toInt() and 0xFF
        if (firstId > 4) return null

        val stdout = ByteArrayOutputStream()
        val stderr = ByteArrayOutputStream()
        var exitCode = 0
        var index = 0

        while (index < bytes.size) {
            if (index + HEADER_SIZE > bytes.size) return null

            val id = bytes[index].toInt() and 0xFF
            if (id > 4) return null

            val size =
                (bytes[index + 1].toInt() and 0xFF) or
                    ((bytes[index + 2].toInt() and 0xFF) shl 8) or
                    ((bytes[index + 3].toInt() and 0xFF) shl 16) or
                    ((bytes[index + 4].toInt() and 0xFF) shl 24)
            index += HEADER_SIZE

            // Un tamaño negativo (bit alto) o que exceda el buffer indica framing corrupto.
            if (size < 0 || index + size > bytes.size) return null

            val payload = bytes.copyOfRange(index, index + size)
            index += size

            when (id) {
                1 -> {
                    stdout.write(payload)
                }

                2 -> {
                    stderr.write(payload)
                }

                EXIT_ID -> {
                    exitCode =
                        if (payload.size >= 4) {
                            (payload[0].toInt() and 0xFF) or
                                ((payload[1].toInt() and 0xFF) shl 8) or
                                ((payload[2].toInt() and 0xFF) shl 16) or
                                ((payload[3].toInt() and 0xFF) shl 24)
                        } else {
                            payload.firstOrNull()?.toInt()?.and(0xFF) ?: 0
                        }
                }

                else -> {
                    Unit
                }
            }
        }

        return Parsed(
            stdout = stdout.toString("UTF-8"),
            stderr = stderr.toString("UTF-8"),
            exitCode = exitCode,
        )
    }
}
