package com.optimizer.tools.data

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.os.IBinder
import com.optimizer.tools.BuildConfig
import com.optimizer.tools.shizuku.ILocalCommandService
import com.optimizer.tools.shizuku.LocalCommandUserService
import dev.adboptimizer.core.shell.AdbErrorKind
import dev.adboptimizer.core.shell.AdbException
import dev.adboptimizer.core.shell.AdbShell
import dev.adboptimizer.core.shell.ShellResult
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import rikka.shizuku.Shizuku
import java.io.Closeable
import java.io.IOException

/** ADB-shell-compatible command channel bound to an isolated Shizuku UserService. */
class ShizukuAdbShell(
    context: Context,
    private val access: ShizukuLocalAccess,
) : AdbShell,
    Closeable {
    private val appContext = context.applicationContext
    private val args =
        Shizuku
            .UserServiceArgs(
                ComponentName(appContext.packageName, LocalCommandUserService::class.java.name),
            ).daemon(false)
            .processNameSuffix("local-command")
            .debuggable(BuildConfig.DEBUG)
            .version(BuildConfig.VERSION_CODE)

    private val bindMutex = Mutex()
    private val stateLock = Any()

    @Volatile private var service: ILocalCommandService? = null
    private var pending: CompletableDeferred<ILocalCommandService>? = null
    private var serviceConnection: ServiceConnection? = null

    private val connection =
        object : ServiceConnection {
            override fun onServiceConnected(
                name: ComponentName,
                binder: IBinder,
            ) {
                val connected = ILocalCommandService.Stub.asInterface(binder)
                synchronized(stateLock) {
                    service = connected
                    pending?.complete(connected)
                }
            }

            override fun onServiceDisconnected(name: ComponentName) {
                synchronized(stateLock) {
                    service = null
                    pending?.completeExceptionally(IOException("El servicio local de Shizuku se desconectó."))
                }
            }
        }

    override suspend fun exec(
        command: String,
        timeoutMs: Long,
    ): ShellResult {
        access.requireCommandPermission()
        if (command.isBlank()) throw AdbException("El comando está vacío.", AdbErrorKind.SHELL)
        val startedAt = System.nanoTime()
        val remote = getService()
        val result =
            try {
                withContext(Dispatchers.IO) {
                    remote.execute(command, timeoutMs.coerceIn(1L, MAX_TIMEOUT_MS))
                }
            } catch (error: Exception) {
                throw AdbException(
                    error.message ?: "Falló la comunicación con el servicio local de Shizuku.",
                    AdbErrorKind.OFFLINE,
                    error,
                )
            }
        val serviceError = result.getString(KEY_ERROR)
        if (!serviceError.isNullOrBlank() && !result.getBoolean(KEY_TIMED_OUT)) {
            throw IOException(serviceError)
        }
        return ShellResult(
            exitCode = result.getInt(KEY_EXIT_CODE, 127),
            stdout = result.getString(KEY_STDOUT).orEmpty(),
            stderr = result.getString(KEY_STDERR).orEmpty().ifBlank { serviceError.orEmpty() },
            durationMs = result.getLong(KEY_DURATION_MS, (System.nanoTime() - startedAt) / 1_000_000L),
        )
    }

    private suspend fun getService(): ILocalCommandService {
        service?.takeIf { it.asBinder().isBinderAlive }?.let { return it }
        return bindMutex.withLock {
            service?.takeIf { it.asBinder().isBinderAlive }?.let { return@withLock it }
            access.requireCommandPermission()
            val waiting = CompletableDeferred<ILocalCommandService>()
            synchronized(stateLock) {
                pending = waiting
                serviceConnection = connection
            }
            try {
                Shizuku.bindUserService(args, connection)
                withTimeout(BIND_TIMEOUT_MS) { waiting.await() }
            } catch (error: Exception) {
                synchronized(stateLock) {
                    if (pending === waiting) pending = null
                    service = null
                }
                runCatching { Shizuku.unbindUserService(args, connection, true) }
                throw AdbException(
                    error.message ?: "No se pudo iniciar el servicio de comandos de Shizuku.",
                    if (error is kotlinx.coroutines.TimeoutCancellationException) AdbErrorKind.TIMEOUT else AdbErrorKind.OFFLINE,
                    error,
                )
            } finally {
                synchronized(stateLock) { if (pending === waiting) pending = null }
            }
        }
    }

    override fun close() {
        val connectionToUnbind =
            synchronized(stateLock) {
                service = null
                pending?.cancel()
                pending = null
                serviceConnection.also { serviceConnection = null }
            } ?: return
        runCatching { Shizuku.unbindUserService(args, connectionToUnbind, true) }
    }

    private companion object {
        const val BIND_TIMEOUT_MS = 15_000L
        const val MAX_TIMEOUT_MS = 120_000L
        const val KEY_EXIT_CODE = "exitCode"
        const val KEY_STDOUT = "stdout"
        const val KEY_STDERR = "stderr"
        const val KEY_TIMED_OUT = "timedOut"
        const val KEY_ERROR = "error"
        const val KEY_DURATION_MS = "durationMs"
    }
}
