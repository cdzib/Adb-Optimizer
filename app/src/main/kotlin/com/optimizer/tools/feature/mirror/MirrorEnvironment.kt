package com.optimizer.tools.feature.mirror

import android.view.Surface
import dev.adboptimizer.core.adb.scrcpy.ScrcpyUnit
import dev.adboptimizer.core.adb.scrcpy.ScrcpyVideoStream

/** Permisos de entrada remotos relevantes para el espejo. */
data class MirrorInputCapabilities(
    val inputPermissionGranted: Boolean?,
)

/** Decodificador de video que dibuja en una superficie. */
interface MirrorDecoder {
    suspend fun feed(packet: ScrcpyUnit.Packet)

    fun close()
}

/** Puertos de la feature Espejo. */
interface MirrorEnvironment {
    fun hasDevice(): Boolean

    fun isTv(): Boolean

    suspend fun readInputCapabilities(): MirrorInputCapabilities

    suspend fun openVideo(onLog: (String) -> Unit): ScrcpyVideoStream

    fun createDecoder(
        surface: Surface,
        width: Int,
        height: Int,
    ): MirrorDecoder
}
