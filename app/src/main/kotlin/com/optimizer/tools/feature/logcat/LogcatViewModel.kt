package com.optimizer.tools.feature.logcat

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

/** Estado exclusivo del visor de registro. */
data class LogcatState(
    val entries: List<LogcatEntry> = emptyList(),
    val visible: List<LogcatEntry> = emptyList(),
    val loading: Boolean = false,
    val error: UiText? = null,
    val query: String = "",
    val level: Char = 'V',
    val connected: Boolean = false,
)

class LogcatViewModel(
    private val environment: LogcatEnvironment,
) : ViewModel() {
    private val _state = MutableStateFlow(LogcatState())
    val state: StateFlow<LogcatState> = _state.asStateFlow()

    fun onEnter() {
        _state.update { it.copy(connected = environment.activeDevice() != null) }
        if (environment.activeDevice() != null) refresh()
    }

    fun refresh() {
        if (_state.value.loading) return
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            try {
                val entries = environment.read(READ_LIMIT)
                _state.update { current ->
                    current.copy(
                        entries = entries,
                        visible = LogcatParser.filter(entries, current.level, current.query),
                        loading = false,
                    )
                }
            } catch (error: Exception) {
                _state.update {
                    it.copy(loading = false, error = error.message?.asUiText() ?: uiText(R.string.logcat_error_title))
                }
            }
        }
    }

    fun setQuery(query: String) {
        _state.update { it.copy(query = query, visible = LogcatParser.filter(it.entries, it.level, query)) }
    }

    fun setLevel(level: Char) {
        _state.update { it.copy(level = level, visible = LogcatParser.filter(it.entries, level, it.query)) }
    }

    companion object {
        const val READ_LIMIT = 800

        fun factory(environment: LogcatEnvironment): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T = LogcatViewModel(environment) as T
            }
    }
}
