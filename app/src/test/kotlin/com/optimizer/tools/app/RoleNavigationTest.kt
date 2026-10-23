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
    fun `local role hides optimization module until Shizuku is ready`() {
        assertEquals(
            listOf(Screen.HOME, Screen.APPS, Screen.CONNECT, Screen.SETTINGS),
            mainNavigationScreens(AppTarget.LOCAL, shizukuReady = false),
        )
        assertEquals(
            listOf(Screen.HOME, Screen.APPS, Screen.CONNECT, Screen.OPTIMIZE, Screen.SETTINGS),
            mainNavigationScreens(AppTarget.LOCAL, shizukuReady = true),
        )
    }

    @Test
    fun `remote role exposes remote optimization modules without local Shizuku`() {
        assertEquals(
            listOf(Screen.HOME, Screen.APPS, Screen.CONNECT, Screen.OPTIMIZE, Screen.SETTINGS),
            mainNavigationScreens(AppTarget.REMOTE, shizukuReady = false),
        )
    }
}
