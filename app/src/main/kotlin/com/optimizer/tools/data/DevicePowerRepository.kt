package com.optimizer.tools.data

import com.optimizer.tools.domain.model.DevicePowerAction
import dev.adboptimizer.core.shell.AdbErrorKind
import dev.adboptimizer.core.shell.AdbException
import dev.adboptimizer.core.shell.AdbShell

/** One request, no retry: a reboot can close its own transport before replying. */
class DevicePowerRepository(
    private val shell: AdbShell,
) {
    suspend fun execute(action: DevicePowerAction) {
        val command =
            when (action) {
                DevicePowerAction.REBOOT -> "svc power reboot"
                DevicePowerAction.SHUTDOWN -> "svc power shutdown"
                DevicePowerAction.RECOVERY -> "svc power reboot recovery"
            }
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
}
