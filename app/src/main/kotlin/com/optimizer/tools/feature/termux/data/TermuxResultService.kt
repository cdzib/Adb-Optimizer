package com.optimizer.tools.feature.termux.data

import android.app.Service
import android.content.Intent
import android.os.Bundle
import android.os.IBinder
import com.optimizer.tools.feature.termux.domain.TermuxResult

/**
 * Recibe el resultado de Termux a través del `PendingIntent` enviado en RUN_COMMAND.
 * Declarado en el manifiesto; Termux lo arranca con los extras `result`, `stdout`, etc.
 */
class TermuxResultService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int,
    ): Int {
        intent?.let(::publish)
        stopSelf(startId)
        return START_NOT_STICKY
    }

    private fun publish(intent: Intent) {
        val bundle = intent.getBundleExtra(EXTRA_RESULT_BUNDLE) ?: return
        val stdout = bundle.getString(RESULT_STDOUT).orEmpty()
        val stderr = bundle.getString(RESULT_STDERR).orEmpty()
        TermuxResultBus.publish(
            TermuxResult(
                executionId = intent.getIntExtra(EXTRA_EXECUTION_ID, -1),
                stdout = stdout,
                stderr = stderr,
                exitCode = bundle.getInt(RESULT_EXIT_CODE, -1),
                stdoutTruncated = bundle.originalLength(RESULT_STDOUT_LENGTH) > stdout.length,
                stderrTruncated = bundle.originalLength(RESULT_STDERR_LENGTH) > stderr.length,
                internalError = bundle.getString(RESULT_ERRMSG)?.takeIf { it.isNotBlank() },
            ),
        )
    }

    private fun Bundle.originalLength(key: String): Int = getString(key)?.toIntOrNull() ?: 0
}
