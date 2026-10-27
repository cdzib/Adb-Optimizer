package com.optimizer.tools.feature.doze

/** Posicion en la linea de tiempo de Doze, de despierto a mantenimiento. */
enum class DozePhase { ACTIVE, INACTIVE, PENDING, IDLE, MAINTENANCE, UNKNOWN }

data class DozeStatus(
    val deep: String? = null,
    val light: String? = null,
    val forced: Boolean? = null,
) {
    val phase: DozePhase get() = DozeParser.phase(deep)
}

enum class DozeAction { FORCE, STEP, UNFORCE, MOTION }

/**
 * Reglas puras sobre lo que imprime `dumpsys deviceidle`.
 *
 * El estado se lee de `get deep` / `get light` (un token suelto) o del volcado completo
 * ("Stepped to deep: X", "Deep state: X, ..."). Verificado en Android 16: `get deep` responde
 * `ACTIVE` y `get force` responde `false`.
 */
object DozeParser {
    val DEEP_STATES =
        listOf("ACTIVE", "INACTIVE", "IDLE_PENDING", "SENSING", "LOCATING", "IDLE", "IDLE_MAINTENANCE", "QUICK_DOZE_DELAY")

    val LIGHT_STATES =
        listOf("ACTIVE", "INACTIVE", "PRE_IDLE", "IDLE", "WAITING_FOR_NETWORK", "IDLE_MAINTENANCE", "OVERRIDE")

    private val STEPPED = Regex("^Stepped to (deep|light): ([A-Z_]+)")
    private val DEEP_LINE = Regex("Deep state: ([A-Z_]+)")
    private val LIGHT_LINE = Regex("Light state: ([A-Z_]+)")

    fun token(
        raw: String?,
        light: Boolean,
    ): String? {
        val allowed = if (light) LIGHT_STATES else DEEP_STATES
        raw.orEmpty().lineSequence().forEach { line ->
            val text = line.trim()
            if (text in allowed) return text

            if (!light) {
                DEEP_LINE
                    .find(text)
                    ?.groupValues
                    ?.get(1)
                    ?.let { if (it in allowed) return it }
                STEPPED.find(text)?.let { match ->
                    if (match.groupValues[1] == "deep" && match.groupValues[2] in allowed) return match.groupValues[2]
                }
            } else {
                LIGHT_LINE
                    .find(text)
                    ?.groupValues
                    ?.get(1)
                    ?.let { if (it in allowed) return it }
                STEPPED.find(text)?.let { match ->
                    if (match.groupValues[1] == "light" && match.groupValues[2] in allowed) return match.groupValues[2]
                }
            }
        }
        return null
    }

    fun bool(raw: String?): Boolean? =
        when (
            raw
                ?.trim()
                ?.lineSequence()
                ?.firstOrNull()
                ?.trim()
                ?.lowercase()
        ) {
            "true" -> true
            "false" -> false
            else -> null
        }

    fun phase(deep: String?): DozePhase =
        when (deep) {
            "ACTIVE" -> DozePhase.ACTIVE
            "INACTIVE", "QUICK_DOZE_DELAY" -> DozePhase.INACTIVE
            "IDLE_PENDING", "SENSING", "LOCATING" -> DozePhase.PENDING
            "IDLE" -> DozePhase.IDLE
            "IDLE_MAINTENANCE" -> DozePhase.MAINTENANCE
            else -> DozePhase.UNKNOWN
        }
}
