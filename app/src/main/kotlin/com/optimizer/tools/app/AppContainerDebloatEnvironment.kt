package com.optimizer.tools.app

import com.optimizer.tools.data.AppContainer
import com.optimizer.tools.domain.debloat.DebloatAction
import com.optimizer.tools.domain.debloat.DebloatEntry
import com.optimizer.tools.domain.debloat.DebloatRun
import com.optimizer.tools.domain.profile.ProfileAppState
import com.optimizer.tools.feature.debloat.DebloatEnvironment
import dev.adboptimizer.core.model.ManagedApp

/** Adaptador de composición para el debloater. */
internal class AppContainerDebloatEnvironment(
    private val container: AppContainer,
) : DebloatEnvironment {
    override fun hasDevice(): Boolean = container.debloat.available()

    override fun cachedCatalog(): List<DebloatEntry>? = container.debloat.cachedCatalog()

    override fun catalogUpdatedAt(): Long? = container.debloat.catalogUpdatedAt()

    override suspend fun downloadCatalog(onProgress: (Int) -> Unit): List<DebloatEntry> = container.debloat.downloadCatalog(onProgress)

    override suspend fun apps(): List<ManagedApp> =
        if (container.shell != null) {
            container.remoteApps()?.list(includeSystem = true).orEmpty()
        } else {
            container.localApps.list(includeSystem = true)
        }

    override suspend fun states(): Map<String, ProfileAppState> = container.debloat.states()

    override suspend fun apply(
        action: DebloatAction,
        targets: List<String>,
        onProgress: (Int, Int, String) -> Unit,
    ): DebloatRun = container.debloat.apply(action, targets, onProgress)

    override suspend fun undo(run: DebloatRun): Int = container.debloat.undo(run)
}
