package com.optimizer.tools.app

import com.optimizer.tools.data.AppContainer
import com.optimizer.tools.feature.console.ConsoleEnvironment
import dev.adboptimizer.core.shell.ShellResult

/** Adaptador de composición para la consola de comandos. */
internal class AppContainerConsoleEnvironment(
    private val container: AppContainer,
) : ConsoleEnvironment {
    override fun hasDevice(): Boolean = container.console.available()

    override fun history(): List<String> = container.console.history()

    override suspend fun run(command: String): ShellResult = container.console.run(command)
}
