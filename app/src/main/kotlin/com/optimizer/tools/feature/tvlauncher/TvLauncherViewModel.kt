package com.optimizer.tools.feature.tvlauncher

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.optimizer.tools.R
import com.optimizer.tools.core.ui.UiText
import com.optimizer.tools.core.ui.asUiText
import com.optimizer.tools.core.ui.uiText
import dev.adboptimizer.core.model.RemoteLauncher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Estado exclusivo de la pantalla Launchers de TV. */
data class TvLauncherState(
    val launchers: List<RemoteLauncher> = emptyList(),
    val current: RemoteLauncher? = null,
    val loading: Boolean = false,
    val updating: String? = null,
    val pending: String? = null,
    val error: UiText? = null,
)

class TvLauncherViewModel(
    private val environment: TvLauncherEnvironment,
) : ViewModel() {
    private val _state = MutableStateFlow(TvLauncherState())
    val state: StateFlow<TvLauncherState> = _state.asStateFlow()

    fun onEnter() {
        _state.update { it.copy(pending = null, error = null) }
        refresh()
    }

    fun refresh() {
        if (environment.activeDevice()?.isTv != true) return
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            try {
                val launchers = environment.homeLaunchers()
                val current = environment.currentHomeLauncher()
                _state.update { it.copy(launchers = launchers, current = current, loading = false) }
            } catch (error: Exception) {
                _state.update {
                    it.copy(
                        loading = false,
                        error = error.message?.asUiText() ?: uiText(R.string.tv_launcher_error_load),
                    )
                }
            }
        }
    }

    fun requestChange(packageName: String) {
        if (_state.value.launchers.none { it.packageName == packageName }) return
        if (_state.value.current?.packageName == packageName) return
        _state.update { it.copy(pending = packageName, error = null) }
    }

    fun dismissChange() {
        _state.update { it.copy(pending = null) }
    }

    fun confirmChange() {
        val packageName = _state.value.pending ?: return
        if (environment.activeDevice()?.isTv != true) return
        viewModelScope.launch {
            _state.update { it.copy(updating = packageName, error = null) }
            try {
                val current = environment.setHomeLauncher(packageName)
                _state.update { it.copy(current = current, pending = null, updating = null) }
            } catch (error: Exception) {
                _state.update {
                    it.copy(
                        pending = null,
                        updating = null,
                        error = error.message?.asUiText() ?: uiText(R.string.tv_launcher_error_change),
                    )
                }
            }
        }
    }

    companion object {
        fun factory(environment: TvLauncherEnvironment): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T = TvLauncherViewModel(environment) as T
            }
    }
}
