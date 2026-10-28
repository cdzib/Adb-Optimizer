package com.optimizer.tools.domain.profile

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProfilePlannerTest {
    private val profile =
        AppProfile(
            id = "p1",
            name = "Limpio",
            createdAt = 1L,
            apps =
                listOf(
                    ProfileApp("com.example.bloat", "Bloat", ProfileAppState.DISABLED),
                    ProfileApp("com.example.gone", "Gone", ProfileAppState.UNINSTALLED),
                    ProfileApp("com.example.keep", "Keep", ProfileAppState.ENABLED),
                    ProfileApp("android", "Android", ProfileAppState.DISABLED),
                    ProfileApp("com.example.other-phone", "Other", ProfileAppState.ENABLED),
                ),
        )

    @Test
    fun `un perfil que ya coincide no propone pasos`() {
        val current =
            mapOf(
                "com.example.bloat" to ProfileAppState.DISABLED,
                "com.example.gone" to ProfileAppState.UNINSTALLED,
                "com.example.keep" to ProfileAppState.ENABLED,
                "android" to ProfileAppState.DISABLED,
            )

        val plan = ProfilePlanner.plan(profile, current, isProtected = { false })

        assertTrue(plan.isEmpty)
        assertEquals(4, plan.same)
        assertEquals(1, plan.missing)
    }

    @Test
    fun `una app desinstalada que debe quedar deshabilitada se reinstala antes`() {
        val plan =
            ProfilePlanner.plan(
                profile,
                mapOf("com.example.bloat" to ProfileAppState.UNINSTALLED),
                isProtected = { false },
            )

        assertEquals(listOf(ProfileStepKind.REINSTALL, ProfileStepKind.DISABLE), plan.steps.map { it.kind })
        assertEquals("com.example.bloat", plan.packages(ProfileStepKind.DISABLE).single())
    }

    @Test
    fun `un paquete critico nunca entra en el plan`() {
        val plan =
            ProfilePlanner.plan(
                profile,
                mapOf("android" to ProfileAppState.ENABLED),
            )

        assertEquals(1, plan.blocked)
        assertFalse(plan.steps.flatMap { it.packages }.contains("android"))
    }

    @Test
    fun `el orden de los pasos es recuperar habilitar deshabilitar desinstalar`() {
        val plan =
            ProfilePlanner.plan(
                profile,
                mapOf(
                    "com.example.bloat" to ProfileAppState.ENABLED,
                    "com.example.gone" to ProfileAppState.ENABLED,
                    "com.example.keep" to ProfileAppState.DISABLED,
                ),
                isProtected = { false },
            )

        assertEquals(
            listOf(ProfileStepKind.ENABLE, ProfileStepKind.DISABLE, ProfileStepKind.UNINSTALL),
            plan.steps.map { it.kind },
        )
        assertEquals(3, plan.appCount)
    }

    @Test
    fun `deshacer vuelve al estado anterior`() {
        val changes =
            listOf(
                ProfileChange("com.example.bloat", ProfileAppState.ENABLED, ProfileAppState.DISABLED),
                ProfileChange("com.example.gone", ProfileAppState.ENABLED, ProfileAppState.UNINSTALLED),
            )

        val undo = ProfilePlanner.undoPlan(changes)

        assertEquals(listOf(ProfileStepKind.REINSTALL, ProfileStepKind.ENABLE), undo.map { it.kind })
    }

    @Test
    fun `el snapshot ordena por nombre visible`() {
        val snapshot =
            ProfilePlanner.snapshot(
                states =
                    mapOf(
                        "com.b" to ProfileAppState.ENABLED,
                        "com.a" to ProfileAppState.DISABLED,
                    ),
                labels = mapOf("com.b" to "Zeta", "com.a" to "Alfa"),
            )

        assertEquals(listOf("Alfa", "Zeta"), snapshot.map { it.displayName })
    }
}
