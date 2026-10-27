package com.optimizer.tools.feature.doze

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.optimizer.tools.R
import com.optimizer.tools.core.ui.UiText
import com.optimizer.tools.core.ui.asUiText
import com.optimizer.tools.core.ui.uiText
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class DozeState(
    val status: DozeStatus = DozeStatus(),
    val loading: Boolean = false,
    val running: DozeAction? = null,
    val error: UiText? = null,
    val message: UiText? = null,
    val connected: Boolean = false,
)

class DozeViewModel(
    private val environment: DozeEnvironment,
) : ViewModel() {
    private val _state = MutableStateFlow(DozeState())
    val state: StateFlow<DozeState> = _state.asStateFlow()

    fun onEnter() {
        _state.update { it.copy(connected = environment.activeDevice() != null) }
        refresh()
    }

    fun refresh() {
        if (_state.value.loading || _state.value.running != null) return
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            try {
                val status = environment.status()
                _state.update { it.copy(status = status, loading = false) }
            } catch (error: Exception) {
                _state.update { it.copy(loading = false, error = error.message?.asUiText() ?: uiText(R.string.doze_error)) }
            }
        }
    }

    fun run(action: DozeAction) {
        val current = _state.value
        if (current.running != null) return
        viewModelScope.launch {
            _state.update { it.copy(running = action, error = null, message = null) }
            try {
                val status = environment.run(action)
                _state.update {
                    it.copy(
                        running = null,
                        status = status,
                        message = uiText(action.messageResource()),
                    )
                }
            } catch (error: Exception) {
                _state.update {
                    it.copy(running = null, error = error.message?.asUiText() ?: uiText(R.string.doze_error))
                }
            }
        }
    }

    companion object {
        fun factory(environment: DozeEnvironment): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T = DozeViewModel(environment) as T
            }
    }
}

internal fun DozeAction.messageResource(): Int =
    when (this) {
        DozeAction.FORCE -> R.string.doze_message_force
        DozeAction.STEP -> R.string.doze_message_step
        DozeAction.UNFORCE -> R.string.doze_message_unforce
        DozeAction.MOTION -> R.string.doze_message_motion
    }
