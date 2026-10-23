package com.optimizer.tools.data.remote

import dev.adboptimizer.core.model.RemoteLauncher
import org.junit.Assert.assertEquals
import org.junit.Test

class RemoteLauncherParserTest {
    @Test
    fun `parses and de duplicates launcher components`() {
        val launchers =
            RemoteLauncherParser.parse(
                """
                priority=0 preferredOrder=0 match=0x108000 isDefault=true
                com.vendor.home/com.vendor.home.MainActivity
                com.light.home/.LauncherActivity
                com.vendor.home/com.vendor.home.AlternateActivity
                """.trimIndent(),
            )

        assertEquals(
            listOf(
                RemoteLauncher("com.vendor.home", "com.vendor.home/com.vendor.home.MainActivity"),
                RemoteLauncher("com.light.home", "com.light.home/com.light.home.LauncherActivity"),
            ),
            launchers,
        )
    }

    @Test
    fun `returns no launchers for package manager empty response`() {
        assertEquals(emptyList<RemoteLauncher>(), RemoteLauncherParser.parse("No activities found"))
    }
}
