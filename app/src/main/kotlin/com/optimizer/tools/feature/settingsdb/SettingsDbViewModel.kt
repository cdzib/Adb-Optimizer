package com.optimizer.tools.feature.settingsdb

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.optimizer.tools.R
import com.optimizer.tools.core.ui.UiText
import com.optimizer.tools.core.ui.asUiText
import com.optimizer.tools.core.ui.uiText
import com.optimizer.tools.data.SettingEntry
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Estado exclusivo del editor de ajustes. */
data class SettingsDbState(
    val namespace: String = "secure",
    val entries: List<SettingEntry> = emptyList(),
    val visible: List<SettingEntry> = emptyList(),
    val query: String = "",
    val loading: Boolean = false,
    val saving: String? = null,
    val error: UiText? = null,
    val message: UiText? = null,
    val connected: Boolean = false,
)

class SettingsDbViewModel(
    private val environment: SettingsDbEnvironment,
) : ViewModel() {
    private val _state = MutableStateFlow(SettingsDbState())
    val state: StateFlow<SettingsDbState> = _state.asStateFlow()

    fun onEnter() {
        _state.update { it.copy(connected = environment.activeDevice() != null) }
        if (environment.activeDevice() != null) reload()
    }

    fun setNamespace(namespace: String) {
        if (namespace == _state.value.namespace) return
        _state.update { it.copy(namespace = namespace, query = "", message = null) }
        reload()
    }

    fun setQuery(query: String) {
        _state.update { it.copy(query = query, visible = filter(it.entries, query)) }
    }

    fun save(
        key: String,
        value: String,
    ) {
        val current = _state.value
        if (current.saving != null) return
        viewModelScope.launch {
            _state.update { it.copy(saving = key, error = null, message = null) }
            try {
                val confirmed = environment.save(current.namespace, key, value)
                if (confirmed) {
                    _state.update { state ->
                        val entries = state.entries.replace(key, value)
                        state.copy(
                            saving = null,
                            entries = entries,
                            visible = filter(entries, state.query),
                            message = uiText(R.string.settings_db_saved),
                        )
                    }
                } else {
                    _state.update { it.copy(saving = null, error = uiText(R.string.settings_db_not_saved)) }
                }
            } catch (error: Exception) {
                _state.update { it.copy(saving = null, error = error.message?.asUiText() ?: uiText(R.string.settings_db_error)) }
            }
        }
    }

    fun reload() {
        val namespace = _state.value.namespace
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            try {
                val entries = environment.list(namespace)
                _state.update { it.copy(entries = entries, visible = filter(entries, it.query), loading = false) }
            } catch (error: Exception) {
                _state.update {
                    it.copy(
                        loading = false,
                        entries = emptyList(),
                        visible = emptyList(),
                        error =
                            error.message?.asUiText() ?: uiText(R.string.settings_db_error),
                    )
                }
            }
        }
    }

    private fun filter(
        entries: List<SettingEntry>,
        query: String,
    ): List<SettingEntry> {
        val needle = query.trim()
        if (needle.isEmpty()) return entries
        return entries.filter { it.key.contains(needle, ignoreCase = true) }
    }

    private fun List<SettingEntry>.replace(
        key: String,
        value: String,
    ): List<SettingEntry> = map { if (it.key == key) it.copy(value = value) else it }

    companion object {
        fun factory(environment: SettingsDbEnvironment): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T = SettingsDbViewModel(environment) as T
            }
    }
}
