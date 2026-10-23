package com.optimizer.tools.feature.optimize

import dev.adboptimizer.core.model.DeviceInfo
import dev.adboptimizer.core.model.PlanItem
import dev.adboptimizer.core.model.Profile
import dev.adboptimizer.core.model.Recipe
import dev.adboptimizer.core.model.RecipeRun

/**
 * Frontera de la feature con el motor de recetas. Permite probar el ViewModel
 * sin Android ni ADB.
 */
interface OptimizeEngine {
    fun plan(
        device: DeviceInfo,
        profile: Profile,
    ): List<PlanItem>

    suspend fun run(
        recipe: Recipe,
        device: DeviceInfo,
    ): RecipeRun
}
