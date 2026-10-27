package com.optimizer.tools.domain.apk

import dev.adboptimizer.core.model.AppAction
import dev.adboptimizer.core.model.AppSafety
import dev.adboptimizer.core.shell.AdbCommandRules
import dev.adboptimizer.core.shell.ShellQuote

/** A package-scoped command whose package argument has passed strict validation. */
data class LocalPackageCommand(
    val command: String,
    val successMessage: String,
    val timeoutMs: Long = 20_000L,
    val successToken: String? = null,
    /** Lectura con la que se confirma que el cambio quedo aplicado en el dispositivo. */
    val readBack: PackageReadBack? = null,
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

            AppAction.COMPILE_AOT -> {
                if (AppSafety.isCritical(packageName)) return null
                val command = AdbCommandRules.compileCommand(packageName, "speed", force = true) ?: return null
                LocalPackageCommand(
                    command,
                    "App compilada para arranque rápido.",
                    120_000L,
                    "Success",
                    PackageReadBack.aot(packageName),
                )
            }

            AppAction.RESTRICT_STANDBY -> {
                if (AppSafety.isCritical(packageName)) return null
                val command = AdbCommandRules.standbyBucketCommand(packageName, "restricted") ?: return null
                LocalPackageCommand(
                    command,
                    "App restringida en segundo plano. Se revierte en Ajustes > Batería.",
                    10_000L,
                    readBack = PackageReadBack.standby(packageName),
                )
            }

            AppAction.RESTRICT_BACKGROUND -> {
                if (AppSafety.isCritical(packageName)) return null
                val command = AdbCommandRules.appOpsCommand(packageName, "RUN_IN_BACKGROUND", "ignore") ?: return null
                LocalPackageCommand(
                    command,
                    "Ejecución en segundo plano bloqueada. Se revierte en Ajustes > Batería.",
                    10_000L,
                    readBack = PackageReadBack.backgroundOp(packageName),
                )
            }

            AppAction.OPEN, AppAction.EXTRACT_APK, AppAction.APP_INFO -> {
                null
            }
        }
    }

    fun isValidPackageName(packageName: String): Boolean = AdbCommandRules.isPackageName(packageName)
}
