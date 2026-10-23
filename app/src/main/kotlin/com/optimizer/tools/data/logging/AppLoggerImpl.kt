package com.optimizer.tools.data.logging

import android.util.Log
import com.optimizer.tools.domain.logging.AppLogger

/** Implementación de debug respaldada por Logcat. */
internal class LogcatAppLogger : AppLogger {
    override fun debug(
        tag: String,
        message: String,
        error: Throwable?,
    ) {
        if (error == null) Log.d(tag, message) else Log.d(tag, message, error)
    }

    override fun error(
        tag: String,
        message: String,
        error: Throwable?,
    ) {
        if (error == null) Log.e(tag, message) else Log.e(tag, message, error)
    }
}

/** Implementación para variantes sin logging. */
internal object NoOpAppLogger : AppLogger {
    override fun debug(
        tag: String,
        message: String,
        error: Throwable?,
    ) = Unit

    override fun error(
        tag: String,
        message: String,
        error: Throwable?,
    ) = Unit
}
