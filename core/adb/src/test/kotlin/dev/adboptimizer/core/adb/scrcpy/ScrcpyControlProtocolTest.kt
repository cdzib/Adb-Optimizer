package dev.adboptimizer.core.adb.scrcpy

import org.junit.Assert.assertEquals
import org.junit.Test
import java.nio.ByteBuffer

class ScrcpyControlProtocolTest {
    @Test fun `key press contains down and up in order`() {
        val bytes = ByteBuffer.wrap(ScrcpyControlProtocol.pressKey(3))
        assertEquals(28, bytes.remaining())
        repeat(2) { action ->
            assertEquals(0, bytes.get().toInt())
            assertEquals(action, bytes.get().toInt())
            assertEquals(3, bytes.int)
            assertEquals(0, bytes.int)
            assertEquals(0, bytes.int)
        }
    }

    @Test fun `touch maps normalized coordinates into video bounds`() {
        val bytes = ByteBuffer.wrap(ScrcpyControlProtocol.touch(0, 1f, 0.5f, 1280, 720))
        assertEquals(32, bytes.remaining())
        assertEquals(2, bytes.get().toInt())
        assertEquals(0, bytes.get().toInt())
        assertEquals(-2L, bytes.long)
        assertEquals(1279, bytes.int)
        assertEquals(360, bytes.int)
        assertEquals(1280, bytes.short.toInt())
        assertEquals(720, bytes.short.toInt())
        assertEquals(65535, bytes.short.toInt() and 65535)
    }
}
