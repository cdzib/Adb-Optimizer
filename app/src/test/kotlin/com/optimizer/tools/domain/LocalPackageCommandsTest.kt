package com.optimizer.tools.domain

import com.optimizer.tools.domain.apk.LocalPackageCommands
import dev.adboptimizer.core.model.AppAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalPackageCommandsTest {
    @Test
    fun `creates shell commands only for validated package names`() {
        val command = LocalPackageCommands.create("com.example.player", AppAction.DISABLE, 35)

        assertEquals("pm disable-user --user 0 'com.example.player'", command?.command)
        assertFalse(LocalPackageCommands.isValidPackageName("com.example;reboot"))
        assertNull(
            runCatching {
                LocalPackageCommands.create("com.example;reboot", AppAction.DISABLE, 35)
            }.getOrNull(),
        )
    }

    @Test
    fun `cache clearing requires android 11`() {
        assertNull(LocalPackageCommands.create("com.example.player", AppAction.CLEAR_CACHE, 29))
        assertEquals(
            "pm clear --cache-only 'com.example.player'",
            LocalPackageCommands.create("com.example.player", AppAction.CLEAR_CACHE, 30)?.command,
        )
    }

    @Test
    fun `maps destructive and non-destructive actions to shell`() {
        assertTrue(LocalPackageCommands.create("com.example.player", AppAction.CLEAR_DATA, 35)!!.command.startsWith("pm clear "))
        assertTrue(LocalPackageCommands.create("com.example.player", AppAction.UNINSTALL, 35)!!.command.contains("pm uninstall --user 0"))
        assertTrue(LocalPackageCommands.create("com.example.player", AppAction.FORCE_STOP, 35)!!.command.startsWith("am force-stop"))
    }
}
