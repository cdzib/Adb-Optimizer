package com.optimizer.tools.domain.model

enum class ThemeMode {
    SYSTEM,
    LIGHT,
    DARK,
}

/** Preferencias de la aplicacion que afectan el comportamiento de la UI. */
data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val showSystemApps: Boolean = false,
    val keepScreenOn: Boolean = false,
    val autoRefreshMetrics: Boolean = false,
    val metricsRefreshSeconds: Int = 30,
    val autoReconnect: Boolean = true,
)
