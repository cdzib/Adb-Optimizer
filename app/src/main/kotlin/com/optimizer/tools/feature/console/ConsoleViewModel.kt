package com.optimizer.tools.feature.console

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

data class ConsoleEntry(
    val command: String,
    val stdout: String,
    val stderr: String,
    val exitCode: Int,
    val durationMs: Long,
)

data class ConsoleState(
    val command: String = "",
    val running: Boolean = false,
    val entries: List<ConsoleEntry> = emptyList(),
    val history: List<String> = emptyList(),
    val deviceAvailable: Boolean = true,
    val message: UiText? = null,
    val error: UiText? = null,
) {
    val output: String?
        get() =
            entries.firstOrNull()?.let { entry ->
                entry.stdout.ifBlank { entry.stderr }
            }
}

class ConsoleViewModel(
    private val environment: ConsoleEnvironment,
) : ViewModel() {
    private val _state = MutableStateFlow(ConsoleState())
    val state: StateFlow<ConsoleState> = _state.asStateFlow()

    fun onEnter() {
        _state.update {
            it.copy(
                deviceAvailable = environment.hasDevice(),
                history = environment.history(),
                error = if (environment.hasDevice()) null else uiText(R.string.console_error_no_device),
            )
        }
    }

    fun setCommand(command: String) {
        _state.update { it.copy(command = command) }
    }

    fun useHistory(command: String) {
        _state.update { it.copy(command = command) }
    }

    fun run() {
        val command = _state.value.command.trim()
        if (command.isEmpty() || _state.value.running) return
        viewModelScope.launch {
            _state.update { it.copy(running = true, error = null, message = null) }
            try {
                val result = environment.run(command)
                val entry = ConsoleEntry(command, result.stdout, result.stderr, result.exitCode, result.durationMs)
                _state.update {
                    it.copy(
                        running = false,
                        entries = (listOf(entry) + it.entries).take(MAX_ENTRIES),
                        history = environment.history(),
                        message = if (result.exitCode == 0) null else uiText(R.string.console_message_exit, result.exitCode),
                    )
                }
            } catch (error: Exception) {
                _state.update {
                    it.copy(
                        running = false,
                        error = error.message?.asUiText() ?: uiText(R.string.console_error_run),
                    )
                }
            }
        }
    }

    fun clear() {
        _state.update { it.copy(entries = emptyList(), message = null, error = null) }
    }

    fun dismissMessage() {
        _state.update { it.copy(message = null) }
    }

    fun dismissError() {
        _state.update { it.copy(error = null) }
    }

    companion object {
        const val MAX_ENTRIES = 30

        fun factory(environment: ConsoleEnvironment): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T = ConsoleViewModel(environment) as T
            }
    }
}
