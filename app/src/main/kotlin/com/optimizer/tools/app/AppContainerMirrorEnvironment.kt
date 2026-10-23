package com.optimizer.tools.app

import android.view.Surface
import com.optimizer.tools.data.AppContainer
import com.optimizer.tools.data.mirror.H264Decoder
import com.optimizer.tools.data.remote.RemoteInputCapabilitiesRepository
import com.optimizer.tools.feature.mirror.MirrorDecoder
import com.optimizer.tools.feature.mirror.MirrorEnvironment
import com.optimizer.tools.feature.mirror.MirrorInputCapabilities
import dev.adboptimizer.core.adb.scrcpy.ScrcpyUnit
import dev.adboptimizer.core.adb.scrcpy.ScrcpyVideoStream

/** Adaptador de composición para la feature Espejo. */
internal class AppContainerMirrorEnvironment(
    private val container: AppContainer,
) : MirrorEnvironment {
    override fun hasDevice(): Boolean = container.shell != null

    override fun isTv(): Boolean = container.device?.isTv == true

    override suspend fun readInputCapabilities(): MirrorInputCapabilities {
        val shell = container.shell ?: error("No hay dispositivo conectado.")
        val capabilities = RemoteInputCapabilitiesRepository(shell).readCapabilities()
        return MirrorInputCapabilities(capabilities.inputPermissionGranted)
    }

    override suspend fun openVideo(onLog: (String) -> Unit): ScrcpyVideoStream = container.openScrcpyVideo(onLog)

    override fun createDecoder(
        surface: Surface,
        width: Int,
        height: Int,
    ): MirrorDecoder {
        val decoder = H264Decoder(surface, width, height)
        return object : MirrorDecoder {
            override suspend fun feed(packet: ScrcpyUnit.Packet) = decoder.feed(packet)

            override fun close() = decoder.close()
        }
    }
}
