package com.optimizer.tools.data

import com.optimizer.tools.feature.settingsdb.NightMode
import com.optimizer.tools.feature.settingsdb.QuickSettings
import com.optimizer.tools.feature.settingsdb.QuickSettingsParser
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

    /** Lee los cuatro ajustes rapidos; cada uno que falle queda en `null`. */
    suspend fun quickSettings(): QuickSettings =
        QuickSettings(
            nightMode = QuickSettingsParser.nightMode(readOrNull("cmd uimode night")),
            timeoutMs = QuickSettingsParser.int(readOrNull("settings get system screen_off_timeout")),
            brightness = QuickSettingsParser.int(readOrNull("settings get system screen_brightness")),
            batterySaver = QuickSettingsParser.bool(readOrNull("settings get global low_power")),
        )

    /** Cambia el modo noche y confirma que el dispositivo lo reporta. */
    suspend fun setNightMode(mode: NightMode): Boolean {
        val command = requireNotNull(AdbCommandRules.nightModeCommand(mode.wire)) { "Modo noche invalido." }
        shell.exec(command, 15_000)
        repeat(ATTEMPTS) { attempt ->
            if (attempt > 0) pause(PAUSE_MS)
            val printed = readOrNull("cmd uimode night") ?: return@repeat
            if (AdbCommandRules.nightModeReported(printed, mode.wire)) return true
        }
        return false
    }

    suspend fun setScreenTimeout(milliseconds: Int): Boolean =
        writeConfirmed("system", "screen_off_timeout", milliseconds.coerceAtLeast(15_000).toString())

    suspend fun setBrightness(value: Int): Boolean = writeConfirmed("system", "screen_brightness", value.coerceIn(0, 255).toString())

    suspend fun setBatterySaver(enabled: Boolean): Boolean = writeConfirmed("global", "low_power", if (enabled) "1" else "0")

    private suspend fun writeConfirmed(
        namespace: String,
        key: String,
        value: String,
    ): Boolean {
        val put =
            requireNotNull(AdbCommandRules.settingsPutCommand(namespace, key, value)) {
                "La clave o el valor no son validos."
            }
        val get = requireNotNull(AdbCommandRules.settingsGetCommand(namespace, key)) { "La clave no es valida." }
        val result = shell.exec(put, 20_000)
        if (result.exitCode != 0) {
            throw IllegalStateException(result.output.ifBlank { "El dispositivo rechazo el cambio." })
        }
        repeat(ATTEMPTS) { attempt ->
            if (attempt > 0) pause(PAUSE_MS)
            val printed = readOrNull(get) ?: return@repeat
            if (SettingsDbParser.readBackMatches(printed, value)) return true
        }
        return false
    }

    private suspend fun readOrNull(command: String): String? = runCatching { shell.exec(command, 10_000).stdout }.getOrNull()

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

    private companion object {
        const val ATTEMPTS = 3
        const val PAUSE_MS = 150L
    }
}
