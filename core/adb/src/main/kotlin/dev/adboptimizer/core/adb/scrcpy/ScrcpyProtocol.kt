package dev.adboptimizer.core.adb.scrcpy

import java.io.DataInputStream
import java.io.IOException

/** Datos iniciales del stream de video de scrcpy (protocolo 5.0.1, tunnel_forward). */
data class ScrcpyVideoInfo(
    val deviceName: String,
    val codec: String,
    val width: Int,
    val height: Int,
)

sealed interface ScrcpyUnit {
    data class Packet(
        val ptsUs: Long,
        val isConfig: Boolean,
        val isKeyFrame: Boolean,
        val payload: ByteArray,
    ) : ScrcpyUnit

    data class Resize(
        val width: Int,
        val height: Int,
    ) : ScrcpyUnit
}

/**
 * Lectura del stream de video de scrcpy.
 * Referencia: app/src/demuxer.c y app/src/server.c del cliente oficial de scrcpy 5.0.1.
 */
object ScrcpyProtocol {
    const val DEVICE_NAME_LENGTH = 64
    const val PACKET_HEADER_LENGTH = 12
    const val CODEC_H264 = 0x68323634
    private const val CODEC_DISABLED = 0
    private const val CODEC_CONFIG_ERROR = 1

    private const val FLAG_CONFIG = 1L shl 62
    private const val FLAG_KEY_FRAME = 1L shl 61
    private const val PTS_MASK = FLAG_KEY_FRAME - 1

    /**
     * Lee el nombre del dispositivo, el codec y la cabecera de sesión.
     * El byte dummy ya lo consume la conexión al abrir el socket, antes de llegar aquí.
     */
    fun readVideoInfo(input: DataInputStream): ScrcpyVideoInfo {
        val nameBytes = ByteArray(DEVICE_NAME_LENGTH).also(input::readFully)
        val nameEnd = nameBytes.indexOf(0).let { if (it < 0) nameBytes.size else it }
        val deviceName = String(nameBytes, 0, nameEnd, Charsets.UTF_8)

        val codecId = input.readInt()
        when (codecId) {
            CODEC_DISABLED -> throw IOException(
                "El servidor desactivó el video: este dispositivo no tiene encoder disponible.",
            )

            CODEC_CONFIG_ERROR -> throw IOException(
                "El servidor reportó un error de configuración de video.",
            )
        }
        if (codecId != CODEC_H264) {
            throw IOException("Codec de video no soportado por este cliente: 0x%08x".format(codecId))
        }

        val header = ByteArray(PACKET_HEADER_LENGTH).also(input::readFully)
        if (!isSessionHeader(header)) {
            throw IOException("Se esperaba una cabecera de sesión de video.")
        }
        val (width, height) = sessionSize(header)
        if (width <= 0 || height <= 0) {
            throw IOException("Tamaño de video inválido: ${width}x$height")
        }
        return ScrcpyVideoInfo(deviceName, "h264", width, height)
    }

    /** Lee la siguiente unidad del stream: un paquete de video o un cambio de tamaño. */
    fun readUnit(input: DataInputStream): ScrcpyUnit {
        val header = ByteArray(PACKET_HEADER_LENGTH).also(input::readFully)
        if (isSessionHeader(header)) {
            val (width, height) = sessionSize(header)
            return ScrcpyUnit.Resize(width, height)
        }

        val flags = readLongBigEndian(header, 0)
        val size = readIntBigEndian(header, 8)
        if (size <= 0) throw IOException("Longitud de paquete inválida: $size")

        val payload = ByteArray(size).also(input::readFully)
        val isConfig = flags and FLAG_CONFIG != 0L
        val isKeyFrame = flags and FLAG_KEY_FRAME != 0L
        return ScrcpyUnit.Packet(
            ptsUs = if (isConfig) 0L else flags and PTS_MASK,
            isConfig = isConfig,
            isKeyFrame = isKeyFrame,
            payload = payload,
        )
    }

    private fun isSessionHeader(header: ByteArray): Boolean = header[0].toInt() and 0x80 != 0

    private fun sessionSize(header: ByteArray): Pair<Int, Int> = readIntBigEndian(header, 4) to readIntBigEndian(header, 8)

    private fun readIntBigEndian(
        bytes: ByteArray,
        offset: Int,
    ): Int =
        ((bytes[offset].toInt() and 0xFF) shl 24) or
            ((bytes[offset + 1].toInt() and 0xFF) shl 16) or
            ((bytes[offset + 2].toInt() and 0xFF) shl 8) or
            (bytes[offset + 3].toInt() and 0xFF)

    private fun readLongBigEndian(
        bytes: ByteArray,
        offset: Int,
    ): Long =
        (readIntBigEndian(bytes, offset).toLong() shl 32) or
            (readIntBigEndian(bytes, offset + 4).toLong() and 0xFFFF_FFFFL)
}
