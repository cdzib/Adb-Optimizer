package com.optimizer.tools.data.dpc

import com.optimizer.tools.domain.dpc.DpcMode
import com.optimizer.tools.domain.dpc.DpcParsers
import com.optimizer.tools.domain.dpc.DpcPreflight
import com.optimizer.tools.domain.dpc.DpcPreflightEvaluator
import com.optimizer.tools.domain.dpc.DpcReceiver
import com.optimizer.tools.domain.dpc.DpcResult
import dev.adboptimizer.core.shell.AdbShell
import java.io.IOException

private const val ADMIN_ACTION = "android.app.action.DEVICE_ADMIN_ENABLED"
private const val DPM_TIMEOUT_MS = 60_000L

/** Aprovisionamiento DPC sobre el dispositivo remoto. */
class DpcRepository(
    private val shell: AdbShell,
) {
    suspend fun preflight(mode: DpcMode): DpcPreflight {
        val ownersRaw =
            read("dpm list-owners", 20_000)
                .ifBlank { read("dumpsys device_policy", 25_000) }
        val usersRaw = read("pm list users", 15_000)
        val accountsRaw = read("dumpsys account", 30_000)
        val sdk = read("getprop ro.build.version.sdk", 8_000).trim().toIntOrNull() ?: 0

        val owners = DpcParsers.parseOwners(ownersRaw)
        val users = DpcParsers.parseUsers(usersRaw)
        val accounts = DpcParsers.countAccounts(accountsRaw)
        val (checks, ready) = DpcPreflightEvaluator.evaluate(sdk, owners, users, accounts, mode)

        return DpcPreflight(
            sdk = sdk,
            owners = owners,
            users = users,
            accounts = accounts,
            checks = checks,
            ready = ready,
        )
    }

    suspend fun receivers(packageName: String? = null): List<DpcReceiver> {
        val raw = read("cmd package query-receivers --brief -a $ADMIN_ACTION", 25_000)
        val all = DpcParsers.parseReceivers(raw)
        return if (packageName.isNullOrBlank()) all else all.filter { it.packageName == packageName }
    }

    suspend fun setOwner(
        component: String,
        mode: DpcMode,
    ): DpcResult {
        require(component.matches(COMPONENT_PATTERN)) { "El componente del DPC no es válido." }
        val command =
            when (mode) {
                DpcMode.DEVICE -> "dpm set-device-owner --user 0 $component"
                DpcMode.PROFILE -> "dpm set-profile-owner --user 0 $component"
            }
        return run(command)
    }

    /** Solo funciona si el DPC lo permite (p. ej. apps testOnly o con allowRemoval). */
    suspend fun removeOwner(component: String): DpcResult {
        require(component.matches(COMPONENT_PATTERN)) { "El componente del DPC no es válido." }
        return run("dpm remove-active-admin --user 0 $component")
    }

    private suspend fun run(command: String): DpcResult {
        val result =
            runCatching { shell.exec(command, DPM_TIMEOUT_MS) }
                .getOrElse { return DpcResult(false, it.message ?: "Error al ejecutar el comando.") }
        val output = result.stdout.ifBlank { result.stderr }.trim()
        val ok =
            result.exitCode == 0 &&
                output.contains("Success", ignoreCase = true) &&
                !output.contains("Failure", ignoreCase = true)
        return DpcResult(ok, output.ifBlank { "sin respuesta del dispositivo" })
    }

    private suspend fun read(
        command: String,
        timeoutMs: Long,
    ): String =
        runCatching {
            shell.exec(command, timeoutMs).let { it.stdout.ifBlank { it.stderr } }
        }.getOrDefault("")

    private companion object {
        val COMPONENT_PATTERN = Regex("""[A-Za-z0-9_.]+/[A-Za-z0-9_.$]+""")
    }

    class Unavailable : IOException("No hay un dispositivo remoto conectado.")
}
