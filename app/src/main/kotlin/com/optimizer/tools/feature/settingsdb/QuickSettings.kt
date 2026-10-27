package com.optimizer.tools.feature.settingsdb

/** Modo noche tal como lo entiende `cmd uimode night`. */
enum class NightMode(
    val wire: String,
) {
    YES("yes"),
    NO("no"),
    AUTO("auto"),
}

/** Valores de los ajustes rapidos que se leen antes de mostrar la pantalla. */
data class QuickSettings(
    val nightMode: NightMode? = null,
    val timeoutMs: Int? = null,
    val brightness: Int? = null,
    val batterySaver: Boolean? = null,
)

/** Presets de tiempo de pantalla, en milisegundos. */
val SCREEN_TIMEOUT_PRESETS = listOf(15_000, 30_000, 60_000, 300_000, 600_000)

object QuickSettingsParser {
    fun nightMode(raw: String?): NightMode? {
        val text = raw?.trim()?.lowercase().orEmpty()
        return NightMode.entries.firstOrNull { text.endsWith(it.wire) }
    }

    fun int(raw: String?): Int? = raw?.trim()?.toIntOrNull()

    fun bool(raw: String?): Boolean? =
        when (raw?.trim()?.lowercase()) {
            "1", "true" -> true
            "0", "false" -> false
            else -> null
        }
}
