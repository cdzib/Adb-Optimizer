package com.optimizer.tools.app

import com.optimizer.tools.data.AppContainer
import com.optimizer.tools.feature.localadb.domain.CheckAdbEnabled
import com.optimizer.tools.feature.localadb.domain.ConnectAdb
import com.optimizer.tools.feature.localadb.domain.RunShellCommand
import com.optimizer.tools.feature.localadb.domain.ShellCommandResult
import com.optimizer.tools.feature.localadb.presentation.LocalAdbEnvironment
import kotlinx.coroutines.flow.StateFlow

/** Composición de la feature ADB local: datos + casos de uso. */
internal class AppContainerLocalAdbEnvironment(
    private val container: AppContainer,
) : LocalAdbEnvironment {
    private val repository = container.localAdb
    private val checkAdbEnabled = CheckAdbEnabled(repository)
    private val connectAdb = ConnectAdb(repository)
    private val runShellCommand = RunShellCommand(repository)

    override val logs: StateFlow<List<String>> = repository.logs

    override fun log(message: String) = repository.appendLog(message)

    override fun isAdbEnabled(): Boolean = checkAdbEnabled()

    override fun isDeveloperOptionsEnabled(): Boolean = repository.isDeveloperOptionsEnabled()

    override fun isAuthorized(): Boolean = repository.isAuthorized()

    override suspend fun connect() = connectAdb()

    override suspend fun run(command: String): ShellCommandResult = runShellCommand(command)

    override fun disconnect() = repository.disconnect()
}
