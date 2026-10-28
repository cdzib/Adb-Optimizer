package com.optimizer.tools.domain.profile

import kotlinx.serialization.Serializable

/**
 * Estado de una app dentro de un perfil.
 *
 * Un perfil guarda lo que el usuario quiere que sea cada app, no lo que es ahora:
 * por eso la comparacion con el dispositivo (el "drift") es el corazon de la funcion.
 */
@Serializable
enum class ProfileAppState {
    ENABLED,
    DISABLED,
    UNINSTALLED,
}

/** Una app del perfil, con el nombre visible que tenia cuando se guardo. */
@Serializable
data class ProfileApp(
    val packageName: String,
    val label: String = packageName,
    val state: ProfileAppState,
) {
    val displayName: String get() = label.ifBlank { packageName }
}

/** Snapshot con nombre del estado de las apps de un dispositivo. */
@Serializable
data class AppProfile(
    val id: String,
    val name: String,
    val createdAt: Long,
    val apps: List<ProfileApp> = emptyList(),
    val watched: Boolean = false,
) {
    val appCount: Int get() = apps.size

    fun stateOf(packageName: String): ProfileAppState? = apps.firstOrNull { it.packageName == packageName }?.state
}

/**
 * Pasos que aplica un perfil. Se ejecutan en este orden: primero se recupera lo que falta,
 * despues se habilita, luego se deshabilita y al final se desinstala. Asi una app que debe
 * quedar deshabilitada y esta desinstalada se reinstala antes de deshabilitarse.
 */
enum class ProfileStepKind {
    REINSTALL,
    ENABLE,
    DISABLE,
    UNINSTALL,
}

data class ProfileStep(
    val kind: ProfileStepKind,
    val packages: List<String>,
)

/** Resultado de comparar un perfil con el dispositivo. */
data class ProfilePlan(
    val steps: List<ProfileStep> = emptyList(),
    val same: Int = 0,
    val missing: Int = 0,
    val blocked: Int = 0,
) {
    val appCount: Int get() = steps.sumOf { it.packages.size }

    val isEmpty: Boolean get() = appCount == 0

    fun packages(kind: ProfileStepKind): List<String> = steps.firstOrNull { it.kind == kind }?.packages.orEmpty()

    fun count(kind: ProfileStepKind): Int = packages(kind).size
}

/** Un cambio aplicado por un perfil, con el estado anterior para poder deshacerlo. */
@Serializable
data class ProfileChange(
    val packageName: String,
    val from: ProfileAppState,
    val to: ProfileAppState,
)

/** Ejecucion de un perfil; la ultima se ofrece como "Deshacer". */
@Serializable
data class ProfileRun(
    val id: String,
    val profileId: String,
    val profileName: String,
    val appliedAt: Long,
    val changes: List<ProfileChange> = emptyList(),
    val failures: List<String> = emptyList(),
) {
    val count: Int get() = changes.size
}

/** Documento en disco: perfiles guardados y las ultimas ejecuciones. */
@Serializable
data class ProfilesDocument(
    val profiles: List<AppProfile> = emptyList(),
    val runs: List<ProfileRun> = emptyList(),
)

/** Progreso de una aplicacion por lotes, ya resuelto por la capa de datos. */
data class ProfileProgress(
    val done: Int,
    val total: Int,
    val packageName: String,
    val kind: ProfileStepKind,
)
