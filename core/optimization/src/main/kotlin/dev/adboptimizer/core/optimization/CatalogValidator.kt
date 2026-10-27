package dev.adboptimizer.core.optimization

import dev.adboptimizer.core.model.ActionCatalog
import dev.adboptimizer.core.model.Catalog

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
                val undoCommand = step.undo
                if (undoCommand != null && !undoCommand.contains("{{prev}}") && step.undoIfUnset != null) {
                    problems += "${recipe.id}[$index]: undoIfUnset sin plantilla {{prev}}"
                }
                if (step.type == dev.adboptimizer.core.model.RecipeStepType.INSTALL_SYSTEM_APK) {
                    if (!recipe.requiresRoot) problems += "${recipe.id}[$index]: installSystemApk requiere root"
                    if (step.apkPath.isNullOrBlank()) problems += "${recipe.id}[$index]: apkPath vacio"
                    val targetPath = step.targetPath
                    if (targetPath == null || !targetPath.startsWith("/system/") || targetPath.contains("..")) {
                        problems += "${recipe.id}[$index]: targetPath debe estar bajo /system"
                    }
                    if (step.mode?.matches(Regex("[0-7]{3,4}")) != true) problems += "${recipe.id}[$index]: mode invalido"
                    if (step.owner?.matches(Regex("[0-9]+:[0-9]+")) != true) problems += "${recipe.id}[$index]: owner invalido"
                    if (step.selinuxContext.isNullOrBlank()) problems += "${recipe.id}[$index]: selinuxContext vacio"
                    val disablePackages = step.disablePackages
                    if (disablePackages.isNullOrEmpty() || disablePackages.any { !it.matches(Regex("[A-Za-z0-9_]+(?:\\.[A-Za-z0-9_]+)+")) }) {
                        problems += "${recipe.id}[$index]: disablePackages invalido"
                    }
                }
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
}
