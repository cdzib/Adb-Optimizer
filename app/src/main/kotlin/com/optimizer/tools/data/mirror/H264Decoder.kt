package com.optimizer.tools.data.mirror

import android.media.MediaCodec
import android.media.MediaFormat
import android.view.Surface
import dev.adboptimizer.core.adb.scrcpy.ScrcpyUnit
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.io.Closeable

/**
 * Decodifica H.264 del servidor scrcpy y dibuja cada fotograma directamente en la [Surface].
 * Los paquetes de configuración (SPS/PPS) se envían como BUFFER_FLAG_CODEC_CONFIG.
 */
class H264Decoder(
    surface: Surface,
    width: Int,
    height: Int,
) : Closeable {
    private val codec: MediaCodec = MediaCodec.createDecoderByType(MIME_H264)
    private val bufferInfo = MediaCodec.BufferInfo()

    init {
        val format =
            MediaFormat.createVideoFormat(MIME_H264, width, height).apply {
                setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, MAX_INPUT_BYTES)
                setInteger(MediaFormat.KEY_LOW_LATENCY, 1)
                setInteger(MediaFormat.KEY_PRIORITY, 0)
            }
        codec.configure(format, surface, null, 0)
        codec.start()
    }

    suspend fun feed(packet: ScrcpyUnit.Packet) {
        currentCoroutineContext().ensureActive()
        var index = codec.dequeueInputBuffer(INPUT_TIMEOUT_US)
        while (index < 0) {
            currentCoroutineContext().ensureActive()
            drainOutput()
            index = codec.dequeueInputBuffer(INPUT_TIMEOUT_US)
        }
        val buffer = requireNotNull(codec.getInputBuffer(index)) { "Buffer de entrada no disponible" }
        buffer.clear()
        buffer.put(packet.payload)
        val flags = if (packet.isConfig) MediaCodec.BUFFER_FLAG_CODEC_CONFIG else 0
        codec.queueInputBuffer(index, 0, packet.payload.size, packet.ptsUs, flags)
        drainOutput()
    }

    private fun drainOutput() {
        while (true) {
            val index = codec.dequeueOutputBuffer(bufferInfo, 0)
            if (index == MediaCodec.INFO_TRY_AGAIN_LATER) return
            if (index >= 0) codec.releaseOutputBuffer(index, true)
        }
    }

    override fun close() {
        runCatching { codec.stop() }
        codec.release()
    }

    private companion object {
        const val MIME_H264 = "video/avc"
        const val INPUT_TIMEOUT_US = 10_000L
        const val MAX_INPUT_BYTES = 2 * 1024 * 1024
    }
}
