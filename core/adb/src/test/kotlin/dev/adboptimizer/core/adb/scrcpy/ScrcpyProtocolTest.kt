package dev.adboptimizer.core.adb.scrcpy

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.EOFException
import java.io.IOException

class ScrcpyProtocolTest {
    private val keyFlag = 1L shl 61
    private val configFlag = 1L shl 62

    private fun sessionHeader(
        width: Int,
        height: Int,
    ): ByteArray =
        ByteArrayOutputStream()
            .also { bytes ->
                DataOutputStream(bytes).run {
                    writeByte(0x80)
                    writeByte(0)
                    writeByte(0)
                    writeByte(0)
                    writeInt(width)
                    writeInt(height)
                }
            }.toByteArray()

    private fun mediaHeader(
        flags: Long,
        size: Int,
    ): ByteArray =
        ByteArrayOutputStream()
            .also { bytes ->
                DataOutputStream(bytes).run {
                    writeLong(flags)
                    writeInt(size)
                }
            }.toByteArray()

    private fun stream(vararg parts: ByteArray): DataInputStream =
        DataInputStream(ByteArrayInputStream(parts.fold(ByteArray(0)) { acc, part -> acc + part }))

    private fun deviceName(name: String): ByteArray = name.toByteArray(Charsets.UTF_8).copyOf(ScrcpyProtocol.DEVICE_NAME_LENGTH)

    private fun codec(id: Int): ByteArray = ByteArrayOutputStream().also { DataOutputStream(it).writeInt(id) }.toByteArray()

    @Test
    fun `lee nombre, codec h264 y tamano de la cabecera de sesion`() {
        val input =
            stream(
                deviceName("Pixel 7"),
                codec(ScrcpyProtocol.CODEC_H264),
                sessionHeader(1080, 2400),
            )

        val info = ScrcpyProtocol.readVideoInfo(input)

        assertEquals(ScrcpyVideoInfo("Pixel 7", "h264", 1080, 2400), info)
    }

    @Test
    fun `rechaza codecs distintos de h264`() {
        val input = stream(deviceName("TV"), codec(0x00617631))

        assertThrows(IOException::class.java) { ScrcpyProtocol.readVideoInfo(input) }
    }

    @Test
    fun `rechaza cabecera de sesion con tamano cero`() {
        val input =
            stream(
                deviceName("TV"),
                codec(ScrcpyProtocol.CODEC_H264),
                sessionHeader(0, 1920),
            )

        assertThrows(IOException::class.java) { ScrcpyProtocol.readVideoInfo(input) }
    }

    @Test
    fun `lee paquete de frame clave con su pts y payload`() {
        val payload = byteArrayOf(1, 2, 3, 4, 5)
        val input = stream(mediaHeader(keyFlag or 1234L, payload.size), payload)

        val unit = ScrcpyProtocol.readUnit(input) as ScrcpyUnit.Packet

        assertEquals(1234L, unit.ptsUs)
        assertTrue(unit.isKeyFrame)
        assertFalse(unit.isConfig)
        assertArrayEquals(payload, unit.payload)
    }

    @Test
    fun `paquete de configuracion no conserva pts`() {
        val payload = byteArrayOf(9, 9, 9)
        val input = stream(mediaHeader(configFlag or 500L, payload.size), payload)

        val unit = ScrcpyProtocol.readUnit(input) as ScrcpyUnit.Packet

        assertTrue(unit.isConfig)
        assertEquals(0L, unit.ptsUs)
    }

    @Test
    fun `cabecera de sesion posterior se interpreta como cambio de tamano`() {
        val input = stream(sessionHeader(720, 1280))

        val unit = ScrcpyProtocol.readUnit(input)

        assertEquals(ScrcpyUnit.Resize(720, 1280), unit)
    }

    @Test
    fun `longitud de paquete cero es un error`() {
        val input = stream(mediaHeader(keyFlag, 0))

        assertThrows(IOException::class.java) { ScrcpyProtocol.readUnit(input) }
    }

    @Test
    fun `stream cortado lanza EOF en vez de devolver datos parciales`() {
        val input = stream(mediaHeader(keyFlag, 10), byteArrayOf(1, 2))

        assertThrows(EOFException::class.java) { ScrcpyProtocol.readUnit(input) }
    }
}
