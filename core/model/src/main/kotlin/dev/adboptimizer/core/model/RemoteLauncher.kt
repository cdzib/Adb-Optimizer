package dev.adboptimizer.core.model

/** Activity HOME instalada en el dispositivo remoto. */
data class RemoteLauncher(
    val packageName: String,
    val componentName: String,
)
