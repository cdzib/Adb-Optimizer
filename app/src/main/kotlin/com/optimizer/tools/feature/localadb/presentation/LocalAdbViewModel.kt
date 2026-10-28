package com.optimizer.tools.feature.localadb.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.optimizer.tools.feature.localadb.domain.LocalAdbErrorKind
import com.optimizer.tools.feature.localadb.domain.LocalAdbException
import com.optimizer.tools.feature.localadb.domain.LocalAdbPhase
import com.optimizer.tools.feature.localadb.domain.ShellCommandResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class LocalAdbEntry(
    val command: String,
    val exitCode: Int,
    val stdout: String,
    val stderr: String,
    val durationMs: Long,
)

data class LocalAdbUiState(
    val phase: LocalAdbPhase = LocalAdbPhase.DEBUGGING_OFF,
    val errorKind: LocalAdbErrorKind? = null,
    val detail: String? = null,
    val command: String = "",
    val running: Boolean = false,
    val entries: List<LocalAdbEntry> = emptyList(),
    val attempt: Int = 0,
    val developerOptionsEnabled: Boolean = false,
    val log: List<String> = emptyList(),
)

class LocalAdbViewModel(
    private val environment: LocalAdbEnvironment,
) : ViewModel() {
    private val _state = MutableStateFlow(LocalAdbUiState())
    val state: StateFlow<LocalAdbUiState> = _state.asStateFlow()

    private var connectJob: Job? = null
    private var checkingConnection = false

    init {
        viewModelScope.launch {
            environment.logs.collect { lines -> _state.update { it.copy(log = lines) } }
        }
        // Si ya se aceptó el diálogo RSA, reconecta al abrir la app sin volver a preguntar.
        if (environment.isAuthorized() && environment.isAdbEnabled()) {
            connect()
        }
    }

    /**
     * Reenganche silencioso a nivel de app: solo actúa si la llave ya fue autorizada,
     * para no abrir el diálogo RSA sin que el usuario esté en la pantalla.
     */
    fun onAppResumed() {
        when (_state.value.phase) {
            LocalAdbPhase.CONNECTED -> verifyConnection()

            LocalAdbPhase.CONNECTING,
            LocalAdbPhase.WAITING_AUTHORIZATION,
            -> Unit

            else -> if (environment.isAdbEnabled() && environment.isAuthorized()) connect()
        }
    }

    /** Se llama al volver a la app: verifica la sesión y reconecta si adbd la cerró. */
    fun onResume() {
        val phase = _state.value.phase
        if (phase == LocalAdbPhase.CONNECTED) {
            verifyConnection()
            return
        }
        if (phase == LocalAdbPhase.CONNECTING || phase == LocalAdbPhase.WAITING_AUTHORIZATION) {
            return
        }
        refresh()
    }

    fun refresh() {
        environment.log(
            "refresh: adb_enabled=${environment.isAdbEnabled()} " +
                "opciones_desarrollador=${environment.isDeveloperOptionsEnabled()} " +
                "autorizado=${environment.isAuthorized()}",
        )
        if (!environment.isAdbEnabled()) {
            connectJob?.cancel()
            _state.update {
                it.copy(
                    phase = LocalAdbPhase.DEBUGGING_OFF,
                    errorKind = null,
                    detail = null,
                    attempt = 0,
                    developerOptionsEnabled = environment.isDeveloperOptionsEnabled(),
                )
            }
            return
        }
        connect()
    }

    fun connect() {
        connectJob?.cancel()
        connectJob =
            viewModelScope.launch {
                if (!environment.isAdbEnabled()) {
                    _state.update { it.copy(phase = LocalAdbPhase.DEBUGGING_OFF) }
                    return@launch
                }
                var attempt = 1
                while (attempt <= MAX_ATTEMPTS) {
                    _state.update {
                        it.copy(
                            phase = if (attempt == 1) LocalAdbPhase.CONNECTING else LocalAdbPhase.WAITING_AUTHORIZATION,
                            errorKind = null,
                            detail = null,
                            attempt = attempt,
                        )
                    }
                    environment.log("connect: intento $attempt de $MAX_ATTEMPTS")
                    val waiting =
                        launch {
                            delay(WAITING_DELAY_MS)
                            if (_state.value.phase == LocalAdbPhase.CONNECTING) {
                                _state.update { it.copy(phase = LocalAdbPhase.WAITING_AUTHORIZATION) }
                            }
                        }
                    try {
                        environment.connect()
                        waiting.cancel()
                        _state.update {
                            it.copy(
                                phase = LocalAdbPhase.CONNECTED,
                                errorKind = null,
                                detail = null,
                                attempt = 0,
                            )
                        }
                        return@launch
                    } catch (cancelled: CancellationException) {
                        waiting.cancel()
                        throw cancelled
                    } catch (error: LocalAdbException) {
                        waiting.cancel()
                        environment.log("connect: error ${error.kind} · ${error.message}")
                        when (error.kind) {
                            LocalAdbErrorKind.PORT_CLOSED -> {
                                fail(error, LocalAdbPhase.ERROR)
                                return@launch
                            }

                            LocalAdbErrorKind.AUTHORIZATION -> {
                                if (attempt >= MAX_ATTEMPTS) {
                                    fail(error, LocalAdbPhase.ERROR)
                                    return@launch
                                }
                            }

                            else -> {
                                fail(error, LocalAdbPhase.ERROR)
                                return@launch
                            }
                        }
                    } catch (error: Exception) {
                        waiting.cancel()
                        _state.update {
                            it.copy(
                                phase = LocalAdbPhase.ERROR,
                                errorKind = LocalAdbErrorKind.UNKNOWN,
                                detail = error.message,
                            )
                        }
                        return@launch
                    }
                    attempt += 1
                    delay(BACKOFF_MS * attempt)
                }
            }
    }

    fun retry() {
        refresh()
    }

    private fun verifyConnection() {
        if (checkingConnection) return
        checkingConnection = true
        viewModelScope.launch {
            val alive = runCatching { environment.run("echo ping") }.isSuccess
            checkingConnection = false
            if (!alive) {
                connect()
            }
        }
    }

    fun setCommand(command: String) {
        _state.update { it.copy(command = command) }
    }

    fun run() {
        val command = _state.value.command.trim()
        if (command.isEmpty() || _state.value.running || _state.value.phase != LocalAdbPhase.CONNECTED) return
        viewModelScope.launch {
            _state.update { it.copy(running = true, errorKind = null, detail = null) }
            environment.log("shell: $command")
            try {
                val result = environment.run(command)
                _state.update {
                    it.copy(
                        running = false,
                        command = "",
                        entries = (listOf(result.toEntry()) + it.entries).take(MAX_ENTRIES),
                    )
                }
            } catch (error: LocalAdbException) {
                _state.update {
                    it.copy(
                        running = false,
                        phase =
                            if (error.kind == LocalAdbErrorKind.DISCONNECTED) {
                                LocalAdbPhase.ERROR
                            } else {
                                it.phase
                            },
                        errorKind = error.kind,
                        detail = error.message,
                    )
                }
            } catch (error: Exception) {
                environment.log("shell: fallo · ${error.message}")
                _state.update {
                    it.copy(
                        running = false,
                        phase = LocalAdbPhase.ERROR,
                        errorKind = LocalAdbErrorKind.UNKNOWN,
                        detail = error.message,
                    )
                }
            }
        }
    }

    fun clearOutput() {
        _state.update { it.copy(entries = emptyList()) }
    }

    private fun fail(
        error: LocalAdbException,
        phase: LocalAdbPhase,
    ) {
        _state.update {
            it.copy(
                phase = phase,
                errorKind = error.kind,
                detail = error.message,
            )
        }
    }

    private fun ShellCommandResult.toEntry() =
        LocalAdbEntry(
            command = command,
            exitCode = exitCode,
            stdout = stdout,
            stderr = stderr,
            durationMs = durationMs,
        )

    override fun onCleared() {
        environment.log("viewmodel liberado")
        connectJob?.cancel()
        environment.disconnect()
        super.onCleared()
    }

    companion object {
        private const val MAX_ATTEMPTS = 3
        private const val BACKOFF_MS = 1_500L
        private const val WAITING_DELAY_MS = 600L
        private const val MAX_ENTRIES = 30

        fun factory(environment: LocalAdbEnvironment): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T = LocalAdbViewModel(environment) as T
            }
    }
}
