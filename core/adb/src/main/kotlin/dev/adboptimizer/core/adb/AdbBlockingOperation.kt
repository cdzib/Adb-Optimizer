package dev.adboptimizer.core.adb

import dev.adboptimizer.core.shell.AdbErrorKind
import dev.adboptimizer.core.shell.AdbException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withTimeout

/** Propagate the coroutine deadline to libadb's blocking wait/read operations. */
internal suspend fun <T> runAdbBlockingOperation(
    timeoutMs: Long,
    dispatcher: CoroutineDispatcher,
    onAbort: () -> Unit,
    block: () -> T,
): T =
    try {
        withTimeout(timeoutMs) { runInterruptible(dispatcher, block) }
    } catch (cancelled: CancellationException) {
        onAbort()
        // An outer deadline or user cancellation must stay cancellation, not an app error.
        currentCoroutineContext().ensureActive()
        if (cancelled is TimeoutCancellationException) {
            throw AdbException("El dispositivo no respondió dentro del tiempo de espera.", AdbErrorKind.TIMEOUT, cancelled)
        }
        throw cancelled
    }
