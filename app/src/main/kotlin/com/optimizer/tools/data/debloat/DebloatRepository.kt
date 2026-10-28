package com.optimizer.tools.data.debloat

import com.optimizer.tools.data.packages.PackageStateReader
import com.optimizer.tools.domain.debloat.DebloatAction
import com.optimizer.tools.domain.debloat.DebloatEntry
import com.optimizer.tools.domain.debloat.DebloatRun
import com.optimizer.tools.domain.profile.ProfileAppState
import com.optimizer.tools.domain.profile.ProfileChange
import com.optimizer.tools.domain.profile.ProfilePlanner
import dev.adboptimizer.core.shell.AdbShell

/** Ejecuta las tandas del debloater y conserva lo necesario para deshacerlas. */
class DebloatRepository(
    private val catalog: DebloatCatalog,
    shell: () -> AdbShell?,
) {
    private val packages = PackageStateReader(shell)

    fun available(): Boolean = packages.available()

    fun cachedCatalog(): List<DebloatEntry>? = catalog.cached()

    fun catalogUpdatedAt(): Long? = catalog.updatedAt()

    suspend fun downloadCatalog(onProgress: (Int) -> Unit = {}): List<DebloatEntry> = catalog.download(onProgress)

    suspend fun states(): Map<String, ProfileAppState> = packages.states()

    /** Congela, descongela o desinstala en orden; un fallo se anota y no detiene la tanda. */
    suspend fun apply(
        action: DebloatAction,
        targets: List<String>,
        onProgress: (Int, Int, String) -> Unit = { _, _, _ -> },
    ): DebloatRun {
        val before = packages.states()
        val changes = mutableListOf<ProfileChange>()
        val failures = mutableListOf<String>()
        var done = 0
        targets.forEach { packageName ->
            runCatching { packages.execute(action.step, packageName) }
                .onSuccess {
                    changes += ProfileChange(packageName, before[packageName] ?: ProfileAppState.ENABLED, action.targetState)
                }.onFailure { failures += packageName }
            done++
            onProgress(done, targets.size, packageName)
        }
        return DebloatRun(action = action, changes = changes.toList(), failures = failures.toList())
    }

    /** Devuelve cada paquete al estado que tenia antes de la tanda. */
    suspend fun undo(run: DebloatRun): Int {
        var restored = 0
        ProfilePlanner.undoPlan(run.changes).forEach { step ->
            step.packages.forEach { packageName ->
                runCatching { packages.execute(step.kind, packageName) }.onSuccess { restored++ }
            }
        }
        return restored
    }
}
