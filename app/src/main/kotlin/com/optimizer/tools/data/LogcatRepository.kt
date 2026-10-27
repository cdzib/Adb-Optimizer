package com.optimizer.tools.data

import com.optimizer.tools.feature.logcat.LogcatEntry
import com.optimizer.tools.feature.logcat.LogcatParser
import dev.adboptimizer.core.shell.AdbShell
import dev.adboptimizer.core.shell.ShellQuote

/** Lectura del registro del sistema por ADB shell, sin permisos especiales. */
class LogcatRepository(
    private val shell: AdbShell,
) {
    /**
     * Lee el registro. Sin [since] trae las ultimas [limit] lineas; con una marca de tiempo
     * pide solo lo posterior (`-T`), que es lo que hace barato el modo en vivo.
     */
    suspend fun read(
        limit: Int,
        since: String? = null,
    ): List<LogcatEntry> {
        val command =
            if (since == null) {
                "logcat -d -v threadtime -t $limit"
            } else {
                "logcat -d -v threadtime -T ${ShellQuote.of(since)}"
            }
        val result = shell.exec(command, 20_000)
        if (result.exitCode != 0 && result.output.isBlank()) {
            throw IllegalStateException("El dispositivo no devolvio el registro.")
        }
        return LogcatParser.parse(result.output)
    }
}
