plugins {
    alias(libs.plugins.detekt)
    alias(libs.plugins.ktlint)
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "dev.adboptimizer.core.adb"
    compileSdk = 36

    defaultConfig {
        minSdk = 26
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    api(project(":core:model"))
    api(project(":core:shell"))
    implementation(libs.libadb)
    implementation(libs.conscrypt)
    implementation(libs.bouncycastle.pkix)
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}

// Los fixtures de conformidad salen de la misma carpeta `shared/` que el catalogo.
val sharedRootDir: File? = rootProject.extra["sharedRootDir"] as File?

tasks.withType<org.gradle.api.tasks.testing.Test>().configureEach {
    sharedRootDir?.let {
        systemProperty("adb.shared.dir", it.absolutePath)
        inputs.dir(File(it, "conformance")).withPropertyName("sharedConformance")
    }
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
