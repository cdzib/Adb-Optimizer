package com.optimizer.tools.data

import com.optimizer.tools.feature.doze.DozeAction
import com.optimizer.tools.feature.doze.DozeParser
import com.optimizer.tools.feature.doze.DozeStatus
import dev.adboptimizer.core.shell.AdbShell
import kotlinx.coroutines.delay

/** Lectura y control del controlador de reposo (Doze) por ADB shell. */
class DozeRepository(
    private val shell: AdbShell,
    private val pause: suspend (Long) -> Unit = { delay(it) },
) {
    suspend fun status(): DozeStatus =
        DozeStatus(
            deep = DozeParser.token(read("dumpsys deviceidle get deep"), light = false),
            light = DozeParser.token(read("dumpsys deviceidle get light"), light = true),
            forced = DozeParser.bool(read("dumpsys deviceidle get force")),
        )

    /**
     * Ejecuta la accion y devuelve el estado leido despues. Para forzar y salir se espera un
     * cambio concreto (`get force`); avanzar y simular movimiento solo reportan el estado nuevo.
     */
    suspend fun run(action: DozeAction): DozeStatus {
        when (action) {
            DozeAction.FORCE -> {
                exec("dumpsys deviceidle force-idle deep")
                await(expectedForced = true)
            }

            DozeAction.STEP -> {
                exec("dumpsys deviceidle step deep")
            }

            DozeAction.UNFORCE -> {
                exec("dumpsys deviceidle unforce")
                await(expectedForced = false)
            }

            DozeAction.MOTION -> {
                exec("dumpsys deviceidle motion")
            }
        }
        return status()
    }

    private suspend fun await(expectedForced: Boolean) {
        repeat(ATTEMPTS) { attempt ->
            if (attempt > 0) pause(PAUSE_MS)
            if (DozeParser.bool(read("dumpsys deviceidle get force")) == expectedForced) return
        }
    }

    private suspend fun read(command: String): String = runCatching { shell.exec(command, 10_000).stdout }.getOrDefault("")

    private suspend fun exec(command: String) {
        shell.exec(command, 15_000)
    }

    private companion object {
        const val ATTEMPTS = 4
        const val PAUSE_MS = 250L
    }
}
