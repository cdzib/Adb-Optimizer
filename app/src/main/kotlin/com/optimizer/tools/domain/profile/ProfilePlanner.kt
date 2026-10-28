package com.optimizer.tools.domain.profile

import dev.adboptimizer.core.model.AppSafety

/**
 * Reglas puras de los perfiles de estado de apps.
 *
 * No conoce Android ni ADB: recibe el estado que el dispositivo reporta y devuelve los
 * pasos ordenados que lo acercan al perfil. La capa de datos es la que ejecuta y vuelve a
 * leer cada cambio.
 */
object ProfilePlanner {
    /** El orden importa: recuperar, habilitar, deshabilitar y desinstalar. */
    val ORDER: List<ProfileStepKind> =
        listOf(
            ProfileStepKind.REINSTALL,
            ProfileStepKind.ENABLE,
            ProfileStepKind.DISABLE,
            ProfileStepKind.UNINSTALL,
        )

    /**
     * Compara el perfil con el estado actual del dispositivo.
     *
     * - `same`: apps que ya cumplen el perfil.
     * - `missing`: apps del perfil que el dispositivo no conoce (otro telefono, otro usuario).
     * - `blocked`: paquetes criticos que nunca se tocan, aunque el perfil los incluya.
     */
    fun plan(
        profile: AppProfile,
        current: Map<String, ProfileAppState>,
        isProtected: (String) -> Boolean = AppSafety::isCritical,
    ): ProfilePlan {
        val byKind = ORDER.associateWith { mutableListOf<String>() }
        var same = 0
        var missing = 0
        var blocked = 0
        profile.apps.forEach { entry ->
            val state = current[entry.packageName]
            when {
                isProtected(entry.packageName) -> {
                    blocked++
                }

                state == null -> {
                    missing++
                }

                state == entry.state -> {
                    same++
                }

                else -> {
                    kindsFor(entry.state, state).forEach { kind -> byKind.getValue(kind).add(entry.packageName) }
                }
            }
        }
        val steps =
            ORDER.mapNotNull { kind ->
                byKind.getValue(kind).takeIf { it.isNotEmpty() }?.let { ProfileStep(kind, it.toList()) }
            }
        return ProfilePlan(steps = steps, same = same, missing = missing, blocked = blocked)
    }

    /**
     * Los pasos que llevan una app desde [current] hasta [target].
     * Una app desinstalada que debe quedar deshabilitada se reinstala primero.
     */
    fun kindsFor(
        target: ProfileAppState,
        current: ProfileAppState,
    ): List<ProfileStepKind> =
        when (target) {
            ProfileAppState.UNINSTALLED -> {
                listOf(ProfileStepKind.UNINSTALL)
            }

            ProfileAppState.ENABLED -> {
                when (current) {
                    ProfileAppState.UNINSTALLED -> listOf(ProfileStepKind.REINSTALL)
                    ProfileAppState.DISABLED -> listOf(ProfileStepKind.ENABLE)
                    ProfileAppState.ENABLED -> emptyList()
                }
            }

            ProfileAppState.DISABLED -> {
                when (current) {
                    ProfileAppState.UNINSTALLED -> listOf(ProfileStepKind.REINSTALL, ProfileStepKind.DISABLE)
                    ProfileAppState.ENABLED -> listOf(ProfileStepKind.DISABLE)
                    ProfileAppState.DISABLED -> emptyList()
                }
            }
        }

    /** Reconstruye los pasos para volver al estado anterior de una ejecucion. */
    fun undoPlan(
        changes: List<ProfileChange>,
        isProtected: (String) -> Boolean = AppSafety::isCritical,
    ): List<ProfileStep> {
        val byKind = ORDER.associateWith { mutableListOf<String>() }
        changes.forEach { change ->
            if (isProtected(change.packageName)) return@forEach
            kindsFor(change.from, change.to).forEach { kind -> byKind.getValue(kind).add(change.packageName) }
        }
        return ORDER.mapNotNull { kind ->
            byKind.getValue(kind).takeIf { it.isNotEmpty() }?.let { ProfileStep(kind, it.toList()) }
        }
    }

    /** Snapshot ordenado por nombre visible, con el nombre del paquete cuando se desconoce. */
    fun snapshot(
        states: Map<String, ProfileAppState>,
        labels: Map<String, String>,
    ): List<ProfileApp> =
        states
            .map { (packageName, state) ->
                ProfileApp(
                    packageName = packageName,
                    label = labels[packageName]?.takeIf { it.isNotBlank() } ?: packageName,
                    state = state,
                )
            }.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.displayName })

    /** Apps del perfil que ya no coinciden, para el aviso de drift. */
    fun driftedPackages(
        profile: AppProfile,
        current: Map<String, ProfileAppState>,
    ): List<String> = plan(profile, current).steps.flatMap { it.packages }
}
