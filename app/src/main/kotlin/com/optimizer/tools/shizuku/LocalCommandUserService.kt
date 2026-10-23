package com.optimizer.tools.shizuku

import android.os.Bundle
import androidx.annotation.Keep
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread

/** Runs in Shizuku's shell/root user-service process, never in the app's own UID. */
@Keep
class LocalCommandUserService
    @Keep
    constructor() : ILocalCommandService.Stub() {
        override fun execute(
            command: String,
            timeoutMs: Long,
        ): Bundle {
            val result = Bundle()
            if (command.isBlank() || command.length > MAX_COMMAND_LENGTH) {
                return result.apply { putString(KEY_ERROR, "Comando vacío o demasiado largo.") }
            }

            val timeout = timeoutMs.coerceIn(1L, MAX_TIMEOUT_MS)
            val startedAt = System.nanoTime()
            var process: Process? = null
            try {
                process = ProcessBuilder("/system/bin/sh", "-c", command).start()
                // Commands are non-interactive; close stdin so no command can hang waiting for input.
                process.outputStream.close()

                val stdout = AtomicReference("")
                val stderr = AtomicReference("")
                val stdoutThread = drain(process.inputStream, stdout)
                val stderrThread = drain(process.errorStream, stderr)
                val completed = process.waitFor(timeout, TimeUnit.MILLISECONDS)
                if (!completed) {
                    process.destroyForcibly()
                    process.waitFor(1, TimeUnit.SECONDS)
                }
                stdoutThread.join(STREAM_JOIN_MS)
                stderrThread.join(STREAM_JOIN_MS)

                result.putInt(KEY_EXIT_CODE, if (completed) process.exitValue() else TIMEOUT_EXIT_CODE)
                result.putString(KEY_STDOUT, stdout.get())
                result.putString(KEY_STDERR, stderr.get())
                result.putBoolean(KEY_TIMED_OUT, !completed)
                if (!completed) result.putString(KEY_ERROR, "El comando excedió el tiempo límite.")
            } catch (interrupted: InterruptedException) {
                process?.destroyForcibly()
                Thread.currentThread().interrupt()
                result.putInt(KEY_EXIT_CODE, TIMEOUT_EXIT_CODE)
                result.putBoolean(KEY_TIMED_OUT, true)
                result.putString(KEY_ERROR, "La ejecución fue interrumpida.")
            } catch (error: Exception) {
                process?.destroyForcibly()
                result.putInt(KEY_EXIT_CODE, 127)
                result.putString(KEY_ERROR, error.message ?: "No se pudo ejecutar el comando local.")
            } finally {
                result.putLong(KEY_DURATION_MS, (System.nanoTime() - startedAt) / 1_000_000L)
            }
            return result
        }

        /** Special Shizuku destroy transaction (16777114) used when unbinding the UserService. */
        override fun destroy() {
            System.exit(0)
        }

        private fun drain(
            stream: InputStream,
            target: AtomicReference<String>,
        ): Thread =
            thread(
                name = "local-command-stream",
                isDaemon = true,
            ) {
                val output = ByteArrayOutputStream()
                val buffer = ByteArray(8 * 1024)
                try {
                    stream.use { input ->
                        while (true) {
                            val count = input.read(buffer)
                            if (count < 0) break
                            val remaining = MAX_OUTPUT_BYTES - output.size()
                            if (remaining > 0) output.write(buffer, 0, minOf(count, remaining))
                            // Continue draining after the cap to avoid blocking the child process on a full pipe.
                        }
                    }
                    target.set(String(output.toByteArray(), StandardCharsets.UTF_8))
                } catch (_: Exception) {
                    target.set(String(output.toByteArray(), StandardCharsets.UTF_8))
                }
            }

        private companion object {
            const val MAX_COMMAND_LENGTH = 16_384
            const val MAX_OUTPUT_BYTES = 128 * 1024
            const val MAX_TIMEOUT_MS = 120_000L
            const val STREAM_JOIN_MS = 2_000L
            const val TIMEOUT_EXIT_CODE = 124
            const val KEY_EXIT_CODE = "exitCode"
            const val KEY_STDOUT = "stdout"
            const val KEY_STDERR = "stderr"
            const val KEY_TIMED_OUT = "timedOut"
            const val KEY_ERROR = "error"
            const val KEY_DURATION_MS = "durationMs"
        }
    }
