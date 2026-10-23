package com.optimizer.tools.data

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import com.optimizer.tools.domain.apk.ApkDescriptor
import com.optimizer.tools.domain.apk.LocalPackageCommands
import com.optimizer.tools.domain.model.ShizukuLocalStatus
import com.optimizer.tools.domain.port.HostAppsSource
import dev.adboptimizer.core.model.AppAction
import dev.adboptimizer.core.model.AppActionResult
import dev.adboptimizer.core.model.ManagedApp
import dev.adboptimizer.core.shell.AdbShell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

/**
 * Administracion del telefono donde corre la app.
 *
 * Usa PackageManager para inventariar el telefono y Shizuku UserService para
 * acciones de shell autorizadas. Sin permiso Shizuku, conserva operaciones de UI
 * mediadas por el sistema y explica cuando una accion privilegiada no esta disponible.
 */
class LocalAppRepository(
    private val context: Context,
    private val shizukuAccess: ShizukuLocalAccess,
    private val shell: AdbShell,
) : HostAppsSource {
    private val pm: PackageManager get() = context.packageManager

    override suspend fun list(includeSystem: Boolean): List<ManagedApp> =
        withContext(Dispatchers.IO) {
            installedApplications()
                .asSequence()
                .mapNotNull { info -> runCatching { info.toManagedApp() }.getOrNull() }
                .filter { includeSystem || !it.isSystem }
                .sortedBy { it.displayName.lowercase() }
                .toList()
        }

    suspend fun details(packageName: String): ManagedApp? =
        withContext(Dispatchers.IO) {
            runCatching {
                val info = applicationInfo(packageName) ?: return@runCatching null
                info.toManagedApp()
            }.getOrNull()
        }

    override suspend fun stageApks(packageName: String): List<ApkDescriptor> =
        withContext(Dispatchers.IO) {
            val info = applicationInfo(packageName) ?: throw IOException("No se encontró $packageName en este teléfono.")
            val baseSource =
                info.sourceDir?.takeIf(String::isNotBlank)
                    ?: throw IOException("No se encontró el APK base de $packageName.")
            val splitSources = info.splitSourceDirs?.filter(String::isNotBlank).orEmpty()
            val splitNames = info.splitNames?.toList().orEmpty()
            if (splitSources.isNotEmpty() && splitNames.size != splitSources.size) {
                throw IOException("Android no proporcionó los nombres de todos los APK divididos de $packageName.")
            }
            val sources = listOf(baseSource) + splitSources

            val stagedApks = mutableListOf<ApkDescriptor>()
            try {
                sources.forEachIndexed { index, source ->
                    val staged = File.createTempFile("adb-host-$index-", ".apk", context.cacheDir)
                    val splitName = if (index == 0) null else splitNames[index - 1]
                    stagedApks += ApkDescriptor(staged, packageName, splitName)
                    File(source).inputStream().use { input -> staged.outputStream().use(input::copyTo) }
                    if (staged.length() == 0L) throw IOException("El APK de $packageName está vacío.")
                }
                stagedApks
            } catch (error: Exception) {
                stagedApks.forEach { it.file.delete() }
                throw IOException("No se pudieron leer los APK instalados de $packageName: ${error.message}", error)
            }
        }

    suspend fun run(
        packageName: String,
        action: AppAction,
    ): AppActionResult =
        withContext(Dispatchers.IO) {
            if (!LocalPackageCommands.isValidPackageName(packageName)) {
                return@withContext AppActionResult(false, "Nombre de paquete inválido.")
            }
            when (action) {
                AppAction.OPEN -> {
                    open(packageName)
                }

                AppAction.APP_INFO -> {
                    openSettings(packageName)
                }

                AppAction.EXTRACT_APK -> {
                    extract(packageName)
                }

                else -> {
                    val command =
                        LocalPackageCommands.create(packageName, action, Build.VERSION.SDK_INT)
                            ?: return@withContext AppActionResult(
                                false,
                                if (action == AppAction.CLEAR_CACHE && Build.VERSION.SDK_INT < 30) {
                                    "Limpiar caché requiere Android 11 o posterior."
                                } else {
                                    "Esta acción no está disponible en el teléfono."
                                },
                            )
                    if (action == AppAction.UNINSTALL && shizukuAccess.status() != ShizukuLocalStatus.READY) {
                        return@withContext uninstallWithSystemUi(packageName)
                    }
                    runPrivileged(command.command, command.timeoutMs, command.successMessage, command.successToken)
                }
            }
        }

    private suspend fun runPrivileged(
        command: String,
        timeoutMs: Long,
        successMessage: String,
        successToken: String? = null,
    ): AppActionResult =
        runCatching {
            val response = shell.exec(command, timeoutMs)
            val output = response.output.trim()
            val success =
                response.exitCode == 0 &&
                    (successToken == null || output.contains(successToken, ignoreCase = true)) &&
                    !output.contains("Failure", ignoreCase = true) &&
                    !output.contains("Error", ignoreCase = true)
            if (success) {
                AppActionResult(true, successMessage)
            } else {
                AppActionResult(false, output.ifBlank { "El sistema rechazó la acción (código ${response.exitCode})." })
            }
        }.getOrElse { error ->
            AppActionResult(false, error.message ?: "No se pudo ejecutar la acción con Shizuku.")
        }

    private fun open(packageName: String): AppActionResult {
        val intent =
            pm.getLaunchIntentForPackage(packageName)
                ?: return AppActionResult(false, "La app no tiene una activity de inicio.")
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return runCatching {
            context.startActivity(intent)
            AppActionResult(true, "App abierta.")
        }.getOrElse { AppActionResult(false, it.message ?: "No se pudo abrir.") }
    }

    private fun openSettings(packageName: String): AppActionResult {
        val intent =
            Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                .setData(Uri.parse("package:$packageName"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return runCatching {
            context.startActivity(intent)
            AppActionResult(true, "Informacion de la app abierta.")
        }.getOrElse { AppActionResult(false, it.message ?: "No se pudo abrir Ajustes.") }
    }

    private fun uninstallWithSystemUi(packageName: String): AppActionResult {
        val intent =
            Intent(Intent.ACTION_DELETE)
                .setData(Uri.parse("package:$packageName"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return runCatching {
            context.startActivity(intent)
            AppActionResult(true, "Se abrio el desinstalador del sistema.")
        }.getOrElse { AppActionResult(false, it.message ?: "No se pudo abrir el desinstalador.") }
    }

    private fun extract(packageName: String): AppActionResult {
        val info =
            applicationInfo(packageName)
                ?: return AppActionResult(false, "No se encontro la app.")
        val sources =
            buildList {
                info.sourceDir?.takeIf { it.isNotBlank() }?.let(::add)
                info.splitSourceDirs?.filter { it.isNotBlank() }?.let(::addAll)
            }
        if (sources.isEmpty()) return AppActionResult(false, "No se encontraron APKs.")

        val root =
            context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
                ?: context.filesDir
        val targetDir = File(root, "AdbOptimizer/${packageName.replace(Regex("[^A-Za-z0-9._-]"), "_")}")
        if (!targetDir.mkdirs() && !targetDir.isDirectory) {
            return AppActionResult(false, "No se pudo crear ${targetDir.absolutePath}.")
        }
        return runCatching {
            sources.forEach { source ->
                val name = source.substringAfterLast('/').ifBlank { "base.apk" }
                File(source).copyTo(File(targetDir, name), overwrite = true)
            }
            AppActionResult(true, "APK copiado a ${targetDir.absolutePath}.")
        }.getOrElse { AppActionResult(false, it.message ?: "No se pudo copiar el APK.") }
    }

    private fun ApplicationInfo.toManagedApp(): ManagedApp {
        val packageInfo = packageInfo(packageName)
        val paths =
            buildList {
                sourceDir?.takeIf { it.isNotBlank() }?.let(::add)
                splitSourceDirs?.filter { it.isNotBlank() }?.let(::addAll)
            }
        return ManagedApp(
            packageName = packageName,
            label = runCatching { loadLabel(pm).toString() }.getOrDefault(packageName),
            versionName = packageInfo?.versionName.orEmpty(),
            versionCode =
                packageInfo?.let {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) it.longVersionCode else it.versionCode.toLong()
                } ?: 0L,
            minSdk = packageInfo?.applicationInfo?.minSdkVersion ?: 0,
            targetSdk = targetSdkVersion,
            isSystem = (flags and ApplicationInfo.FLAG_SYSTEM) != 0,
            enabled = enabled,
            sizeBytes = paths.sumOf { runCatching { File(it).length() }.getOrDefault(0L) },
            installedAt = packageInfo?.firstInstallTime ?: 0L,
            updatedAt = packageInfo?.lastUpdateTime ?: 0L,
            hasSplits = paths.size > 1,
            apkPaths = paths,
            installerPackage =
                runCatching {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        pm.getInstallSourceInfo(packageName).installingPackageName
                    } else {
                        @Suppress("DEPRECATION")
                        pm.getInstallerPackageName(packageName)
                    }
                }.getOrNull(),
            requestedPermissions = packageInfo?.requestedPermissions?.toList().orEmpty(),
        )
    }

    @Suppress("DEPRECATION")
    private fun installedApplications(): List<ApplicationInfo> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.getInstalledApplications(PackageManager.ApplicationInfoFlags.of(0))
        } else {
            pm.getInstalledApplications(0)
        }

    @Suppress("DEPRECATION")
    private fun applicationInfo(packageName: String): ApplicationInfo? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.getApplicationInfo(packageName, PackageManager.ApplicationInfoFlags.of(0))
        } else {
            pm.getApplicationInfo(packageName, 0)
        }

    @Suppress("DEPRECATION")
    private fun packageInfo(packageName: String): PackageInfo? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.getPackageInfo(
                packageName,
                PackageManager.PackageInfoFlags.of(PackageManager.GET_PERMISSIONS.toLong()),
            )
        } else {
            pm.getPackageInfo(packageName, PackageManager.GET_PERMISSIONS)
        }
}
