package com.optimizer.tools.data.remote

import dev.adboptimizer.core.shell.AdbShell
import kotlinx.coroutines.CancellationException

data class RemoteInputCapabilities(
    val inputPermissionGranted: Boolean?,
    val hasTouchscreen: Boolean?,
)

/** Permission/feature checks are hints, not proof that a remote app accepts input. */
class RemoteInputCapabilitiesRepository(
    private val shell: AdbShell,
) {
    suspend fun readCapabilities(): RemoteInputCapabilities {
        suspend fun query(command: String): String? =
            try {
                shell
                    .exec(command, 10_000)
                    .takeIf { it.exitCode == 0 }
                    ?.stdout
                    ?.trim()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                null
            }
        val permissionOutput = query("dumpsys package com.android.shell | grep 'android.permission.INJECT_EVENTS:'")
        val permission =
            when (
                Regex("android\\.permission\\.INJECT_EVENTS:\\s+granted=(true|false)(?:,|\\s|$)")
                    .find(permissionOutput.orEmpty())
                    ?.groupValues
                    ?.get(1)
            ) {
                "true" -> true
                "false" -> false
                else -> null
            }
        val features = query("pm list features")
        val hasTouchscreen =
            features?.takeIf { it.contains("feature:") }?.lineSequence()?.any {
                it.trim() == "feature:android.hardware.touchscreen"
            }
        return RemoteInputCapabilities(permission, hasTouchscreen)
    }
}
