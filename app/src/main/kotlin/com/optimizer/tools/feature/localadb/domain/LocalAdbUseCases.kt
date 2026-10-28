package com.optimizer.tools.feature.localadb.domain

/** Comprueba `Settings.Global.ADB_ENABLED` sin intentar activarlo. */
class CheckAdbEnabled(
    private val repository: LocalAdbRepository,
) {
    operator fun invoke(): Boolean = repository.isAdbEnabled()
}

/** Abre la sesión contra 127.0.0.1:5555 reutilizando el par de llaves guardado. */
class ConnectAdb(
    private val repository: LocalAdbRepository,
) {
    suspend operator fun invoke() = repository.connect()
}

/** Ejecuta un comando shell y devuelve salida y código de salida. */
class RunShellCommand(
    private val repository: LocalAdbRepository,
) {
    suspend operator fun invoke(command: String): ShellCommandResult {
        require(command.isNotBlank()) { "El comando no puede estar vacío" }
        return repository.runShell(command.trim())
    }
}
