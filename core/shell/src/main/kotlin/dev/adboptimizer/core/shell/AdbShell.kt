package dev.adboptimizer.core.shell

import java.io.IOException

enum class AdbErrorKind { NOT_FOUND, UNAUTHORIZED, OFFLINE, TIMEOUT, SHELL, PAIRING }

class AdbException(
    message: String,
    val kind: AdbErrorKind,
    cause: Throwable? = null,
) : IOException(message, cause)

data class ShellResult(
    val exitCode: Int,
    val stdout: String,
    val stderr: String,
    val durationMs: Long,
) {
    val output: String get() = stdout.ifBlank { stderr }
}

/** Puerto unico de salida hacia el dispositivo. El motor de recetas no conoce el transporte. */
interface AdbShell {
    suspend fun exec(
        command: String,
        timeoutMs: Long = DEFAULT_TIMEOUT_MS,
    ): ShellResult

    companion object {
        const val DEFAULT_TIMEOUT_MS = 20_000L
    }
}
