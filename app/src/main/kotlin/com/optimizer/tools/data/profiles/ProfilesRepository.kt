package com.optimizer.tools.data.profiles

import com.optimizer.tools.data.packages.PackageStateReader
import com.optimizer.tools.domain.profile.AppProfile
import com.optimizer.tools.domain.profile.ProfileApp
import com.optimizer.tools.domain.profile.ProfileAppState
import com.optimizer.tools.domain.profile.ProfileChange
import com.optimizer.tools.domain.profile.ProfilePlan
import com.optimizer.tools.domain.profile.ProfilePlanner
import com.optimizer.tools.domain.profile.ProfileProgress
import com.optimizer.tools.domain.profile.ProfileRun
import com.optimizer.tools.domain.profile.ProfilesDocument
import dev.adboptimizer.core.shell.AdbShell
import java.util.UUID

/**
 * Lectura y aplicacion de perfiles sobre el dispositivo activo.
 *
 * El trabajo con paquetes vive en [PackageStateReader], compartido con el debloater:
 * ambos ejecutan `pm` y confirman cada cambio leyendolo de vuelta.
 */
class ProfilesRepository(
    private val store: ProfileStore,
    shell: () -> AdbShell?,
) {
    private val packages = PackageStateReader(shell)

    fun document(): ProfilesDocument = store.document()

    fun profiles(): List<AppProfile> = store.document().profiles.sortedByDescending { it.createdAt }

    fun lastRun(): ProfileRun? = store.document().runs.firstOrNull()

    fun save(profile: AppProfile): AppProfile {
        store.update { document ->
            val existing = document.profiles.indexOfFirst { it.id == profile.id }
            val profiles =
                if (existing >= 0) {
                    document.profiles.toMutableList().also { it[existing] = profile }
                } else {
                    document.profiles + profile
                }
            val normalized =
                if (profile.watched) {
                    profiles.map { if (it.id == profile.id) it else it.copy(watched = false) }
                } else {
                    profiles
                }
            document.copy(profiles = normalized)
        }
        return profile
    }

    fun delete(id: String): Boolean {
        val before = store.document()
        val next = before.copy(profiles = before.profiles.filterNot { it.id == id })
        if (next == before) return false
        store.update { next }
        return true
    }

    /** Solo un perfil queda en vigilancia: el aviso de drift habla de uno a la vez. */
    fun setWatched(
        id: String,
        watched: Boolean,
    ): Boolean {
        val before = store.document()
        if (before.profiles.none { it.id == id }) return false
        store.update { document ->
            document.copy(
                profiles =
                    document.profiles.map {
                        when {
                            it.id == id -> it.copy(watched = watched)
                            watched -> it.copy(watched = false)
                            else -> it
                        }
                    },
            )
        }
        return true
    }

    suspend fun states(): Map<String, ProfileAppState> = packages.states()

    suspend fun snapshot(labels: Map<String, String>): List<ProfileApp> = ProfilePlanner.snapshot(packages.states(), labels)

    suspend fun plan(profile: AppProfile): ProfilePlan = ProfilePlanner.plan(profile, packages.states())

    /**
     * Ejecuta el plan del perfil. Un paso que el dispositivo rechaza no detiene el resto:
     * se anota el paquete y la ejecucion queda con lo aplicado para poder deshacerlo.
     */
    suspend fun apply(
        profile: AppProfile,
        onProgress: (ProfileProgress) -> Unit = {},
    ): ProfileRun {
        val before = packages.states()
        val plan = ProfilePlanner.plan(profile, before)
        val total = plan.appCount
        val changes = mutableListOf<ProfileChange>()
        val failures = mutableListOf<String>()
        var done = 0
        plan.steps.forEach { step ->
            step.packages.forEach { packageName ->
                runCatching { packages.execute(step.kind, packageName) }
                    .onSuccess {
                        changes +=
                            ProfileChange(packageName, before.getValue(packageName), profile.stateOf(packageName) ?: return@onSuccess)
                    }.onFailure { failures += packageName }
                done++
                onProgress(ProfileProgress(done, total, packageName, step.kind))
            }
        }
        val run =
            ProfileRun(
                id = UUID.randomUUID().toString(),
                profileId = profile.id,
                profileName = profile.name,
                appliedAt = System.currentTimeMillis(),
                changes = changes.toList(),
                failures = failures.toList(),
            )
        if (changes.isNotEmpty()) {
            store.update { document -> document.copy(runs = (listOf(run) + document.runs).take(MAX_RUNS)) }
        }
        return run
    }

    /** Devuelve las apps al estado que tenian antes de la ejecucion. */
    suspend fun undo(changes: List<ProfileChange>): Int {
        var restored = 0
        ProfilePlanner.undoPlan(changes).forEach { step ->
            step.packages.forEach { packageName ->
                runCatching { packages.execute(step.kind, packageName) }.onSuccess { restored++ }
            }
        }
        return restored
    }

    companion object {
        const val MAX_RUNS = 10
    }
}
