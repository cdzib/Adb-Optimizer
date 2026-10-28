package com.optimizer.tools.feature.logcat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.optimizer.tools.R
import com.optimizer.tools.core.ui.UiText
import com.optimizer.tools.core.ui.asUiText
import com.optimizer.tools.core.ui.uiText
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
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
    val live: Boolean = false,
)

class LogcatViewModel(
    private val environment: LogcatEnvironment,
    private val liveIntervalMs: Long = LIVE_INTERVAL_MS,
) : ViewModel() {
    private val _state = MutableStateFlow(LogcatState())
    val state: StateFlow<LogcatState> = _state.asStateFlow()

    private var liveJob: Job? = null

    fun onEnter() {
        _state.update { it.copy(connected = environment.hasDevice()) }
        if (environment.hasDevice()) refresh()
    }

    /** Activa o pausa la lectura incremental. Pausar no descarta lo ya leido. */
    fun toggleLive() {
        if (_state.value.live) {
            pauseLive()
        } else {
            startLive()
        }
    }

    private fun startLive() {
        if (liveJob != null || !_state.value.connected) return
        _state.update { it.copy(live = true, error = null) }
        liveJob =
            viewModelScope.launch {
                while (isActive) {
                    val since =
                        _state.value.entries
                            .lastOrNull()
                            ?.time
                    try {
                        val fresh = environment.read(LIVE_LIMIT, since)
                        merge(fresh)
                    } catch (error: Exception) {
                        _state.update {
                            it.copy(
                                live = false,
                                error = error.message?.asUiText() ?: uiText(R.string.logcat_error_title),
                            )
                        }
                        break
                    }
                    delay(liveIntervalMs)
                }
            }
    }

    private fun pauseLive() {
        liveJob?.cancel()
        liveJob = null
        _state.update { it.copy(live = false) }
    }

    private fun merge(fresh: List<LogcatEntry>) {
        if (fresh.isEmpty()) return
        _state.update { current ->
            val entries = LogcatParser.append(current.entries, fresh, MAX_ENTRIES)
            current.copy(entries = entries, visible = LogcatParser.filter(entries, current.level, current.query))
        }
    }

    override fun onCleared() {
        liveJob?.cancel()
        liveJob = null
        super.onCleared()
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
        const val LIVE_LIMIT = 400
        const val MAX_ENTRIES = 2000
        const val LIVE_INTERVAL_MS = 2_000L

        fun factory(environment: LogcatEnvironment): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T = LogcatViewModel(environment) as T
            }
    }
}
