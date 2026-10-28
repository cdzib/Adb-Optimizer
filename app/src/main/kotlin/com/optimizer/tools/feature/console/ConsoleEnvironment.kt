package com.optimizer.tools.feature.console

import dev.adboptimizer.core.shell.ShellResult

/** Puertos de la consola de comandos. */
interface ConsoleEnvironment {
    fun hasDevice(): Boolean

    fun history(): List<String>

    suspend fun run(command: String): ShellResult
}
