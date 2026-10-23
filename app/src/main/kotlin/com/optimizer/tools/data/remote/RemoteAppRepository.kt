package com.optimizer.tools.data.remote

import dev.adboptimizer.core.model.AppAction
import dev.adboptimizer.core.model.AppActionResult
import dev.adboptimizer.core.model.ManagedApp
import dev.adboptimizer.core.shell.AdbShell
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * Administracion de apps del dispositivo conectado usando solo ADB shell (uid 2000).
 *
 * No usa PackageManager ni Shizuku: el objetivo es un TV/telefono remoto. La etiqueta
 * visible no esta disponible por shell, por eso se usa el nombre del paquete como
 * fallback; el detalle si trae version, SDK, fechas, tamano y permisos.
 */
class RemoteAppRepository(
    private val shell: AdbShell,
) {
    suspend fun list(includeSystem: Boolean): List<ManagedApp> {
        val user = parsePackageList(shell.exec("pm list packages -3 -f --show-versioncode", 30_000).stdout)
        val system =
            if (includeSystem) {
                parsePackageList(shell.exec("pm list packages -s -f --show-versioncode", 45_000).stdout)
            } else {
                emptyList()
            }
        val disabled =
            shell
                .exec("pm list packages -d", 20_000)
                .stdout
                .lineSequence()
                .mapNotNull { it.trim().removePrefix("package:").takeIf(String::isNotBlank) }
                .toSet()

        val systemNames = system.map { it.packageName }.toSet()
        val merged =
            (user + system)
                .groupBy { it.packageName }
                .map { (_, entries) ->
                    val base = entries.firstOrNull { it.packageName !in systemNames } ?: entries.first()
                    base.copy(
                        isSystem = entries.any { it.isSystem } || base.packageName in systemNames,
                        enabled = base.packageName !in disabled,
                    )
                }.sortedBy { it.packageName.lowercase() }

        val sizes = readStat(merged.flatMap { it.apkPaths }.distinct())
        return merged.map { app ->
            val meta = app.apkPaths.firstOrNull()?.let { sizes[it] }
            app.copy(
                sizeBytes = meta?.sizeBytes ?: 0L,
                updatedAt = meta?.modifiedAt ?: 0L,
            )
        }
    }

    suspend fun details(packageName: String): ManagedApp? {
        val result = shell.exec("dumpsys package ${shQuote(packageName)}", 30_000)
        val raw = result.stdout
        if (raw.isBlank()) return null

        val paths =
            shell
                .exec("pm path ${shQuote(packageName)}", 15_000)
                .stdout
                .lineSequence()
                .mapNotNull { it.trim().removePrefix("package:").takeIf(String::isNotBlank) }
                .toList()
        val sizes = readStat(paths)

        val versionLine =
            Regex("""versionCode=(\d+)\s+minSdk=(\d+)\s+targetSdk=(\d+)""")
                .find(raw)
                ?.groupValues
        val flags =
            Regex("""flags=\[([^]]*)]""")
                .find(raw)
                ?.groupValues
                ?.get(1)
                .orEmpty()
        val userLine =
            Regex("""User 0:.*?enabled=(\d+)""")
                .find(raw)
                ?.groupValues
                ?.get(1)
                ?.toIntOrNull() ?: 0
        val splits =
            Regex("""splits=\[([^]]*)]""")
                .find(raw)
                ?.groupValues
                ?.get(1)
                ?.split(',')
                ?.map { it.trim() }
                ?.filter { it.isNotEmpty() }
                .orEmpty()

        return ManagedApp(
            packageName = packageName,
            label = packageName,
            versionName =
                Regex("""versionName=(.+)""")
                    .find(raw)
                    ?.groupValues
                    ?.get(1)
                    ?.trim()
                    .orEmpty(),
            versionCode = versionLine?.getOrNull(1)?.toLongOrNull() ?: 0L,
            minSdk = versionLine?.getOrNull(2)?.toIntOrNull() ?: 0,
            targetSdk = versionLine?.getOrNull(3)?.toIntOrNull() ?: 0,
            isSystem = flags.contains("SYSTEM"),
            enabled = userLine in 0..1,
            sizeBytes = paths.sumOf { sizes[it]?.sizeBytes ?: 0L },
            installedAt = parseDate(raw, "firstInstallTime"),
            updatedAt = parseDate(raw, "lastUpdateTime"),
            hasSplits = splits.size > 1 || paths.size > 1,
            apkPaths = paths,
            installerPackage =
                Regex("""installerPackageName=(\S+)""")
                    .find(raw)
                    ?.groupValues
                    ?.get(1)
                    ?.takeUnless { it == "null" },
            requestedPermissions = requestedPermissions(raw),
        )
    }

    suspend fun run(
        packageName: String,
        action: AppAction,
    ): AppActionResult {
        if (packageName.isBlank()) return AppActionResult(false, "Paquete vacio.")
        val result =
            when (action) {
                AppAction.OPEN -> {
                    val output = exec("monkey -p ${shQuote(packageName)} -c android.intent.category.LAUNCHER 1", 20_000)
                    if (output.contains("No activities found", true) ||
                        output.contains("monkey aborted", true)
                    ) {
                        AppActionResult(false, "La app no tiene una activity de inicio.")
                    } else {
                        AppActionResult(true, "App abierta.")
                    }
                }

                AppAction.FORCE_STOP -> {
                    execResult("am force-stop ${shQuote(packageName)}", "App detenida.")
                }

                AppAction.ENABLE -> {
                    execResult("pm enable ${shQuote(packageName)}", "App habilitada.")
                }

                AppAction.DISABLE -> {
                    execResult("pm disable-user --user 0 ${shQuote(packageName)}", "App deshabilitada.")
                }

                AppAction.CLEAR_DATA -> {
                    execResult("pm clear ${shQuote(packageName)}", "Datos borrados.")
                }

                AppAction.CLEAR_CACHE -> {
                    execResult(
                        "pm clear --cache-only ${shQuote(packageName)}",
                        "Cache borrada.",
                    )
                }

                AppAction.UNINSTALL -> {
                    val output = exec("pm uninstall --user 0 ${shQuote(packageName)}", 45_000)
                    if (output.contains("Success", true)) {
                        AppActionResult(true, "App desinstalada.")
                    } else {
                        AppActionResult(false, output.ifBlank { "El dispositivo rechazo la desinstalacion." })
                    }
                }

                AppAction.EXTRACT_APK -> {
                    extract(packageName)
                }

                AppAction.APP_INFO -> {
                    AppActionResult(true, "Detalle cargado.")
                }
            }
        return result
    }

    private suspend fun extract(packageName: String): AppActionResult {
        val paths =
            shell
                .exec("pm path ${shQuote(packageName)}", 15_000)
                .stdout
                .lineSequence()
                .mapNotNull { it.trim().removePrefix("package:").takeIf(String::isNotBlank) }
                .toList()
        if (paths.isEmpty()) return AppActionResult(false, "No se encontro el APK en el dispositivo.")

        val safePackage = packageName.replace(Regex("""[^A-Za-z0-9._-]"""), "_")
        val targetDir = "/sdcard/Download/AdbOptimizer/$safePackage"
        val mkdir = shell.exec("mkdir -p ${shQuote(targetDir)}", 10_000)
        if (mkdir.exitCode != 0) {
            return AppActionResult(false, "No se pudo crear $targetDir.")
        }

        val failures = mutableListOf<String>()
        paths.forEachIndexed { index, path ->
            val name = if (paths.size == 1) "base.apk" else path.substringAfterLast('/')
            val target = "$targetDir/" + name.ifBlank { "split_$index.apk" }
            val copy = shell.exec("cp ${shQuote(path)} ${shQuote(target)}", 30_000)
            if (copy.exitCode != 0) failures += name
        }
        return if (failures.isEmpty()) {
            AppActionResult(true, "APK copiado a $targetDir en el dispositivo.")
        } else {
            AppActionResult(false, "No se pudieron copiar: ${failures.joinToString()}.")
        }
    }

    private suspend fun execResult(
        command: String,
        successMessage: String,
    ): AppActionResult {
        val output = exec(command, 30_000)
        return if (output.contains("Failure", true) || output.contains("Error", true)) {
            AppActionResult(false, output)
        } else {
            AppActionResult(true, successMessage)
        }
    }

    private suspend fun exec(
        command: String,
        timeoutMs: Long,
    ): String {
        val result = shell.exec(command, timeoutMs)
        return result.stdout.ifBlank { result.stderr }.trim()
    }

    private fun parsePackageList(raw: String): List<ManagedApp> =
        raw
            .lineSequence()
            .mapNotNull { line ->
                val text = line.trim()
                if (!text.startsWith("package:")) return@mapNotNull null
                val body = text.removePrefix("package:")
                val versionCode =
                    Regex("""\s+versionCode:(\d+)$""")
                        .find(body)
                        ?.groupValues
                        ?.get(1)
                        ?.toLongOrNull() ?: 0L
                val withoutVersion = body.replace(Regex("""\s+versionCode:\d+$"""), "")
                val separator = withoutVersion.lastIndexOf('=')
                if (separator <= 0 || separator >= withoutVersion.lastIndex) return@mapNotNull null
                val path = withoutVersion.substring(0, separator)
                val packageName = withoutVersion.substring(separator + 1)
                ManagedApp(
                    packageName = packageName,
                    versionCode = versionCode,
                    apkPaths = listOf(path),
                    isSystem = false,
                )
            }.distinctBy { it.packageName }
            .toList()

    private data class StatInfo(
        val sizeBytes: Long,
        val modifiedAt: Long,
    )

    private suspend fun readStat(paths: List<String>): Map<String, StatInfo> {
        if (paths.isEmpty()) return emptyMap()
        val result = mutableMapOf<String, StatInfo>()
        paths.chunked(80).forEach { chunk ->
            val command = "stat -c '%n|%s|%Y' " + chunk.joinToString(" ") { shQuote(it) }
            shell.exec(command, 20_000).stdout.lineSequence().forEach { line ->
                val parts = line.trim().split('|')
                if (parts.size >= 3) {
                    val size = parts[1].toLongOrNull() ?: 0L
                    val seconds = parts[2].toLongOrNull() ?: 0L
                    result[parts[0]] = StatInfo(size, seconds * 1_000)
                }
            }
        }
        return result
    }

    private fun requestedPermissions(raw: String): List<String> {
        val permissions = mutableListOf<String>()
        var inSection = false
        raw.lineSequence().forEach { line ->
            val trimmed = line.trim()
            when {
                trimmed.equals("requested permissions:", ignoreCase = true) -> {
                    inSection = true
                }

                inSection && (
                    trimmed.startsWith("install permissions:") ||
                        trimmed.startsWith("User 0:") ||
                        trimmed.startsWith("runtime permissions:")
                ) -> {
                    inSection = false
                }

                inSection && trimmed.isNotEmpty() -> {
                    val permission = trimmed.substringBefore(':').trim()
                    if (permission.startsWith("android.permission.") ||
                        permission.startsWith("com.") ||
                        permission.startsWith("androidx.")
                    ) {
                        permissions += permission
                    }
                }
            }
        }
        return permissions.distinct().take(80)
    }

    private fun parseDate(
        raw: String,
        key: String,
    ): Long {
        val value =
            Regex("""$key=(.+)""")
                .find(raw)
                ?.groupValues
                ?.get(1)
                ?.trim() ?: return 0L
        return runCatching {
            SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).parse(value)?.time ?: 0L
        }.getOrDefault(0L)
    }

    private fun shQuote(value: String): String = "'" + value.replace("'", "'\\''") + "'"
}
