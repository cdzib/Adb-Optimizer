package com.optimizer.tools.app

import dev.adboptimizer.core.model.AppTarget
import org.junit.Assert.assertEquals
import org.junit.Test

class RoleNavigationTest {
    @Test
    fun `default role opens the local device`() {
        val state = UiState()

        assertEquals(AppTarget.LOCAL, state.activeTarget)
    }

    @Test
    fun `bottom bar keeps five destinations regardless of Shizuku`() {
        val expected = listOf(Screen.HOME, Screen.APPS, Screen.CONNECT, Screen.OPTIMIZE, Screen.SETTINGS)
        assertEquals(expected, mainNavigationScreens(AppTarget.LOCAL, shizukuReady = false))
        assertEquals(expected, mainNavigationScreens(AppTarget.LOCAL, shizukuReady = true))
    }

    @Test
    fun `remote role exposes remote optimization modules without local Shizuku`() {
        assertEquals(
            listOf(Screen.HOME, Screen.APPS, Screen.CONNECT, Screen.OPTIMIZE, Screen.SETTINGS),
            mainNavigationScreens(AppTarget.REMOTE, shizukuReady = false),
        )
    }

    @Test
    fun `tv rail keeps destination between apps and optimize`() {
        assertEquals(
            listOf("home", "apps", "connect", "optimize", "tools", "settings"),
            tvNavigationKeys,
        )
        assertEquals(1, tvNavigationKeys.count { it == "connect" })
    }
}
