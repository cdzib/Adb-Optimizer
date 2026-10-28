package com.optimizer.tools.feature.localadb.presentation

import com.optimizer.tools.feature.localadb.domain.ShellCommandResult
import kotlinx.coroutines.flow.StateFlow

/** Puertos que consume el ViewModel; la composición los implementa con los UseCases. */
interface LocalAdbEnvironment {
    /** Pasos de la conexion, en orden, para mostrarlos en pantalla y en Logcat. */
    val logs: StateFlow<List<String>>

    fun log(message: String)

    fun isAdbEnabled(): Boolean

    fun isDeveloperOptionsEnabled(): Boolean

    fun isAuthorized(): Boolean

    suspend fun connect()

    suspend fun run(command: String): ShellCommandResult

    fun disconnect()
}
