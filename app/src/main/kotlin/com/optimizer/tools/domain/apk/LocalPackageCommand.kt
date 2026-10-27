package com.optimizer.tools.domain.apk

import dev.adboptimizer.core.model.AppAction
import dev.adboptimizer.core.shell.AdbCommandRules
import dev.adboptimizer.core.shell.ShellQuote

/** A package-scoped command whose package argument has passed strict validation. */
data class LocalPackageCommand(
    val command: String,
    val successMessage: String,
    val timeoutMs: Long = 20_000L,
    val successToken: String? = null,
)

/** Pure command policy shared by the local repository and unit tests. */
object LocalPackageCommands {
    fun create(
        packageName: String,
        action: AppAction,
        sdk: Int,
    ): LocalPackageCommand? {
        require(AdbCommandRules.isPackageName(packageName)) { "Nombre de paquete inválido." }
        val pkg = ShellQuote.of(packageName)
        return when (action) {
            AppAction.FORCE_STOP -> {
                LocalPackageCommand("am force-stop $pkg", "App detenida.", 15_000L)
            }

            AppAction.ENABLE -> {
                LocalPackageCommand("pm enable --user 0 $pkg", "App habilitada.")
            }

            AppAction.DISABLE -> {
                LocalPackageCommand("pm disable-user --user 0 $pkg", "App deshabilitada.")
            }

            AppAction.CLEAR_DATA -> {
                LocalPackageCommand("pm clear $pkg", "Datos borrados.", 30_000L, "Success")
            }

            AppAction.CLEAR_CACHE -> {
                if (sdk >= 30) {
                    LocalPackageCommand("pm clear --cache-only $pkg", "Caché borrada.", 30_000L, "Success")
                } else {
                    null
                }
            }

            AppAction.UNINSTALL -> {
                LocalPackageCommand(
                    "pm uninstall --user 0 $pkg",
                    "App desinstalada para este usuario.",
                    45_000L,
                    "Success",
                )
            }

            AppAction.OPEN, AppAction.EXTRACT_APK, AppAction.APP_INFO -> {
                null
            }
        }
    }

    fun isValidPackageName(packageName: String): Boolean = AdbCommandRules.isPackageName(packageName)
}
