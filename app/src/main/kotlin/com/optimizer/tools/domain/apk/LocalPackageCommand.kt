package com.optimizer.tools.domain.apk

import dev.adboptimizer.core.model.AppAction

/** A package-scoped command whose package argument has passed strict validation. */
data class LocalPackageCommand(
    val command: String,
    val successMessage: String,
    val timeoutMs: Long = 20_000L,
    val successToken: String? = null,
)

/** Pure command policy shared by the local repository and unit tests. */
object LocalPackageCommands {
    private val packageNamePattern = Regex("[A-Za-z0-9_]+(?:\\.[A-Za-z0-9_]+)+")

    fun create(
        packageName: String,
        action: AppAction,
        sdk: Int,
    ): LocalPackageCommand? {
        require(packageNamePattern.matches(packageName)) { "Nombre de paquete inválido." }
        val pkg = "'${packageName.replace("'", "'\\''")}'"
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

    fun isValidPackageName(packageName: String): Boolean = packageNamePattern.matches(packageName)
}
