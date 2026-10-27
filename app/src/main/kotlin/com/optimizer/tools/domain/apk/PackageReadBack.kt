package com.optimizer.tools.domain.apk

import dev.adboptimizer.core.shell.AdbCommandRules
import dev.adboptimizer.core.shell.AdbShell
import kotlinx.coroutines.delay

/**
 * Comprobacion posterior a una accion: el comando se considera aplicado solo cuando el
 * dispositivo lo confirma al leerlo de vuelta, igual que hace el motor de recetas.
 */
class PackageReadBack(
    val command: String,
    private val accepts: (String) -> Boolean,
) {
    init {
        require(command.isNotBlank()) { "El comando de lectura no puede estar vacio." }
    }

    fun matches(output: String): Boolean = accepts(output)

    /**
     * Lee el estado hasta [attempts] veces. Un valor que nunca aparece es un fallo, no un
     * "quizas": Android aplica algunos cambios con retraso y una sola lectura miente.
     */
    suspend fun confirm(
        shell: AdbShell,
        attempts: Int = 3,
        pauseMs: Long = 150,
    ): Boolean {
        repeat(attempts.coerceAtLeast(1)) { attempt ->
            if (attempt > 0) delay(pauseMs)
            val output = runCatching { shell.exec(command, 15_000).output }.getOrNull()
            if (output != null && matches(output)) return true
        }
        return false
    }

    companion object {
        /** Estado de compilacion ART: Android 11+ imprime `status=speed`, antes `compiler filter: [speed]`. */
        fun aot(packageName: String): PackageReadBack =
            containing(
                "dumpsys package $packageName | grep -m1 -E 'status=|compiler filter'",
                "speed",
            )

        /** Bucket de standby: Android puede imprimir el nombre o el numero, y la regla entiende ambos. */
        fun standby(
            packageName: String,
            expected: String = "restricted",
        ): PackageReadBack {
            val read =
                requireNotNull(AdbCommandRules.standbyBucketCommand(packageName, null)) {
                    "Nombre de paquete invalido."
                }
            return PackageReadBack(read) { output -> AdbCommandRules.parseStandbyBucket(output) == expected }
        }

        fun backgroundOp(
            packageName: String,
            expected: String = "ignore",
        ): PackageReadBack = containing("appops get $packageName RUN_IN_BACKGROUND", expected)

        fun containing(
            command: String,
            vararg accepted: String,
        ): PackageReadBack {
            require(accepted.isNotEmpty()) { "Hace falta al menos un valor aceptado." }
            return PackageReadBack(command) { output ->
                accepted.any { output.contains(it, ignoreCase = true) }
            }
        }
    }
}
