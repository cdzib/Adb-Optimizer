package com.optimizer.tools.feature.termux.data

import android.content.Context
import android.content.pm.PackageManager
import com.optimizer.tools.feature.termux.domain.TERMUX_RUN_COMMAND_PERMISSION
import com.optimizer.tools.feature.termux.domain.TermuxAvailability
import com.optimizer.tools.feature.termux.domain.TermuxException
import com.optimizer.tools.feature.termux.domain.TermuxRepository
import com.optimizer.tools.feature.termux.domain.TermuxResult
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.atomic.AtomicInteger

/** Implementación del puerto con el intent RUN_COMMAND de Termux. */
class TermuxRepositoryImpl(
    context: Context,
) : TermuxRepository {
    private val appContext = context.applicationContext
    private val sender = TermuxCommandSender(appContext)
    private val nextExecutionId = AtomicInteger(START_EXECUTION_ID)

    override fun availability(): TermuxAvailability {
        val termux = packageInfo(TERMUX_PACKAGE)
        val permissionGranted =
            appContext.checkSelfPermission(TERMUX_RUN_COMMAND_PERMISSION) == PackageManager.PERMISSION_GRANTED
        return TermuxAvailability(
            installed = termux != null,
            versionName = termux?.versionName,
            apiInstalled = packageInfo(TERMUX_API_PACKAGE) != null,
            permissionGranted = permissionGranted,
        )
    }

    override suspend fun run(
        command: String,
        label: String,
        timeoutMs: Long,
    ): TermuxResult {
        val availability = availability()
        if (!availability.installed) throw TermuxException.NotInstalled()
        if (!availability.permissionGranted) throw TermuxException.PermissionDenied()

        val executionId = nextExecutionId.incrementAndGet()
        if (!sender.send(command, label, executionId)) throw TermuxException.StartFailed()

        return withTimeoutOrNull(timeoutMs) {
            TermuxResultBus.results.first { it.executionId == executionId }
        } ?: throw TermuxException.Timeout()
    }

    @Suppress("DEPRECATION")
    private fun packageInfo(packageName: String) =
        runCatching {
            appContext.packageManager.getPackageInfo(packageName, 0)
        }.getOrNull()

    private companion object {
        const val START_EXECUTION_ID = 1000
    }
}
