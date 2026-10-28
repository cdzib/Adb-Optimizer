package com.optimizer.tools.feature.profiles

import com.optimizer.tools.domain.profile.AppProfile
import com.optimizer.tools.domain.profile.ProfileApp
import com.optimizer.tools.domain.profile.ProfilePlan
import com.optimizer.tools.domain.profile.ProfileProgress
import com.optimizer.tools.domain.profile.ProfileRun

/** Puertos de la pantalla Perfiles: snapshots y aplicacion sobre el dispositivo activo. */
interface ProfilesEnvironment {
    /** Hay un destino con shell disponible (sesion ADB o Shizuku autorizado). */
    fun hasDevice(): Boolean

    suspend fun profiles(): List<AppProfile>

    suspend fun lastRun(): ProfileRun?

    suspend fun snapshot(): List<ProfileApp>

    suspend fun plan(profile: AppProfile): ProfilePlan

    suspend fun save(profile: AppProfile): Boolean

    suspend fun delete(id: String): Boolean

    suspend fun setWatched(
        id: String,
        watched: Boolean,
    ): Boolean

    suspend fun apply(
        profile: AppProfile,
        onProgress: (ProfileProgress) -> Unit,
    ): ProfileRun

    suspend fun undo(run: ProfileRun): Int
}
