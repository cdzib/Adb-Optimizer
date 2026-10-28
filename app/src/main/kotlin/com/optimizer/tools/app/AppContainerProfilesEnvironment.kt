package com.optimizer.tools.app

import com.optimizer.tools.data.AppContainer
import com.optimizer.tools.domain.profile.AppProfile
import com.optimizer.tools.domain.profile.ProfileApp
import com.optimizer.tools.domain.profile.ProfilePlan
import com.optimizer.tools.domain.profile.ProfileProgress
import com.optimizer.tools.domain.profile.ProfileRun
import com.optimizer.tools.feature.profiles.ProfilesEnvironment

/** Adaptador de composición para los perfiles de estado de apps. */
internal class AppContainerProfilesEnvironment(
    private val container: AppContainer,
) : ProfilesEnvironment {
    override fun hasDevice(): Boolean = container.shell != null || localReady()

    override suspend fun profiles(): List<AppProfile> = container.profiles.profiles()

    override suspend fun lastRun(): ProfileRun? = container.profiles.lastRun()

    override suspend fun snapshot(): List<ProfileApp> = container.profiles.snapshot(labels())

    override suspend fun plan(profile: AppProfile): ProfilePlan = container.profiles.plan(profile)

    override suspend fun save(profile: AppProfile): Boolean {
        container.profiles.save(profile)
        return true
    }

    override suspend fun delete(id: String): Boolean = container.profiles.delete(id)

    override suspend fun setWatched(
        id: String,
        watched: Boolean,
    ): Boolean = container.profiles.setWatched(id, watched)

    override suspend fun apply(
        profile: AppProfile,
        onProgress: (ProfileProgress) -> Unit,
    ): ProfileRun = container.profiles.apply(profile, onProgress)

    override suspend fun undo(run: ProfileRun): Int = container.profiles.undo(run.changes)

    /**
     * Los nombres visibles se leen de la misma lista de apps que usa la pantalla Apps:
     * PackageManager en local y `pm` en remoto. Si el listado falla, el perfil se guarda
     * igual: el paquete sirve como etiqueta.
     */
    private suspend fun labels(): Map<String, String> {
        val apps =
            if (container.shell != null) {
                runCatching { container.remoteApps()?.list(includeSystem = true).orEmpty() }.getOrDefault(emptyList())
            } else {
                runCatching { container.localApps.list(includeSystem = true) }.getOrDefault(emptyList())
            }
        return apps.associate { it.packageName to it.label }
    }

    private fun localReady(): Boolean = container.localPrivilegeReady()
}
