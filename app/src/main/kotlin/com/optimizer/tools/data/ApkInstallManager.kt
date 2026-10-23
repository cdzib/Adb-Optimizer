package com.optimizer.tools.data

import android.content.Context
import dev.adboptimizer.core.adb.RemoteApkInstallOutcome
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ApkInstallState(
    val running: Boolean = false,
    val progress: String? = null,
    val message: String? = null,
)

/**
 * Ejecuta instalaciones fuera del ViewModel y mantiene el proceso en primer plano
 * mediante [AdbSessionService] para que cerrar la UI no cancele la transferencia.
 */
class ApkInstallManager(
    private val context: Context,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _state = MutableStateFlow(ApkInstallState())
    val state: StateFlow<ApkInstallState> = _state.asStateFlow()

    fun isRunning(): Boolean = _state.value.running

    fun start(operation: suspend ((String) -> Unit) -> List<RemoteApkInstallOutcome>) {
        if (isRunning()) return
        _state.value = ApkInstallState(running = true, progress = "Preparando archivos…")
        AdbSessionService.ensureStarted(context)
        AdbSessionService.updateProgress("Preparando archivos…")
        scope.launch {
            try {
                val outcomes =
                    operation { progress ->
                        _state.update { it.copy(progress = progress) }
                        AdbSessionService.updateProgress(progress)
                    }
                _state.update {
                    it.copy(
                        running = false,
                        progress = null,
                        message = summarize(outcomes),
                    )
                }
            } catch (cancelled: CancellationException) {
                _state.update { it.copy(running = false, progress = null) }
                throw cancelled
            } catch (error: Exception) {
                _state.update {
                    it.copy(
                        running = false,
                        progress = null,
                        message = error.message ?: "No se pudieron instalar los APK.",
                    )
                }
            } finally {
                AdbSessionService.clearProgress()
            }
        }
    }

    private fun summarize(outcomes: List<RemoteApkInstallOutcome>): String {
        val installed = outcomes.filter { it.success }.map { it.packageName }
        val failures = outcomes.filterNot { it.success }
        return buildString {
            if (installed.isNotEmpty()) append("Instaladas: ${installed.joinToString()}")
            if (failures.isNotEmpty()) {
                if (isNotEmpty()) append("\n")
                append(failures.joinToString("\n") { "${it.packageName}: ${it.output}" })
            }
            if (isEmpty()) append("No se completó ninguna instalación.")
        }
    }
}
