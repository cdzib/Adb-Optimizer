package com.optimizer.tools.data

import dev.adboptimizer.core.shell.AdbCommandRules
import dev.adboptimizer.core.shell.AdbShell
import kotlinx.coroutines.delay

/** Una clave de las tablas de `settings` con su valor tal como lo imprime el dispositivo. */
data class SettingEntry(
    val key: String,
    val value: String,
)

/** Reglas puras de la salida de `settings list`. */
object SettingsDbParser {
    fun parse(raw: String): List<SettingEntry> {
        val entries = mutableListOf<SettingEntry>()
        raw.lineSequence().forEach { line ->
            val trimmed = line.trim()
            if (trimmed.isEmpty()) return@forEach
            val separator = trimmed.indexOf('=')
            if (separator <= 0) return@forEach
            entries += SettingEntry(trimmed.substring(0, separator), trimmed.substring(separator + 1))
        }
        return entries
    }

    /** `settings get` imprime `null` cuando la clave no existe. */
    fun readBackMatches(
        printed: String,
        expected: String,
    ): Boolean {
        val value = printed.trim()
        val normalized = if (value == "null") "" else value
        return normalized == expected
    }
}

/** Lectura y escritura de las tablas global, secure y system con confirmacion por read-back. */
class SettingsDbRepository(
    private val shell: AdbShell,
    private val pause: suspend (Long) -> Unit = { delay(it) },
) {
    suspend fun list(namespace: String): List<SettingEntry> {
        require(AdbCommandRules.isSettingsNamespace(namespace)) { "Tabla desconocida." }
        val result = shell.exec("settings list $namespace", 25_000)
        if (result.exitCode != 0) {
            throw IllegalStateException(result.output.ifBlank { "El dispositivo rechazo la lectura de la tabla." })
        }
        return SettingsDbParser.parse(result.stdout)
    }

    /**
     * Escribe el valor y lo vuelve a leer. Devuelve `false` cuando Android acepta el comando
     * pero no conserva el valor, que es lo que hace con claves protegidas por el fabricante.
     */
    suspend fun save(
        namespace: String,
        key: String,
        value: String,
        attempts: Int = 3,
    ): Boolean {
        val put =
            requireNotNull(AdbCommandRules.settingsPutCommand(namespace, key, value)) {
                "La clave o el valor no son validos."
            }
        val get =
            requireNotNull(AdbCommandRules.settingsGetCommand(namespace, key)) {
                "La clave no es valida."
            }

        val result = shell.exec(put, 20_000)
        if (result.exitCode != 0) {
            throw IllegalStateException(result.output.ifBlank { "El dispositivo rechazo el cambio." })
        }

        repeat(attempts.coerceAtLeast(1)) { attempt ->
            if (attempt > 0) pause(150)
            val printed = runCatching { shell.exec(get, 10_000).stdout }.getOrNull() ?: return@repeat
            if (SettingsDbParser.readBackMatches(printed, value)) return true
        }
        return false
    }
}
