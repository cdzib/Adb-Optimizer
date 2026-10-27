package com.optimizer.tools.feature.settingsdb

import com.optimizer.tools.data.SettingEntry
import dev.adboptimizer.core.model.DeviceInfo

/** Puertos del editor de ajustes del sistema. */
interface SettingsDbEnvironment {
    fun activeDevice(): DeviceInfo?

    suspend fun list(namespace: String): List<SettingEntry>

    /** `true` solo cuando el dispositivo confirma el nuevo valor al leerlo de vuelta. */
    suspend fun save(
        namespace: String,
        key: String,
        value: String,
    ): Boolean
}
