package com.optimizer.tools.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.optimizer.tools.R
import com.optimizer.tools.core.ui.UiText
import com.optimizer.tools.core.ui.uiText
import dev.adboptimizer.core.model.AppTarget
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class MainViewModel(
    private val environment: MainEnvironment,
) : ViewModel() {
    private var remoteConnectRequested = false
    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    private val _navigation = Channel<Screen>(Channel.BUFFERED)
    val navigation: Flow<Screen> = _navigation.receiveAsFlow()

    init {
        val existing = environment.activeDevice()
        _state.update {
            it.copy(
                actions = environment.actions,
                connected = existing,
                connectedDevices = environment.connectedDevices(),
                activeTarget = AppTarget.LOCAL,
            )
        }
        if (existing != null) {
            environment.startSessionService(existing)
        }
        viewModelScope.launch {
            var previousActive = environment.activeSessionId()
            environment.sessions.collect { sessions ->
                val active = sessions.activeDevice
                _state.update { it.copy(connected = active, connectedDevices = sessions.devices) }
                if (active != null && active.id != previousActive) {
                    previousActive = active.id
                    if (remoteConnectRequested) {
                        remoteConnectRequested = false
                        _state.update { it.copy(activeTarget = AppTarget.REMOTE) }
                        navigateTo(Screen.HOME)
                    }
                } else if (active == null && previousActive != null) {
                    previousActive = null
                    if (_state.value.activeTarget == AppTarget.REMOTE) {
                        _state.update { it.copy(activeTarget = AppTarget.LOCAL) }
                        navigateTo(Screen.HOME)
                    }
                }
            }
        }
    }

    /** Solicita navegación de una sola vez al host. */
    fun navigateTo(screen: Screen) {
        _navigation.trySend(screen)
    }

    fun goHome() {
        navigateTo(Screen.HOME)
    }

    /** Cambia el rol global entre este teléfono y el destino ADB remoto. */
    fun setActiveTarget(target: AppTarget) {
        if (target == AppTarget.REMOTE && _state.value.connected == null) {
            openConnect()
            return
        }
        remoteConnectRequested = false
        _state.update { it.copy(activeTarget = target) }
    }

    fun openApps() {
        navigateTo(Screen.APPS)
    }

    fun openHostAppPicker() {
        if (_state.value.connected == null || _state.value.activeTarget != AppTarget.REMOTE) {
            openConnect()
            return
        }
        navigateTo(Screen.HOST_APP_PICKER)
    }

    fun openDpc() {
        if (_state.value.connected == null || _state.value.activeTarget != AppTarget.REMOTE) {
            openConnect()
            return
        }
        navigateTo(Screen.DPC)
    }

    fun openConnect() {
        remoteConnectRequested = false
        navigateTo(Screen.CONNECT)
    }

    fun clearRemoteConnectIntent() {
        remoteConnectRequested = false
    }

    fun openAddDevice() {
        remoteConnectRequested = true
        navigateTo(Screen.ADD_DEVICE)
    }

    /**
     * Tras una conexion iniciada por el usuario se vuelve al panel principal con el
     * equipo remoto ya seleccionado, para que el cambio sea visible de inmediato.
     */
    fun onDeviceConnected() {
        remoteConnectRequested = false
        _state.update { it.copy(activeTarget = AppTarget.REMOTE) }
        navigateTo(Screen.HOME)
    }

    fun openTvLauncher() {
        val device = _state.value.connected
        if (_state.value.activeTarget != AppTarget.REMOTE || device == null || !device.isTv) {
            fail("error.network", uiText(R.string.app_error_launcher_tv_only))
            return
        }
        navigateTo(Screen.TV_LAUNCHER)
    }

    fun consumeFeedback() {
        _state.update { it.copy(errorKey = null, errorDetail = null, messageKey = null) }
    }

    private fun fail(
        key: String,
        detail: UiText?,
    ) {
        _state.update { it.copy(errorKey = key, errorDetail = detail) }
    }

    companion object {
        fun factory(environment: MainEnvironment): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T = MainViewModel(environment) as T
            }
    }
}
