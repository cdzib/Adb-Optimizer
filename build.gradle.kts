plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.detekt) apply false
    alias(libs.plugins.ktlint) apply false
}

/**
 * El catalogo y los fixtures de conformidad viven en el repo de escritorio (`shared/`), sin copias.
 * Se usa la primera carpeta que tenga `recipes/recipes.json`: `-PsharedDir=...`,
 * `ADB_OPTIMIZER_SHARED`, `../shared` y `../AdbOptimizer-Desktop/shared`.
 */
val sharedRootDir: File? =
    listOfNotNull(
        providers.gradleProperty("sharedDir").orNull,
        providers.environmentVariable("ADB_OPTIMIZER_SHARED").orNull,
        file("../shared").path,
        file("../AdbOptimizer-Desktop/shared").path,
    ).map(::File)
        .firstOrNull { File(it, "recipes/recipes.json").isFile }

extra["sharedRootDir"] = sharedRootDir
