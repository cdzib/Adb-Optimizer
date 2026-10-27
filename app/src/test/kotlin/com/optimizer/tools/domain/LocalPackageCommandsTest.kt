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

    @Test
    fun `optimiza con comandos validados y lectura de vuelta`() {
        val compile = LocalPackageCommands.create("com.example.player", AppAction.COMPILE_AOT, 35)!!
        assertEquals("pm compile -m speed -f com.example.player", compile.command)
        assertEquals("Success", compile.successToken)
        assertEquals("dumpsys package com.example.player | grep -m1 -E 'status=|compiler filter'", compile.readBack?.command)

        val standby = LocalPackageCommands.create("com.example.player", AppAction.RESTRICT_STANDBY, 35)!!
        assertEquals("am set-standby-bucket com.example.player restricted", standby.command)
        assertEquals("am get-standby-bucket com.example.player", standby.readBack?.command)
        assertTrue(standby.readBack?.matches("45") == true)
        assertTrue(standby.readBack?.matches("restricted") == true)
        assertFalse(standby.readBack?.matches("40") == true)

        val background = LocalPackageCommands.create("com.example.player", AppAction.RESTRICT_BACKGROUND, 35)!!
        assertEquals("appops set com.example.player RUN_IN_BACKGROUND ignore", background.command)
        assertEquals("appops get com.example.player RUN_IN_BACKGROUND", background.readBack?.command)
    }

    @Test
    fun `no optimiza paquetes criticos del sistema`() {
        assertNull(LocalPackageCommands.create("com.android.systemui", AppAction.COMPILE_AOT, 35))
        assertNull(LocalPackageCommands.create("com.android.settings", AppAction.RESTRICT_STANDBY, 35))
        assertNull(LocalPackageCommands.create("com.android.shell", AppAction.RESTRICT_BACKGROUND, 35))
    }
}
