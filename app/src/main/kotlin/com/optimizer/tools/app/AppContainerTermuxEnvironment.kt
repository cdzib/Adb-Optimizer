package com.optimizer.tools.app

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import com.optimizer.tools.data.AppContainer
import com.optimizer.tools.feature.termux.domain.TERMUX_RUN_COMMAND_PERMISSION
import com.optimizer.tools.feature.termux.presentation.TermuxEnvironment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

/** Adaptador de composición para la integración con Termux. */
internal class AppContainerTermuxEnvironment(
    private val container: AppContainer,
    private val context: Context,
) : TermuxEnvironment {
    override fun availability() = container.termux.availability()

    override suspend fun run(
        command: String,
        label: String,
    ) = container.termux.run(command, label)

    override suspend fun hasRoot(): Boolean = withContext(Dispatchers.IO) { runSu("id").contains("uid=0") }

    override suspend fun configureWithRoot(): Boolean =
        withContext(Dispatchers.IO) {
            val script =
                listOf(
                    "pm grant ${context.packageName} $TERMUX_RUN_COMMAND_PERMISSION",
                    ROOT_EXTERNAL_APPS_SCRIPT,
                    // Mejor esfuerzo: Termux recarga propiedades si su activity está visible.
                    "am broadcast --user 0 -a com.termux.app.reload_style com.termux >/dev/null 2>&1 || true",
                ).joinToString(" && ")
            runSu(script)
            if (!container.termux.availability().permissionGranted) return@withContext false
            // Verificación real: si Termux aún cachea allow-external-apps=false, esto falla.
            runCatching {
                container.termux.run("echo ready", "verificación de Termux", VERIFY_TIMEOUT_MS)
            }.isSuccess
        }

    override suspend fun grantPermission(): Boolean {
        if (!container.localPrivilegeReady()) return false
        val command = "pm grant ${context.packageName} $TERMUX_RUN_COMMAND_PERMISSION"
        runCatching { container.localShell.exec(command, GRANT_TIMEOUT_MS) }
        return container.termux.availability().permissionGranted
    }

    override fun configureExternalApps(command: String): Boolean {
        val clipboard = context.getSystemService(ClipboardManager::class.java)
        clipboard?.setPrimaryClip(ClipData.newPlainText("Termux", command))
        return openTermux()
    }

    override fun openTermux(): Boolean {
        val intent = context.packageManager.getLaunchIntentForPackage(TERMUX_PACKAGE) ?: return false
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return runCatching { context.startActivity(intent) }.isSuccess
    }

    override fun openInstallPage() {
        start(
            Intent(Intent.ACTION_VIEW, Uri.parse("https://f-droid.org/packages/$TERMUX_PACKAGE/")),
        )
    }

    override fun openAppSettings() {
        start(
            Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.fromParts("package", context.packageName, null),
            ),
        )
    }

    /**
     * Escribe allow-external-apps respetando el UID de Termux: un archivo propiedad de root
     * haría que Termux no pudiera leer sus propiedades. El `restorecon` evita etiquetas SELinux
     * heredadas del dominio de `su`.
     */
    private fun runSu(command: String): String =
        runCatching {
            val process =
                ProcessBuilder("su", "-c", command)
                    .redirectErrorStream(true)
                    .start()
            if (!process.waitFor(ROOT_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                process.destroy()
                return ""
            }
            process.inputStream.bufferedReader().use { it.readText() }
        }.getOrDefault("")

    private fun start(intent: Intent) {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }
    }

    private companion object {
        const val TERMUX_PACKAGE = "com.termux"
        const val TERMUX_HOME = "/data/data/com.termux/files/home"
        const val GRANT_TIMEOUT_MS = 15_000L
        const val ROOT_TIMEOUT_SECONDS = 30L
        const val VERIFY_TIMEOUT_MS = 20_000L

        val ROOT_EXTERNAL_APPS_SCRIPT =
            listOf(
                "mkdir -p $TERMUX_HOME/.termux",
                "grep -q '^allow-external-apps=true' $TERMUX_HOME/.termux/termux.properties 2>/dev/null || " +
                    "echo 'allow-external-apps=true' >> $TERMUX_HOME/.termux/termux.properties",
                "chown -R \$(stat -c %u:%g /data/data/com.termux) $TERMUX_HOME/.termux",
                "chmod 700 $TERMUX_HOME/.termux",
                "chmod 600 $TERMUX_HOME/.termux/termux.properties",
                "restorecon -R $TERMUX_HOME/.termux 2>/dev/null || true",
            ).joinToString(" && ")
    }
}
