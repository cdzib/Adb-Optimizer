package com.optimizer.tools.feature.apps

import dev.adboptimizer.core.model.AppFilter
import dev.adboptimizer.core.model.AppSort
import dev.adboptimizer.core.model.ManagedApp
import org.junit.Assert.assertEquals
import org.junit.Test

class AppListFilterTest {
    private val apps =
        listOf(
            ManagedApp("com.zeta.player", label = "Zeta Player", sizeBytes = 10, enabled = true),
            ManagedApp("com.alpha.tv", label = "Alpha TV", sizeBytes = 30, enabled = true),
            ManagedApp("com.system.core", label = "Core", isSystem = true, sizeBytes = 20),
            ManagedApp("com.disabled.app", label = "Old App", enabled = false, sizeBytes = 5),
        )

    @Test
    fun `filters user apps by default and sorts by name`() {
        val result = AppListFilter.apply(apps, query = "", filters = setOf(AppFilter.USER), sort = AppSort.NAME)

        assertEquals(listOf("com.alpha.tv", "com.disabled.app", "com.zeta.player"), result.map { it.packageName })
    }

    @Test
    fun `searches by package name ignoring case`() {
        val result = AppListFilter.apply(apps, query = "ZETA", filters = setOf(AppFilter.USER), sort = AppSort.NAME)

        assertEquals(listOf("com.zeta.player"), result.map { it.packageName })
    }

    @Test
    fun `disabled filter narrows results to disabled apps`() {
        val result =
            AppListFilter.apply(
                apps,
                query = "",
                filters = setOf(AppFilter.USER, AppFilter.DISABLED),
                sort = AppSort.NAME,
            )

        assertEquals(listOf("com.disabled.app"), result.map { it.packageName })
    }

    @Test
    fun `sorts by size descending`() {
        val result =
            AppListFilter.apply(
                apps,
                query = "",
                filters = setOf(AppFilter.USER, AppFilter.SYSTEM),
                sort = AppSort.SIZE,
            )

        assertEquals(listOf(30L, 20L, 10L, 5L), result.map { it.sizeBytes })
    }
}
