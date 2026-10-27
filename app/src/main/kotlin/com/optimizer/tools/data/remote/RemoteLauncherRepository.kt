package com.optimizer.tools.data.remote

import dev.adboptimizer.core.model.RemoteLauncher
import dev.adboptimizer.core.shell.AdbCommandRules
import dev.adboptimizer.core.shell.AdbShell
import dev.adboptimizer.core.shell.ShellQuote
import java.io.IOException

class RemoteLauncherRepository(
    private val shell: AdbShell,
) {
    suspend fun listHomeLaunchers(): List<RemoteLauncher> {
        val result =
            shell.exec(
                "cmd package query-activities --brief --components --user 0 " +
                    "-a android.intent.action.MAIN -c android.intent.category.HOME",
                20_000,
            )
        if (result.exitCode != 0) {
            throw IOException(result.output.ifBlank { "Este Android no permite consultar los launchers instalados." })
        }
        return RemoteLauncherParser.parse(result.output)
    }

    suspend fun currentHomeLauncher(): RemoteLauncher? {
        val result =
            shell.exec(
                "cmd package resolve-activity --brief --user 0 " +
                    "-a android.intent.action.MAIN -c android.intent.category.HOME",
                20_000,
            )
        if (result.exitCode != 0) return null
        return RemoteLauncherParser.parse(result.output).firstOrNull()
    }

    suspend fun setDefaultHomeLauncher(packageName: String): RemoteLauncher {
        require(AdbCommandRules.isPackageName(packageName)) { "El paquete launcher no es válido." }
        val launcher =
            listHomeLaunchers().firstOrNull { it.packageName == packageName }
                ?: throw IOException("$packageName no está disponible como launcher de inicio en esta TV.")

        val result = shell.exec("cmd package set-home-activity --user 0 ${ShellQuote.of(packageName)}", 20_000)
        if (result.exitCode != 0 || result.output.contains("Error", ignoreCase = true)) {
            throw IOException(result.output.ifBlank { "Android rechazó el cambio de launcher." })
        }
        val current = currentHomeLauncher()
        if (current?.packageName != launcher.packageName) {
            throw IOException("Android no confirmó el cambio; el launcher actual sigue sin verificarse.")
        }
        return current
    }
}

internal object RemoteLauncherParser {
    private val componentPattern = Regex("^([A-Za-z0-9_]+(?:\\.[A-Za-z0-9_]+)*)/([A-Za-z0-9_.$]+)$")

    fun parse(output: String): List<RemoteLauncher> =
        output
            .lineSequence()
            .mapNotNull { line ->
                val component =
                    line
                        .trim()
                        .substringAfterLast("ComponentInfo{", line.trim())
                        .substringBefore('}')
                        .substringBefore(' ')
                val match = componentPattern.matchEntire(component) ?: return@mapNotNull null
                val packageName = match.groupValues[1]
                val activityName =
                    match.groupValues[2].let { name ->
                        if (name.startsWith('.')) packageName + name else name
                    }
                RemoteLauncher(packageName, "$packageName/$activityName")
            }.distinctBy(RemoteLauncher::packageName)
            .toList()
}
