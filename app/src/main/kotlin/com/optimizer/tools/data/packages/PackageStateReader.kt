package com.optimizer.tools.data.packages

import com.optimizer.tools.domain.apk.PackageReadBack
import com.optimizer.tools.domain.profile.ProfileAppState
import com.optimizer.tools.domain.profile.ProfileStepKind
import dev.adboptimizer.core.model.AppSafety
import dev.adboptimizer.core.shell.AdbCommandRules
import dev.adboptimizer.core.shell.AdbShell
import dev.adboptimizer.core.shell.ShellQuote
import java.io.IOException

/**
 * Lectura y cambio del estado de paquetes por `pm`, compartido por perfiles y debloater.
 *
 * Cada cambio se confirma leyendo el estado de vuelta: un `pm` que responde 0 no basta,
 * porque Android aplica algunos cambios con retraso. Los paquetes criticos nunca se tocan.
 */
class PackageStateReader(
    private val shell: () -> AdbShell?,
) {
    fun available(): Boolean = shell() != null

    fun requireShell(): AdbShell = shell() ?: throw IOException("Conecta un dispositivo o autoriza Shizuku para administrar apps.")

    /** Estado de cada app conocida: instalada, deshabilitada o desinstalada para el usuario. */
    suspend fun states(): Map<String, ProfileAppState> {
        val adb = requireShell()
        val installed = packages(adb, "pm list packages")
        val disabled = packages(adb, "pm list packages -d")
        val known = packages(adb, "pm list packages -u") + installed
        return known.associateWith { packageName ->
            when {
                packageName !in installed -> ProfileAppState.UNINSTALLED
                packageName in disabled -> ProfileAppState.DISABLED
                else -> ProfileAppState.ENABLED
            }
        }
    }

    suspend fun execute(
        kind: ProfileStepKind,
        packageName: String,
    ) {
        val adb = requireShell()
        if (!AdbCommandRules.isPackageName(packageName)) throw IOException("Nombre de paquete inválido.")
        if (AppSafety.isCritical(packageName)) throw IOException("$packageName es un paquete crítico y no se modifica.")
        val response = adb.exec(commandFor(kind, packageName), COMMAND_TIMEOUT_MS)
        val output = response.output.trim()
        val rejected =
            response.exitCode != 0 ||
                output.contains("Failure", ignoreCase = true) ||
                output.contains("Error", ignoreCase = true) ||
                output.contains("Unknown package", ignoreCase = true)
        if (rejected) {
            throw IOException(output.ifBlank { "El dispositivo rechazó el cambio en $packageName." })
        }
        if (!readBackFor(kind, packageName).confirm(adb)) {
            throw IOException("El dispositivo no confirmó el cambio en $packageName.")
        }
    }

    private suspend fun packages(
        adb: AdbShell,
        command: String,
    ): Set<String> {
        val response = adb.exec(command, LIST_TIMEOUT_MS)
        if (response.exitCode != 0) {
            throw IOException(response.output.ifBlank { "El dispositivo rechazó «$command»." })
        }
        return response.stdout
            .lineSequence()
            .map { it.trim().removePrefix("package:") }
            .filter { it.isNotBlank() && !it.contains(' ') && AdbCommandRules.isPackageName(it) }
            .toSet()
    }

    private fun commandFor(
        kind: ProfileStepKind,
        packageName: String,
    ): String {
        val pkg = ShellQuote.of(packageName)
        return when (kind) {
            ProfileStepKind.REINSTALL -> "pm install-existing --user 0 $pkg"
            ProfileStepKind.ENABLE -> "pm enable --user 0 $pkg"
            ProfileStepKind.DISABLE -> "pm disable-user --user 0 $pkg"
            ProfileStepKind.UNINSTALL -> "pm uninstall --user 0 $pkg"
        }
    }

    private fun readBackFor(
        kind: ProfileStepKind,
        packageName: String,
    ): PackageReadBack {
        val pkg = ShellQuote.of(packageName)
        return when (kind) {
            ProfileStepKind.REINSTALL -> {
                PackageReadBack("pm list packages $pkg") { hasExactPackage(it, packageName) }
            }

            ProfileStepKind.ENABLE -> {
                PackageReadBack("pm list packages -e $pkg") { hasExactPackage(it, packageName) }
            }

            ProfileStepKind.DISABLE -> {
                PackageReadBack("pm list packages -d $pkg") { hasExactPackage(it, packageName) }
            }

            ProfileStepKind.UNINSTALL -> {
                PackageReadBack("pm list packages $pkg") { !hasExactPackage(it, packageName) }
            }
        }
    }

    private fun hasExactPackage(
        output: String,
        packageName: String,
    ): Boolean = output.lineSequence().any { it.trim() == "package:$packageName" }

    companion object {
        private const val LIST_TIMEOUT_MS = 30_000L
        private const val COMMAND_TIMEOUT_MS = 45_000L
    }
}
