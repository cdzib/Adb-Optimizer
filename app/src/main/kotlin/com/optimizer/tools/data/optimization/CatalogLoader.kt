package com.optimizer.tools.data.optimization

import android.content.res.AssetManager
import dev.adboptimizer.core.model.ActionCatalog
import dev.adboptimizer.core.model.Catalog
import dev.adboptimizer.core.model.CatalogJson
import dev.adboptimizer.core.optimization.CatalogValidator
import java.io.File

data class CatalogBundle(
    val catalog: Catalog,
    val actions: ActionCatalog,
)

/** Fuente del catalogo. Se abstrae para poder probar sin Android. */
interface CatalogSource {
    fun read(path: String): String
}

class AssetCatalogSource(
    private val assets: AssetManager,
) : CatalogSource {
    override fun read(path: String): String = assets.open(path).bufferedReader(Charsets.UTF_8).use { it.readText() }
}

class FileCatalogSource(
    private val root: File,
) : CatalogSource {
    override fun read(path: String): String = File(root, path).readText(Charsets.UTF_8)
}

class CatalogLoader(
    private val source: CatalogSource,
) {
    fun load(): CatalogBundle {
        val catalog = CatalogJson.decodeFromString(Catalog.serializer(), source.read("recipes/recipes.json"))
        val actions =
            CatalogJson.decodeFromString(ActionCatalog.serializer(), source.read("recipes/actions.json"))

        val errors = CatalogValidator.validate(catalog, actions)
        if (errors.isNotEmpty()) {
            error("Catalogo invalido:\n" + errors.joinToString("\n") { "- $it" })
        }
        return CatalogBundle(catalog, actions)
    }
}
