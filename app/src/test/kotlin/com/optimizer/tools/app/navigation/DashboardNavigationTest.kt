package com.optimizer.tools.app.navigation

import com.optimizer.tools.app.Screen
import com.optimizer.tools.feature.dashboard.DashboardCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class DashboardNavigationTest {
    @Test
    fun `each summary category opens a distinct detail route`() {
        val routes = DashboardCategory.entries.map { DashboardDetailRoute(it.name) }
        assertEquals(
            listOf(
                DashboardDetailRoute("DEVICE"),
                DashboardDetailRoute("DISPLAY"),
                DashboardDetailRoute("SENSORS"),
                DashboardDetailRoute("NETWORK"),
                DashboardDetailRoute("SYSTEM"),
                DashboardDetailRoute("CPU"),
                DashboardDetailRoute("BATTERY"),
                DashboardDetailRoute("MEMORY"),
            ),
            routes,
        )
        assertEquals(8, routes.distinct().size)
    }

    @Test
    fun `detail is not mapped to a main screen that would redirect navigation`() {
        DashboardCategory.entries.forEach { category ->
            val route = DashboardDetailRoute(category.name)
            assertNull(route.toScreen())
            assertFalse(route in mainRoutes)
        }
    }

    @Test
    fun `return destination is the existing home screen`() {
        assertEquals(Screen.HOME, HomeRoute.toScreen())
    }

    @Test
    fun `screen and route mappings stay symmetric`() {
        Screen.entries.forEach { screen ->
            assertEquals(screen, screen.toRoute().toScreen())
        }
    }
}
