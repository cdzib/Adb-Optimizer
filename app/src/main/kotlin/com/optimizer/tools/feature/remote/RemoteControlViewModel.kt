package com.optimizer.tools.feature.remote

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.optimizer.tools.R
import com.optimizer.tools.core.ui.UiText
import com.optimizer.tools.core.ui.asUiText
import com.optimizer.tools.core.ui.uiText
import com.optimizer.tools.domain.model.RemoteKey
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Estado exclusivo de la pantalla Control remoto. */
data class RemoteControlState(
    val deviceName: String? = null,
    val error: UiText? = null,
    val mirrorProbing: Boolean = false,
    val mirrorLog: List<String> = emptyList(),
    val mirrorResult: String? = null,
)

class RemoteControlViewModel(
    private val environment: RemoteControlEnvironment,
) : ViewModel() {
    private val _state = MutableStateFlow(RemoteControlState())
    val state: StateFlow<RemoteControlState> = _state.asStateFlow()

    private val pending = Channel<RemoteKey>(capacity = PENDING_CAPACITY)

    @Volatile private var controlsEnabled = true

    fun setControlsEnabled(enabled: Boolean) {
        controlsEnabled = enabled
        if (!enabled) while (pending.tryReceive().isSuccess) { /* Drop old presses on rotation. */ }
    }

    init {
        // Una sola coroutine envía en orden: evita comandos ADB en paralelo y retrasos acumulados.
        viewModelScope.launch {
            for (key in pending) sendKey(key)
        }
    }

    fun onEnter() {
        _state.update { it.copy(deviceName = environment.activeDevice()?.displayName) }
    }

    /** Si la cola está llena se descarta la tecla: es preferible perderla a que la TV vaya atrasada. */
    fun press(key: RemoteKey) {
        if (!controlsEnabled) return
        pending.trySend(key)
    }

    fun dismissError() {
        _state.update { it.copy(error = null) }
    }

    /** Fase 1 de scrcpy: valida el túnel de video y muestra el registro del proceso. */
    fun probeMirror() {
        if (_state.value.mirrorProbing) return
        viewModelScope.launch {
            _state.update { it.copy(mirrorProbing = true, mirrorLog = emptyList(), mirrorResult = null) }
            try {
                val result =
                    environment.probeMirror { line ->
                        _state.update { current ->
                            current.copy(mirrorLog = (current.mirrorLog + line).takeLast(LOG_LIMIT))
                        }
                    }
                _state.update { it.copy(mirrorResult = result) }
            } catch (error: Exception) {
                _state.update {
                    it.copy(mirrorResult = "Error: ${error.message ?: "no se pudo abrir el espejo."}")
                }
            } finally {
                _state.update { it.copy(mirrorProbing = false) }
            }
        }
    }

    private suspend fun sendKey(key: RemoteKey) {
        if (!controlsEnabled) return
        try {
            environment.sendKey(key)
            _state.update { it.copy(error = null) }
        } catch (error: Exception) {
            _state.update {
                it.copy(
                    deviceName = null,
                    error = error.message?.asUiText() ?: uiText(R.string.remote_error_send_key),
                )
            }
        }
    }

    companion object {
        private const val PENDING_CAPACITY = 8
        private const val LOG_LIMIT = 40

        fun factory(environment: RemoteControlEnvironment): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T = RemoteControlViewModel(environment) as T
            }
    }
}
