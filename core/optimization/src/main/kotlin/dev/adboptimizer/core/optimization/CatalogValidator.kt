package dev.adboptimizer.core.optimization

import dev.adboptimizer.core.model.ActionCatalog
import dev.adboptimizer.core.model.Catalog
import dev.adboptimizer.core.model.Recipe
import dev.adboptimizer.core.model.RecipeStep
import dev.adboptimizer.core.model.RecipeStepType
import dev.adboptimizer.core.shell.AdbCommandRules

object CatalogValidator {
    fun validate(
        catalog: Catalog,
        actions: ActionCatalog,
    ): List<String> {
        val problems = mutableListOf<String>()

        if (catalog.schemaVersion != 1) problems += "schemaVersion no soportada: ${catalog.schemaVersion}"
        if (catalog.recipes.isEmpty()) problems += "el catalogo no tiene recetas"

        val duplicated =
            catalog.recipes
                .groupBy { it.id }
                .filterValues { it.size > 1 }
                .keys
        duplicated.forEach { problems += "id de receta duplicado: $it" }

        catalog.recipes.forEach { recipe ->
            if (recipe.steps.isEmpty()) problems += "${recipe.id}: sin pasos"
            if (recipe.titleKey.isBlank()) problems += "${recipe.id}: titleKey vacio"
            if (recipe.summaryKey.isBlank()) problems += "${recipe.id}: summaryKey vacio"
            recipe.steps.forEachIndexed { index, step ->
                if (step.timeoutMs <= 0) problems += "${recipe.id}[$index]: timeout invalido"
                if (step.run.isBlank()) problems += "${recipe.id}[$index]: comando vacio"
                if (step.verify.run.isBlank()) problems += "${recipe.id}[$index]: verificacion vacia"
                if (!step.verify.hasExpectation) problems += "${recipe.id}[$index]: verificacion sin expectativa"
                val undoCommand = step.undo
                if (undoCommand != null && !undoCommand.contains("{{prev}}") && step.undoIfUnset != null) {
                    problems += "${recipe.id}[$index]: undoIfUnset sin plantilla {{prev}}"
                }
                if (step.type == RecipeStepType.INSTALL_SYSTEM_APK) problems += systemApkProblems(recipe, index, step)
            }
        }

        val duplicatedActions =
            actions.actions
                .groupBy { it.id }
                .filterValues { it.size > 1 }
                .keys
        duplicatedActions.forEach { problems += "id de accion duplicado: $it" }

        actions.actions.forEach { action ->
            if (action.paramKind.name == "PACKAGE" && !action.run.contains("{{pkg}}")) {
                problems += "${action.id}: accion por paquete sin plantilla {{pkg}}"
            }
        }

        return problems
    }

    private fun systemApkProblems(
        recipe: Recipe,
        index: Int,
        step: RecipeStep,
    ): List<String> {
        val problems = mutableListOf<String>()
        val prefix = "${recipe.id}[$index]"
        if (!recipe.requiresRoot) problems += "$prefix: installSystemApk requiere root"
        if (step.apkPath.isNullOrBlank()) problems += "$prefix: apkPath vacio"

        val targetPath = step.targetPath
        if (targetPath == null || !targetPath.startsWith("/system/") || targetPath.contains("..")) {
            problems += "$prefix: targetPath debe estar bajo /system"
        }
        if (step.mode?.matches(SYSTEM_APK_MODE) != true) problems += "$prefix: mode invalido"
        if (step.owner?.matches(SYSTEM_APK_OWNER) != true) problems += "$prefix: owner invalido"
        if (step.selinuxContext.isNullOrBlank()) problems += "$prefix: selinuxContext vacio"

        val packages = step.disablePackages
        if (packages.isNullOrEmpty() || packages.any { !AdbCommandRules.isPackageName(it) }) {
            problems += "$prefix: disablePackages invalido"
        }
        return problems
    }

    private val SYSTEM_APK_MODE = Regex("[0-7]{3,4}")
    private val SYSTEM_APK_OWNER = Regex("[0-9]+:[0-9]+")
}
