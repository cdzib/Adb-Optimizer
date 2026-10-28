package com.optimizer.tools.feature.localadb.domain

/** Fase visible del flujo de ADB local. */
enum class LocalAdbPhase {
    DEBUGGING_OFF,
    CONNECTING,
    WAITING_AUTHORIZATION,
    CONNECTED,
    ERROR,
}

/** Motivo de error para que la UI elija el mensaje y las instrucciones. */
enum class LocalAdbErrorKind {
    PORT_CLOSED,
    AUTHORIZATION,
    TIMEOUT,
    DISCONNECTED,
    UNKNOWN,
}

data class ShellCommandResult(
    val command: String,
    val stdout: String,
    val stderr: String,
    val exitCode: Int,
    val durationMs: Long,
)

sealed class LocalAdbException(
    message: String,
    val kind: LocalAdbErrorKind,
    cause: Throwable? = null,
) : Exception(message, cause) {
    class PortClosed(
        cause: Throwable? = null,
    ) : LocalAdbException("El puerto 5555 no acepta conexiones", LocalAdbErrorKind.PORT_CLOSED, cause)

    class AuthorizationTimeout(
        cause: Throwable? = null,
    ) : LocalAdbException("El dispositivo no respondió a la autorización", LocalAdbErrorKind.AUTHORIZATION, cause)

    class NotConnected : LocalAdbException("No hay una sesión ADB local activa", LocalAdbErrorKind.DISCONNECTED)

    class Disconnected(
        cause: Throwable? = null,
    ) : LocalAdbException("La sesión ADB se cerró", LocalAdbErrorKind.DISCONNECTED, cause)

    class Unknown(
        cause: Throwable,
    ) : LocalAdbException(cause.message ?: "Fallo de ADB local", LocalAdbErrorKind.UNKNOWN, cause)
}

/** Puerto de datos: ajustes del dispositivo y sesión ADB de loopback. */
interface LocalAdbRepository {
    fun isAdbEnabled(): Boolean

    fun isDeveloperOptionsEnabled(): Boolean

    suspend fun connect()

    suspend fun runShell(command: String): ShellCommandResult

    fun disconnect()
}
