package com.optimizer.tools.data

import com.optimizer.tools.domain.model.DevicePowerAction
import dev.adboptimizer.core.shell.AdbErrorKind
import dev.adboptimizer.core.shell.AdbException
import dev.adboptimizer.core.shell.AdbShell
import kotlinx.coroutines.delay

/**
 * One request, no retry: a reboot can close its own transport before replying.
 *
 * La excepcion es reiniciar la interfaz: no hay reinicio del transporte, asi que se comprueba
 * que `com.android.systemui` tenga un PID nuevo antes de darlo por hecho.
 */
class DevicePowerRepository(
    private val shell: AdbShell,
    private val pause: suspend (Long) -> Unit = { delay(it) },
) {
    suspend fun execute(action: DevicePowerAction) {
        if (action == DevicePowerAction.RESTART_SYSTEM_UI) {
            restartSystemUi()
            return
        }
        val command =
            when (action) {
                DevicePowerAction.REBOOT -> "svc power reboot"
                DevicePowerAction.SHUTDOWN -> "svc power shutdown"
                DevicePowerAction.RECOVERY -> "svc power reboot recovery"
                DevicePowerAction.BOOTLOADER -> "svc power reboot bootloader"
                DevicePowerAction.RESTART_SYSTEM_UI -> error("Se maneja antes de construir el comando.")
            }
        run(command)
    }

    private suspend fun restartSystemUi() {
        val before = processId(SYSTEM_UI)
        run("am crash $SYSTEM_UI")

        repeat(RESTART_ATTEMPTS) { attempt ->
            if (attempt > 0) pause(RESTART_PAUSE_MS)
            val after = processId(SYSTEM_UI)
            if (after != null && after != before) return
        }
        throw AdbException(
            "La interfaz no reinició: el proceso sigue con el mismo PID (${before ?: "desconocido"}).",
            AdbErrorKind.SHELL,
        )
    }

    private suspend fun processId(packageName: String): String? =
        runCatching { shell.exec("pidof $packageName", 5_000L).stdout.trim() }
            .getOrNull()
            ?.ifBlank { null }

    private suspend fun run(command: String) {
        val result = shell.exec(command, 10_000L)
        if (result.exitCode != 0 || result.stderr.isNotBlank() ||
            Regex("error|exception|failed|permission denial|unknown command|usage:", RegexOption.IGNORE_CASE).containsMatchIn(result.stdout)
        ) {
            throw AdbException(
                result.stderr.ifBlank { result.stdout }.ifBlank { "exit=${result.exitCode}" },
                AdbErrorKind.SHELL,
            )
        }
    }

    private companion object {
        const val SYSTEM_UI = "com.android.systemui"
        const val RESTART_ATTEMPTS = 6
        const val RESTART_PAUSE_MS = 400L
    }
}
