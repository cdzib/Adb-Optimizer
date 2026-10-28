package com.optimizer.tools.feature.termux.data

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build

/** Construye y envía el intent RUN_COMMAND que ejecuta un script en el contexto de Termux. */
internal class TermuxCommandSender(
    private val context: Context,
) {
    fun send(
        script: String,
        label: String,
        executionId: Int,
    ): Boolean {
        val intent =
            Intent().apply {
                setClassName(TERMUX_PACKAGE, TERMUX_RUN_COMMAND_SERVICE)
                action = ACTION_RUN_COMMAND
                putExtra(EXTRA_COMMAND_PATH, TERMUX_BASH)
                putExtra(EXTRA_ARGUMENTS, arrayOf("-c", script))
                putExtra(EXTRA_WORKDIR, TERMUX_HOME)
                // En segundo plano Termux devuelve stdout/stderr separados y el exit code.
                putExtra(EXTRA_BACKGROUND, true)
                putExtra(EXTRA_COMMAND_LABEL, label)
                putExtra(EXTRA_COMMAND_DESCRIPTION, "Comando lanzado desde AdbOptimizer")
                putExtra(EXTRA_PENDING_INTENT, resultPendingIntent(executionId))
            }
        return runCatching { context.startService(intent) }.isSuccess
    }

    private fun resultPendingIntent(executionId: Int): PendingIntent {
        val intent =
            Intent(context, TermuxResultService::class.java)
                .putExtra(EXTRA_EXECUTION_ID, executionId)
        val mutability =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0
        return PendingIntent.getService(
            context,
            executionId,
            intent,
            PendingIntent.FLAG_ONE_SHOT or mutability,
        )
    }
}
