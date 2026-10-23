package dev.adboptimizer.core.adb.scrcpy

import java.nio.ByteBuffer

/** Wire format verified against scrcpy 5.0.1 app/src/control_msg.c. */
object ScrcpyControlProtocol {
    fun pressKey(keycode: Int): ByteArray =
        ByteBuffer
            .allocate(28)
            .apply {
                require(keycode >= 0)
                repeat(2) { action ->
                    put(0)
                        .put(action.toByte())
                        .putInt(keycode)
                        .putInt(0)
                        .putInt(0)
                }
            }.array()

    fun touch(
        action: Int,
        x: Float,
        y: Float,
        width: Int,
        height: Int,
    ): ByteArray {
        require(action in listOf(0, 1, 2, 3))
        require(width in 1..65535 && height in 1..65535)
        require(x.isFinite() && y.isFinite())
        return ByteBuffer
            .allocate(32)
            .apply {
                put(2).put(action.toByte()).putLong(-2L)
                putInt((x.coerceIn(0f, 1f) * width).toInt().coerceAtMost(width - 1))
                putInt((y.coerceIn(0f, 1f) * height).toInt().coerceAtMost(height - 1))
                putShort(width.toShort()).putShort(height.toShort())
                putShort(if (action == 1 || action == 3) 0 else 65535.toShort())
                putInt(0).putInt(0)
            }.array()
    }
}
