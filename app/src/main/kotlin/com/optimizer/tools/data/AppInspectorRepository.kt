package com.optimizer.tools.data

import com.optimizer.tools.feature.inspector.AppInspection
import com.optimizer.tools.feature.inspector.AppInspectorParser
import dev.adboptimizer.core.shell.AdbCommandRules
import dev.adboptimizer.core.shell.AdbShell
import dev.adboptimizer.core.shell.ShellQuote

/** Inventario de permisos, AppOps, componentes y firma de una app instalada. */
class AppInspectorRepository(
    private val shell: AdbShell,
) {
    suspend fun inspect(packageName: String): AppInspection {
        require(AdbCommandRules.isPackageName(packageName)) { "Nombre de paquete inválido." }
        val dump = shell.exec("dumpsys package ${ShellQuote.of(packageName)}", 30_000)
        if (dump.exitCode != 0 && dump.stdout.isBlank()) {
            throw IllegalStateException(dump.output.ifBlank { "El dispositivo no devolvió el detalle." })
        }
        val ops = runCatching { shell.exec("appops get ${ShellQuote.of(packageName)}", 20_000).stdout }.getOrDefault("")
        return AppInspection(
            permissions = AppInspectorParser.permissions(dump.stdout),
            appOps = AppInspectorParser.appOps(ops),
            components = AppInspectorParser.components(dump.stdout, packageName),
            signature = AppInspectorParser.signature(dump.stdout),
        )
    }
}
