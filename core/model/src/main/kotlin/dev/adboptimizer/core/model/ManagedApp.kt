package dev.adboptimizer.core.model

/** Origen sobre el que se administra una app. */
enum class AppTarget { REMOTE, LOCAL }

/** Filtros de la lista de apps; Usuario/Sistema son excluyentes entre si. */
enum class AppFilter { USER, SYSTEM, DISABLED }

enum class AppSort { NAME, SIZE, UPDATED, VERSION }

/** Acciones de administracion que entiende la UI. */
enum class AppAction {
    OPEN,
    FORCE_STOP,
    ENABLE,
    DISABLE,
    CLEAR_DATA,
    CLEAR_CACHE,
    UNINSTALL,
    EXTRACT_APK,
    APP_INFO,
}

/**
 * App instalada, ya sea en el dispositivo remoto (leida por ADB) o en el telefono
 * local (leida con PackageManager). Los campos desconocidos quedan en 0/null.
 */
data class ManagedApp(
    val packageName: String,
    val label: String = packageName,
    val versionName: String = "",
    val versionCode: Long = 0L,
    val minSdk: Int = 0,
    val targetSdk: Int = 0,
    val isSystem: Boolean = false,
    val enabled: Boolean = true,
    val sizeBytes: Long = 0L,
    val installedAt: Long = 0L,
    val updatedAt: Long = 0L,
    val hasSplits: Boolean = false,
    val apkPaths: List<String> = emptyList(),
    val installerPackage: String? = null,
    val requestedPermissions: List<String> = emptyList(),
) {
    val displayName: String get() = label.ifBlank { packageName }
}

data class AppActionResult(
    val success: Boolean,
    val message: String,
)

/** Paquetes que nunca deben desinstalarse/deshabilitarse/limpiarse desde la app. */
object AppSafety {
    val criticalPackages: Set<String> =
        setOf(
            "android",
            "com.android.systemui",
            "com.android.settings",
            "com.android.shell",
            "com.android.packageinstaller",
            "com.google.android.packageinstaller",
            "com.android.permissioncontroller",
            "com.google.android.permissioncontroller",
            "com.samsung.android.packageinstaller",
            "com.sec.android.app.launcher",
        )

    fun isCritical(packageName: String): Boolean =
        packageName in criticalPackages ||
            packageName == "com.optimizer.tools" ||
            packageName == "com.optimizer.tools.debug" ||
            packageName.startsWith("com.optimizer.tools.")
}
