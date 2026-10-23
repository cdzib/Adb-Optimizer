package com.optimizer.tools.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.optimizer.tools.domain.model.AppSettings
import com.optimizer.tools.domain.model.ThemeMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * ViewModel exclusivo de la pantalla Configuraciones.
 * Persiste a través de [SettingsEnvironment] y expone el estado que esa vista necesita.
 */
class SettingsViewModel(
    private val environment: SettingsEnvironment,
) : ViewModel() {
    private val _settings = MutableStateFlow(AppSettings())
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    init {
        viewModelScope.launch {
            environment.settings.collect { _settings.value = it }
        }
    }

    fun setThemeMode(mode: ThemeMode) = update { it.copy(themeMode = mode) }

    fun setShowSystemApps(enabled: Boolean) = update { it.copy(showSystemApps = enabled) }

    fun setKeepScreenOn(enabled: Boolean) = update { it.copy(keepScreenOn = enabled) }

    fun setAutoRefreshMetrics(enabled: Boolean) = update { it.copy(autoRefreshMetrics = enabled) }

    fun setMetricsRefreshSeconds(seconds: Int) = update { it.copy(metricsRefreshSeconds = seconds.coerceIn(10, 300)) }

    fun setAutoReconnect(enabled: Boolean) = update { it.copy(autoReconnect = enabled) }

    private fun update(transform: (AppSettings) -> AppSettings) {
        viewModelScope.launch {
            environment.save(transform(_settings.value))
        }
    }

    companion object {
        fun factory(environment: SettingsEnvironment): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T = SettingsViewModel(environment) as T
            }
    }
}
