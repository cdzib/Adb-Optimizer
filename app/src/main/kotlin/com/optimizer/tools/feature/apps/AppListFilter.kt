package com.optimizer.tools.feature.apps

import dev.adboptimizer.core.model.AppFilter
import dev.adboptimizer.core.model.AppSort
import dev.adboptimizer.core.model.ManagedApp

/**
 * Reglas de busqueda, filtrado y orden de la lista de apps.
 * Se mantienen fuera de Compose para poder probarlas sin UI.
 */
object AppListFilter {
    fun apply(
        apps: List<ManagedApp>,
        query: String,
        filters: Set<AppFilter>,
        sort: AppSort,
    ): List<ManagedApp> {
        val typeFilters = filters.filter { it == AppFilter.USER || it == AppFilter.SYSTEM }
        val filtered =
            apps
                .asSequence()
                .filter { app ->
                    val typeOk =
                        typeFilters.isEmpty() ||
                            (AppFilter.USER in typeFilters && !app.isSystem) ||
                            (AppFilter.SYSTEM in typeFilters && app.isSystem)
                    val stateOk = if (AppFilter.DISABLED in filters) !app.enabled else true
                    typeOk && stateOk
                }.filter { app ->
                    query.isBlank() ||
                        app.displayName.contains(query, ignoreCase = true) ||
                        app.packageName.contains(query, ignoreCase = true)
                }.toList()
        return when (sort) {
            AppSort.NAME -> filtered.sortedBy { it.displayName.lowercase() }
            AppSort.SIZE -> filtered.sortedByDescending { it.sizeBytes }
            AppSort.UPDATED -> filtered.sortedByDescending { it.updatedAt }
            AppSort.VERSION -> filtered.sortedByDescending { it.versionCode }
        }
    }
}
