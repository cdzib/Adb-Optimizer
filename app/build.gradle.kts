plugins {
    alias(libs.plugins.detekt)
    alias(libs.plugins.ktlint)
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

/**
 * El catalogo es la unica fuente de verdad del repo de escritorio (`shared/`), sin copias.
 * Se busca en este orden y se usa la primera carpeta que tenga `recipes/recipes.json`:
 * `-PsharedDir=...`, `ADB_OPTIMIZER_SHARED`, `../shared` y `../AdbOptimizer-Desktop/shared`.
 */
val sharedCatalogDir: File? =
    listOfNotNull(
        providers.gradleProperty("sharedDir").orNull,
        providers.environmentVariable("ADB_OPTIMIZER_SHARED").orNull,
        rootProject.file("../shared").path,
        rootProject.file("../AdbOptimizer-Desktop/shared").path,
    ).map(::File)
        .firstOrNull { File(it, "recipes/recipes.json").isFile }

val sharedCatalogPresent = sharedCatalogDir != null

android {
    namespace = "com.optimizer.tools"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.optimizer.tools"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
        debug {
            applicationIdSuffix = ".debug"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
        aidl = true
    }

    // Unica fuente de verdad del catalogo: shared/ del repo de escritorio, sin copias.
    sourceSets["main"].assets.srcDir(sharedCatalogDir ?: rootProject.file("../shared"))

    packaging {
        resources.excludes += setOf("/META-INF/{AL2.0,LGPL2.1}")
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(project(":core:model"))
    implementation(project(":core:adb"))
    implementation(project(":core:optimization"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.zxing.core)
    implementation(libs.zxing.embedded)
    implementation(libs.shizuku.api)
    implementation(libs.shizuku.provider)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons)
    debugImplementation(libs.androidx.compose.ui.tooling)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}

// Un APK sin catalogo no sirve: el empaquetado falla con un mensaje que dice como resolverlo.
tasks
    .matching {
        it.name.startsWith("merge") &&
            it.name.endsWith("Assets") &&
            !it.name.contains("UnitTest") &&
            !it.name.contains("AndroidTest")
    }.configureEach {
        if (!sharedCatalogPresent) {
            doFirst {
                throw GradleException(
                    "No se encontro el catalogo compartido (recipes/recipes.json). " +
                        "Ejecuta con -PsharedDir=/ruta/al/shared o define ADB_OPTIMIZER_SHARED.",
                )
            }
        }
    }

// Los tests del catalogo reciben la misma carpeta que el empaquetado, para no adivinar rutas.
tasks.withType<org.gradle.api.tasks.testing.Test>().configureEach {
    sharedCatalogDir?.let { systemProperty("adb.shared.dir", it.absolutePath) }
}

detekt {
    buildUponDefaultConfig = false
    parallel = true
    config.setFrom(rootProject.files("config/detekt/detekt.yml"))
}

ktlint {
    version.set(libs.versions.ktlintCli.get())
    android.set(true)
    filter {
        exclude { it.file.path.contains("/build/") }
    }
}
