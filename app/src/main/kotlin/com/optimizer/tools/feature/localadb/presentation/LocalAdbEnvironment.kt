package com.optimizer.tools.feature.localadb.presentation

import com.optimizer.tools.feature.localadb.domain.ShellCommandResult

/** Puertos que consume el ViewModel; la composición los implementa con los UseCases. */
interface LocalAdbEnvironment {
    fun isAdbEnabled(): Boolean

    fun isDeveloperOptionsEnabled(): Boolean

    fun isAuthorized(): Boolean

    suspend fun connect()

    suspend fun run(command: String): ShellCommandResult

    fun disconnect()
}
