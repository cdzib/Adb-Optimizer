package com.optimizer.tools.feature.logcat

/** Una linea de `logcat` ya separada en nivel, etiqueta, proceso y mensaje. */
data class LogcatEntry(
    val level: Char,
    val tag: String,
    val pid: String,
    val message: String,
) {
    val line: String get() = "$level/$tag($pid): $message"
}

/**
 * Reglas puras del visor de registro. `logcat -v brief` imprime lineas con la forma
 * `D/Tag     ( 1234): mensaje`; lo que no encaja se descarta en lugar de mostrarse a medias.
 */
object LogcatParser {
    /** De menos a mas grave. */
    val levels: List<Char> = listOf('V', 'D', 'I', 'W', 'E', 'F')

    private val LINE = Regex("""^([VDIWEF])/(.+?)\s*\(\s*(\d+)\):\s?(.*)$""")

    fun parse(raw: String): List<LogcatEntry> {
        val entries = mutableListOf<LogcatEntry>()
        raw.lineSequence().forEach { line ->
            val match = LINE.find(line.trimEnd()) ?: return@forEach
            entries +=
                LogcatEntry(
                    level = match.groupValues[1].single(),
                    tag = match.groupValues[2].trim(),
                    pid = match.groupValues[3],
                    message = match.groupValues[4],
                )
        }
        return entries
    }

    /** Conserva desde [minLevel] hacia arriba y lo que contenga [query] en etiqueta o mensaje. */
    fun filter(
        entries: List<LogcatEntry>,
        minLevel: Char,
        query: String,
    ): List<LogcatEntry> {
        val threshold = levels.indexOf(minLevel).coerceAtLeast(0)
        val needle = query.trim()
        return entries.filter { entry ->
            levels.indexOf(entry.level) >= threshold &&
                (needle.isEmpty() || entry.tag.contains(needle, ignoreCase = true) || entry.message.contains(needle, ignoreCase = true))
        }
    }
}
