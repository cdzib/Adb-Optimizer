package com.optimizer.tools.feature.settingsdb

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.optimizer.tools.R
import com.optimizer.tools.core.ui.UiText
import com.optimizer.tools.core.ui.asUiText
import com.optimizer.tools.core.ui.uiText
import com.optimizer.tools.data.SettingEntry
import com.optimizer.tools.data.SettingsChange
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
    val quick: QuickSettings = QuickSettings(),
    val quickLoading: Boolean = false,
    val savingPreset: Boolean = false,
    val history: List<SettingsChange> = emptyList(),
    val undoing: String? = null,
)

class SettingsDbViewModel(
    private val environment: SettingsDbEnvironment,
) : ViewModel() {
    private val _state = MutableStateFlow(SettingsDbState())
    val state: StateFlow<SettingsDbState> = _state.asStateFlow()

    fun onEnter() {
        _state.update { it.copy(connected = environment.activeDevice() != null) }
        if (environment.activeDevice() != null) {
            reload()
            refreshQuick()
            refreshHistory()
        }
    }

    fun setNamespace(namespace: String) {
        if (namespace == _state.value.namespace) return
        _state.update { it.copy(namespace = namespace, query = "", message = null) }
        reload()
    }

    fun refreshHistory() {
        _state.update { it.copy(history = runCatching { environment.history() }.getOrDefault(emptyList())) }
    }

    /** Deshace un cambio del historial y recarga la tabla para reflejarlo. */
    fun undoChange(id: String) {
        if (_state.value.undoing != null) return
        viewModelScope.launch {
            _state.update { it.copy(undoing = id, error = null, message = null) }
            try {
                if (environment.undo(id)) {
                    _state.update { it.copy(undoing = null, message = uiText(R.string.settings_db_undone)) }
                    reload()
                } else {
                    _state.update { it.copy(undoing = null, error = uiText(R.string.settings_db_undo_failed)) }
                }
            } catch (error: Exception) {
                _state.update {
                    it.copy(undoing = null, error = error.message?.asUiText() ?: uiText(R.string.settings_db_error))
                }
            } finally {
                _state.update { current -> current.copy(history = runCatching { environment.history() }.getOrDefault(current.history)) }
            }
        }
    }

    fun refreshQuick() {
        viewModelScope.launch {
            _state.update { it.copy(quickLoading = true) }
            val quick = runCatching { environment.quickSettings() }.getOrNull()
            _state.update { it.copy(quick = quick ?: it.quick, quickLoading = false) }
        }
    }

    fun setNightMode(mode: NightMode) {
        applyPreset({ it.copy(nightMode = mode) }) { environment.setNightMode(mode) }
    }

    fun setScreenTimeout(milliseconds: Int) {
        applyPreset({ it.copy(timeoutMs = milliseconds) }) { environment.setScreenTimeout(milliseconds) }
    }

    fun setBrightness(value: Int) {
        applyPreset({ it.copy(brightness = value) }) { environment.setBrightness(value) }
    }

    fun setBatterySaver(enabled: Boolean) {
        applyPreset({ it.copy(batterySaver = enabled) }) { environment.setBatterySaver(enabled) }
    }

    private fun applyPreset(
        update: (QuickSettings) -> QuickSettings,
        call: suspend () -> Boolean,
    ) {
        if (_state.value.savingPreset) return
        viewModelScope.launch {
            _state.update { it.copy(savingPreset = true, error = null, message = null) }
            try {
                if (call()) {
                    _state.update {
                        it.copy(savingPreset = false, quick = update(it.quick), message = uiText(R.string.settings_db_quick_saved))
                    }
                } else {
                    _state.update { it.copy(savingPreset = false, error = uiText(R.string.settings_db_quick_not_saved)) }
                }
            } catch (error: Exception) {
                _state.update {
                    it.copy(savingPreset = false, error = error.message?.asUiText() ?: uiText(R.string.settings_db_error))
                }
            }
        }
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
                            history = runCatching { environment.history() }.getOrDefault(state.history),
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
