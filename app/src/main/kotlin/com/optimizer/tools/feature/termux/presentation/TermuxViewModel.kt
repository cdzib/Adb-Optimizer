package com.optimizer.tools.feature.termux.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.optimizer.tools.feature.termux.domain.TermuxAvailability
import com.optimizer.tools.feature.termux.domain.TermuxErrorKind
import com.optimizer.tools.feature.termux.domain.TermuxException
import com.optimizer.tools.feature.termux.domain.TermuxResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class TermuxEntry(
    val command: String,
    val stdout: String,
    val stderr: String,
    val exitCode: Int,
    val truncated: Boolean,
    val durationMs: Long,
)

data class TermuxUiState(
    val availability: TermuxAvailability = TermuxAvailability(),
    val command: String = "",
    val running: Boolean = false,
    val entries: List<TermuxEntry> = emptyList(),
    val granting: Boolean = false,
    val errorKind: TermuxErrorKind? = null,
    val errorDetail: String? = null,
)

class TermuxViewModel(
    private val environment: TermuxEnvironment,
) : ViewModel() {
    private val _state = MutableStateFlow(TermuxUiState())
    val state: StateFlow<TermuxUiState> = _state.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        _state.update { it.copy(availability = environment.availability()) }
    }

    fun setCommand(command: String) {
        _state.update { it.copy(command = command) }
    }

    fun usePreset(preset: TermuxPreset) {
        _state.update { it.copy(command = preset.command) }
        run()
    }

    fun run() {
        val command = _state.value.command.trim()
        if (command.isEmpty() || _state.value.running) return
        refresh()
        val availability = _state.value.availability
        if (!availability.ready) {
            _state.update { it.copy(errorKind = availability.blockedReason(), errorDetail = null) }
            return
        }
        _state.update { it.copy(running = true, errorKind = null, errorDetail = null) }
        viewModelScope.launch {
            val started = System.nanoTime()
            try {
                val result = environment.run(command, command.take(MAX_LABEL_LENGTH))
                append(command, result, elapsedMs(started))
            } catch (error: TermuxException) {
                fail(error.kind, error.message)
            } catch (error: Exception) {
                fail(TermuxErrorKind.UNKNOWN, error.message)
            } finally {
                _state.update { it.copy(running = false) }
            }
        }
    }

    fun clearOutput() {
        _state.update { it.copy(entries = emptyList()) }
    }

    /**
     * Con root concede el permiso y activa allow-external-apps de una vez. Sin root,
     * intenta `pm grant` con el ADB local o Shizuku. Si nada funciona, abre Ajustes.
     */
    fun grantPermissionAutomatically() {
        if (_state.value.granting) return
        _state.update { it.copy(granting = true, errorKind = null, errorDetail = null) }
        viewModelScope.launch {
            val granted =
                runCatching {
                    if (environment.hasRoot()) environment.configureWithRoot() else environment.grantPermission()
                }.getOrDefault(false)
            refresh()
            _state.update { it.copy(granting = false, errorKind = if (granted) null else TermuxErrorKind.PERMISSION_DENIED) }
            if (!granted) environment.openAppSettings()
        }
    }

    /** Copia el comando que activa allow-external-apps y abre Termux para pegarlo. */
    fun configureExternalApps() {
        if (!environment.configureExternalApps(TERMUX_EXTERNAL_APPS_COMMAND)) environment.openInstallPage()
    }

    fun openTermux() {
        if (!environment.openTermux()) environment.openInstallPage()
    }

    fun openInstallPage() = environment.openInstallPage()

    fun openAppSettings() = environment.openAppSettings()

    private fun append(
        command: String,
        result: TermuxResult,
        durationMs: Long,
    ) {
        val entry =
            TermuxEntry(
                command = command,
                stdout = result.stdout,
                stderr = result.stderr.ifBlank { result.internalError.orEmpty() },
                exitCode = result.exitCode,
                truncated = result.stdoutTruncated || result.stderrTruncated,
                durationMs = durationMs,
            )
        _state.update {
            it.copy(
                command = "",
                errorKind = if (result.externalAppsDisabled()) TermuxErrorKind.EXTERNAL_APPS_DISABLED else null,
                entries = (listOf(entry) + it.entries).take(MAX_ENTRIES),
            )
        }
    }

    private fun fail(
        kind: TermuxErrorKind,
        detail: String?,
    ) {
        _state.update { it.copy(running = false, errorKind = kind, errorDetail = detail) }
    }

    private fun TermuxAvailability.blockedReason(): TermuxErrorKind =
        if (!installed) TermuxErrorKind.NOT_INSTALLED else TermuxErrorKind.PERMISSION_DENIED

    private fun elapsedMs(started: Long): Long = (System.nanoTime() - started) / NANOS_PER_MILLI

    private fun TermuxResult.externalAppsDisabled(): Boolean = internalError?.contains("allow-external-apps", ignoreCase = true) == true

    companion object {
        private const val MAX_ENTRIES = 20
        private const val MAX_LABEL_LENGTH = 48
        private const val NANOS_PER_MILLI = 1_000_000L

        fun factory(environment: TermuxEnvironment): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T = TermuxViewModel(environment) as T
            }
    }
}
