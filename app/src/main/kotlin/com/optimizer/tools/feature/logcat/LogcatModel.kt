package com.optimizer.tools.feature.logcat

/**
 * Una linea de `logcat -v threadtime` ya separada.
 *
 * Formato del dispositivo: `01-02 03:04:05.678  1234  1250 I Tag : mensaje`.
 * Las lineas de continuacion (por ejemplo un stack trace) se anexan al mensaje anterior.
 */
data class LogcatEntry(
    val time: String,
    val pid: String,
    val tid: String,
    val level: Char,
    val tag: String,
    val message: String,
) {
    val line: String get() = "$time $level/$tag($pid): $message"
}

/** Reglas puras del visor de registro. */
object LogcatParser {
    /** De menos a mas grave. */
    val levels: List<Char> = listOf('V', 'D', 'I', 'W', 'E', 'F')

    private val THREADTIME =
        Regex("""^(\d{2}-\d{2}\s+\d{2}:\d{2}:\d{2}\.\d{3})\s+(\d+)\s+(\d+)\s+([VDIWEF])\s+(.+?)\s*:\s?(.*)$""")

    fun parse(raw: String): List<LogcatEntry> {
        val entries = mutableListOf<LogcatEntry>()
        raw.lineSequence().forEach { rawLine ->
            val line = rawLine.trimEnd()
            if (line.isBlank()) return@forEach

            val match = THREADTIME.find(line)
            if (match != null) {
                entries +=
                    LogcatEntry(
                        time = match.groupValues[1],
                        pid = match.groupValues[2],
                        tid = match.groupValues[3],
                        level = match.groupValues[4].single(),
                        tag = match.groupValues[5].trim(),
                        message = match.groupValues[6],
                    )
                return@forEach
            }

            // Continuacion de un mensaje multilinea: conserva la traza con la entrada anterior.
            val previous = entries.lastOrNull()
            val continues = line.startsWith(" ") || line.startsWith("\t")
            if (previous != null && continues) {
                entries[entries.lastIndex] = previous.copy(message = previous.message + "\n" + line.trim())
            }
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
