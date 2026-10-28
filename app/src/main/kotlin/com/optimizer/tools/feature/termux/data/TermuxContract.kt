package com.optimizer.tools.feature.termux.data

import com.optimizer.tools.feature.termux.domain.TermuxResult
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

// Claves del contrato RUN_COMMAND de Termux (TermuxConstants de termux-shared).
// Se declaran aquí para no arrastrar la librería completa al APK.
internal const val TERMUX_PACKAGE = "com.termux"
internal const val TERMUX_API_PACKAGE = "com.termux.api"
internal const val TERMUX_RUN_COMMAND_SERVICE = "com.termux.app.RunCommandService"
internal const val TERMUX_BASH = "/data/data/com.termux/files/usr/bin/bash"
internal const val TERMUX_HOME = "/data/data/com.termux/files/home"

internal const val ACTION_RUN_COMMAND = "com.termux.RUN_COMMAND"
internal const val EXTRA_COMMAND_PATH = "com.termux.RUN_COMMAND_PATH"
internal const val EXTRA_ARGUMENTS = "com.termux.RUN_COMMAND_ARGUMENTS"
internal const val EXTRA_WORKDIR = "com.termux.RUN_COMMAND_WORKDIR"
internal const val EXTRA_BACKGROUND = "com.termux.RUN_COMMAND_BACKGROUND"
internal const val EXTRA_COMMAND_LABEL = "com.termux.RUN_COMMAND_COMMAND_LABEL"
internal const val EXTRA_COMMAND_DESCRIPTION = "com.termux.RUN_COMMAND_COMMAND_DESCRIPTION"
internal const val EXTRA_PENDING_INTENT = "com.termux.RUN_COMMAND_PENDING_INTENT"

internal const val EXTRA_EXECUTION_ID = "com.optimizer.tools.termux.EXECUTION_ID"
internal const val EXTRA_RESULT_BUNDLE = "result"
internal const val RESULT_STDOUT = "stdout"
internal const val RESULT_STDOUT_LENGTH = "stdout_original_length"
internal const val RESULT_STDERR = "stderr"
internal const val RESULT_STDERR_LENGTH = "stderr_original_length"
internal const val RESULT_EXIT_CODE = "exitCode"
internal const val RESULT_ERRMSG = "errmsg"

/**
 * Puente entre [TermuxResultService] y [TermuxRepositoryImpl].
 * El servicio lo instancia Android, así que el bus vive en un objeto compartido.
 */
internal object TermuxResultBus {
    private val _results = MutableSharedFlow<TermuxResult>(replay = 8, extraBufferCapacity = 8)
    val results = _results.asSharedFlow()

    fun publish(result: TermuxResult) {
        _results.tryEmit(result)
    }
}
