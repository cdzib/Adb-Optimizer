package com.optimizer.tools.domain.apk

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ApkInstallPlannerTest {
    @Test
    fun `groups unrelated packages as separate installs`() {
        val groups =
            ApkInstallPlanner.create(
                listOf(
                    ApkDescriptor(File("tv.apk"), "com.example.tv", null),
                    ApkDescriptor(File("player.apk"), "com.example.player", null),
                ),
            )

        assertEquals(listOf("com.example.tv", "com.example.player"), groups.map { it.packageName })
        assertTrue(groups.all { it.files.size == 1 })
        assertTrue(groups.none { it.installMultiple })
    }

    @Test
    fun `groups base and split apks for install multiple`() {
        val groups =
            ApkInstallPlanner.create(
                listOf(
                    ApkDescriptor(File("base.apk"), "com.example.tv", null),
                    ApkDescriptor(File("config.apk"), "com.example.tv", "config.en"),
                ),
            )

        assertEquals(1, groups.size)
        assertEquals(2, groups.single().files.size)
        assertTrue(groups.single().installMultiple)
    }

    @Test
    fun `rejects split apks when selected set has no base apk`() {
        val error =
            runCatching {
                ApkInstallPlanner.create(
                    listOf(ApkDescriptor(File("config.apk"), "com.example.tv", "config.en")),
                )
            }.exceptionOrNull()

        assertTrue(error is IllegalArgumentException)
        assertFalse(error?.message.isNullOrBlank())
    }
}
