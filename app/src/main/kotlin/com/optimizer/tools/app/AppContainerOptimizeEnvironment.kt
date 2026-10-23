package com.optimizer.tools.app

import com.optimizer.tools.data.AppContainer
import com.optimizer.tools.domain.model.ShizukuLocalStatus
import com.optimizer.tools.feature.optimize.OptimizeEngine
import com.optimizer.tools.feature.optimize.OptimizeEnvironment
import dev.adboptimizer.core.model.DeviceInfo
import dev.adboptimizer.core.model.PlanItem
import dev.adboptimizer.core.model.Profile
import dev.adboptimizer.core.model.Recipe
import dev.adboptimizer.core.model.RecipeRun
import dev.adboptimizer.core.optimization.RecipeEngine
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/**
 * Adaptador de composicion: traduce `AppContainer` al puerto estrecho de
 * Optimize. Vive en `app/` para que la feature no dependa de `data/`.
 */
internal class AppContainerOptimizeEnvironment(
    private val container: AppContainer,
) : OptimizeEnvironment {
    private val recipes by lazy { container.catalog.catalog.recipes }

    override val remoteAvailable: Flow<Boolean> =
        container.sessionStore.state
            .map { it.activeId != null }
            .distinctUntilChanged()

    override fun hasRemoteSession(): Boolean = container.sessionStore.activeId() != null

    override fun remoteDevice(): DeviceInfo? = container.device

    override fun remoteEngine(): OptimizeEngine? = container.engine?.let { RecipeEngineOptimizeEngine(it, recipes) }

    override fun localEngine(): OptimizeEngine = RecipeEngineOptimizeEngine(container.localEngine, recipes)

    override suspend fun localDevice(): DeviceInfo? = runCatching { container.localDevice.readInfo() }.getOrNull()

    override fun localShizukuStatus(): ShizukuLocalStatus = container.shizukuLocalAccess.status()
}

private class RecipeEngineOptimizeEngine(
    private val engine: RecipeEngine,
    private val recipes: List<Recipe>,
) : OptimizeEngine {
    override fun plan(
        device: DeviceInfo,
        profile: Profile,
    ): List<PlanItem> = engine.plan(recipes, device, profile)

    override suspend fun run(
        recipe: Recipe,
        device: DeviceInfo,
    ): RecipeRun = engine.run(recipe, device, dryRun = false)
}
