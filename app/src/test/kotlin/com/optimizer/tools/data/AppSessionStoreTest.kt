package com.optimizer.tools.data

import dev.adboptimizer.core.model.DeviceInfo
import dev.adboptimizer.core.shell.AdbShell
import dev.adboptimizer.core.shell.ShellResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.Closeable

class AppSessionStoreTest {
    @Test fun `stopping sessions closes every transport and clears active selection`() {
        val store = AppSessionStore()
        val first = ClosingShell()
        val second = ClosingShell()
        store.add(session("192.0.2.10:41000", first))
        store.add(session("192.0.2.11:42000", second))

        store.clear()

        assertTrue(first.closed)
        assertTrue(second.closed)
        assertTrue(
            store.state.value.devices
                .isEmpty(),
        )
        assertNull(store.state.value.activeId)
        assertNull(store.activeShell())
        store.clear()
        assertEquals(0, store.size())
    }

    private fun session(
        address: String,
        shell: ClosingShell,
    ) = DeviceSession(
        address,
        DeviceInfo(address, "Phone", "Android", 36, "16", false, false, "arm64-v8a"),
        shell,
    )

    private class ClosingShell :
        AdbShell,
        Closeable {
        var closed = false

        override fun close() {
            closed = true
        }

        override suspend fun exec(
            command: String,
            timeoutMs: Long,
        ): ShellResult = error("Not used")
    }
}
