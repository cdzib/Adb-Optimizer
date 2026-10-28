package com.optimizer.tools.feature.termux.presentation

import com.optimizer.tools.feature.termux.domain.TermuxAvailability
import com.optimizer.tools.feature.termux.domain.TermuxResult

/** Puertos que consume el ViewModel; la composición los implementa con el contenedor. */
interface TermuxEnvironment {
    fun availability(): TermuxAvailability

    suspend fun run(
        command: String,
        label: String,
    ): TermuxResult

    /** True si el dispositivo tiene root utilizable (`su` responde como uid 0). */
    suspend fun hasRoot(): Boolean

    /**
     * Con root: concede RUN_COMMAND, escribe allow-external-apps y verifica ejecutando
     * un comando real en Termux. Devuelve false si algo falló.
     */
    suspend fun configureWithRoot(): Boolean

    /**
     * Intenta conceder RUN_COMMAND con el ADB local o Shizuku.
     * Devuelve false si no hay transporte privilegiado o el permiso sigue denegado.
     */
    suspend fun grantPermission(): Boolean

    /** Copia [command] al portapapeles y abre Termux para pegarlo. */
    fun configureExternalApps(command: String): Boolean

    /** Abre Termux; devuelve false si la app no está instalada. */
    fun openTermux(): Boolean

    fun openInstallPage()

    fun openAppSettings()
}
