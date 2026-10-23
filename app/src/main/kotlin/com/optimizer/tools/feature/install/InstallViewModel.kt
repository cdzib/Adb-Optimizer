package com.optimizer.tools.feature.install

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.optimizer.tools.R
import com.optimizer.tools.core.ui.UiText
import com.optimizer.tools.core.ui.asUiText
import com.optimizer.tools.core.ui.uiText
import com.optimizer.tools.domain.model.ShizukuLocalStatus
import dev.adboptimizer.core.adb.RemoteApkInstallOutcome
import dev.adboptimizer.core.model.ManagedApp
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Estado exclusivo de la pantalla Instalar aplicaciones. */
data class InstallState(
    val running: Boolean = false,
    val progress: UiText? = null,
    val message: UiText? = null,
    val hostApps: List<ManagedApp> = emptyList(),
    val hostAppsLoading: Boolean = false,
    val hostAppsError: UiText? = null,
)

sealed interface InstallEvent {
    data class Fail(
        val key: String,
        val detail: UiText?,
    ) : InstallEvent
}

class InstallViewModel(
    private val environment: InstallEnvironment,
) : ViewModel() {
    private val _state = MutableStateFlow(InstallState())
    val state: StateFlow<InstallState> = _state.asStateFlow()

    private val _events = MutableSharedFlow<InstallEvent>(extraBufferCapacity = 8)
    val events: SharedFlow<InstallEvent> = _events.asSharedFlow()

    init {
        viewModelScope.launch {
            environment.progress.collect { install ->
                _state.update {
                    it.copy(
                        running = install.running,
                        progress = install.progress?.asUiText(),
                        message = install.message?.asUiText(),
                    )
                }
            }
        }
    }

    /** Se llama al abrir la pantalla de instalacion. */
    fun onEnter() {
        loadHostApps()
    }

    fun reloadHostApps() = loadHostApps()

    private fun loadHostApps() {
        _state.update { it.copy(hostAppsLoading = true, hostAppsError = null) }
        viewModelScope.launch {
            val result =
                if (environment.shizukuStatus() == ShizukuLocalStatus.NOT_INSTALLED) {
                    Result.success(emptyList())
                } else {
                    runCatching { environment.hostApps() }
                }
            _state.update {
                it.copy(
                    hostApps = result.getOrDefault(emptyList()),
                    hostAppsLoading = false,
                    hostAppsError =
                        result.exceptionOrNull()?.message?.asUiText()
                            ?: if (result.getOrDefault(emptyList()).isEmpty()) {
                                uiText(R.string.install_error_no_host_apps)
                            } else {
                                null
                            },
                )
            }
        }
    }

    fun installApks(uris: List<Uri>) {
        if (uris.isEmpty()) return
        begin { onProgress -> environment.installApks(uris, onProgress) }
    }

    fun installHostApps(packageNames: List<String>) {
        if (packageNames.isEmpty()) return
        begin { onProgress -> environment.installHostApps(packageNames, onProgress) }
    }

    private fun begin(install: suspend ((String) -> Unit) -> List<RemoteApkInstallOutcome>) {
        if (!environment.hasRemoteSession()) {
            _events.tryEmit(InstallEvent.Fail("error.notConnected", null))
            return
        }
        if (environment.isInstallRunning()) return
        environment.startInstall(install)
    }

    companion object {
        fun factory(environment: InstallEnvironment): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T = InstallViewModel(environment) as T
            }
    }
}
