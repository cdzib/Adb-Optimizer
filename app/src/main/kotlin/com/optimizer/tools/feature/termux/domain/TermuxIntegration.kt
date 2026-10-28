package com.optimizer.tools.feature.termux.domain

/** Permiso peligroso que Termux exige a las apps que lanzan comandos en su entorno. */
const val TERMUX_RUN_COMMAND_PERMISSION = "com.termux.permission.RUN_COMMAND"

/** Estado de la integración con Termux en este dispositivo. */
data class TermuxAvailability(
    val installed: Boolean = false,
    val versionName: String? = null,
    val apiInstalled: Boolean = false,
    val permissionGranted: Boolean = false,
) {
    /** Solo se pueden lanzar comandos cuando Termux existe y el permiso está concedido. */
    val ready: Boolean get() = installed && permissionGranted
}

/** Resultado de un comando ejecutado en el contexto de Termux. */
data class TermuxResult(
    val executionId: Int,
    val stdout: String,
    val stderr: String,
    val exitCode: Int,
    val stdoutTruncated: Boolean = false,
    val stderrTruncated: Boolean = false,
    val internalError: String? = null,
) {
    val succeeded: Boolean get() = internalError == null && exitCode == 0

    val output: String get() = stdout.ifBlank { stderr }.trim()
}

/** Motivo de fallo para que la UI elija el mensaje y las instrucciones. */
enum class TermuxErrorKind {
    NOT_INSTALLED,
    PERMISSION_DENIED,
    EXTERNAL_APPS_DISABLED,
    START_FAILED,
    TIMEOUT,
    UNKNOWN,
}

sealed class TermuxException(
    message: String,
    val kind: TermuxErrorKind,
    cause: Throwable? = null,
) : Exception(message, cause) {
    class NotInstalled : TermuxException("Termux no está instalado", TermuxErrorKind.NOT_INSTALLED)

    class PermissionDenied :
        TermuxException(
            "Falta el permiso com.termux.permission.RUN_COMMAND",
            TermuxErrorKind.PERMISSION_DENIED,
        )

    class StartFailed(
        cause: Throwable? = null,
    ) : TermuxException("Termux no aceptó el comando", TermuxErrorKind.START_FAILED, cause)

    class Timeout : TermuxException("Termux no devolvió el resultado del comando", TermuxErrorKind.TIMEOUT)

    class Unknown(
        cause: Throwable,
    ) : TermuxException(cause.message ?: "Fallo de la integración con Termux", TermuxErrorKind.UNKNOWN, cause)
}

/** Puerto de datos: ejecuta comandos en el contexto de Termux y espera su resultado. */
interface TermuxRepository {
    fun availability(): TermuxAvailability

    suspend fun run(
        command: String,
        label: String,
        timeoutMs: Long = DEFAULT_TIMEOUT_MS,
    ): TermuxResult

    companion object {
        /** Los `pkg install` pueden tardar varios minutos en el primer arranque. */
        const val DEFAULT_TIMEOUT_MS = 180_000L
    }
}
