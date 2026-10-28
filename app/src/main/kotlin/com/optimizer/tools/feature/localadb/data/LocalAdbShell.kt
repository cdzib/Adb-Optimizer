package com.optimizer.tools.feature.localadb.data

import com.optimizer.tools.feature.localadb.domain.ShellCommandResult
import dev.adboptimizer.core.shell.AdbErrorKind
import dev.adboptimizer.core.shell.AdbException
import dev.adboptimizer.core.shell.AdbShell
import dev.adboptimizer.core.shell.ShellResult
import kotlinx.coroutines.withTimeoutOrNull

/** Transporte ADB local que el resto de herramientas puede usar como shell privilegiado. */
interface LocalAdbTransport : AdbShell {
    fun isConnected(): Boolean
}

/**
 * Envuelve el repositorio dadb para exponerlo como [AdbShell] sin que las features
 * conozcan la librería ni el par de llaves.
 */
class LocalAdbShell(
    private val repository: DadbLocalAdbRepository,
) : LocalAdbTransport {
    override fun isConnected(): Boolean = repository.isConnected()

    override suspend fun exec(
        command: String,
        timeoutMs: Long,
    ): ShellResult {
        val result =
            withTimeoutOrNull(timeoutMs.coerceAtLeast(1L)) {
                repository.runShell(command)
            } ?: throw AdbException(
                "La sesión ADB local no respondió a tiempo.",
                AdbErrorKind.TIMEOUT,
            )
        return result.toShellResult()
    }

    private fun ShellCommandResult.toShellResult() =
        ShellResult(
            exitCode = exitCode,
            stdout = stdout,
            stderr = stderr,
            durationMs = durationMs,
        )
}
