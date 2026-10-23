package dev.adboptimizer.core.adb.scrcpy

import java.io.Closeable
import java.io.DataInputStream

/**
 * Sesión de video abierta contra un servidor scrcpy en el dispositivo.
 * Cerrarla cierra el socket de video y el proceso del servidor.
 */
class ScrcpyVideoStream internal constructor(
    val info: ScrcpyVideoInfo,
    private val input: DataInputStream,
    private val resources: List<Closeable>,
    private val control: java.io.OutputStream? = null,
) : Closeable {
    @Synchronized
    fun sendControl(message: ByteArray) {
        val output = control ?: throw java.io.IOException("El control no está disponible.")
        output.write(message)
        output.flush()
    }

    fun readUnit(): ScrcpyUnit = ScrcpyProtocol.readUnit(input)

    override fun close() {
        resources.forEach { runCatching { it.close() } }
    }
}
