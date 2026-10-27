package com.optimizer.tools.data

import com.optimizer.tools.feature.logcat.LogcatEntry
import com.optimizer.tools.feature.logcat.LogcatParser
import dev.adboptimizer.core.shell.AdbShell

/** Lectura del registro del sistema por ADB shell, sin permisos especiales. */
class LogcatRepository(
    private val shell: AdbShell,
) {
    suspend fun read(limit: Int): List<LogcatEntry> {
        val result = shell.exec("logcat -d -v threadtime -t $limit", 20_000)
        if (result.exitCode != 0 && result.output.isBlank()) {
            throw IllegalStateException("El dispositivo no devolvio el registro.")
        }
        return LogcatParser.parse(result.output)
    }
}
