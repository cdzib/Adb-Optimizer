package com.optimizer.tools.feature.settingsdb

import com.optimizer.tools.data.SettingEntry
import com.optimizer.tools.data.SettingsChange

/** Puertos del editor de ajustes del sistema. */
interface SettingsDbEnvironment {
    fun hasDevice(): Boolean

    suspend fun list(namespace: String): List<SettingEntry>

    suspend fun quickSettings(): QuickSettings

    suspend fun setNightMode(mode: NightMode): Boolean

    suspend fun setScreenTimeout(milliseconds: Int): Boolean

    suspend fun setBrightness(value: Int): Boolean

    suspend fun setBatterySaver(enabled: Boolean): Boolean

    fun history(): List<SettingsChange>

    suspend fun undo(changeId: String): Boolean

    /** `true` solo cuando el dispositivo confirma el nuevo valor al leerlo de vuelta. */
    suspend fun save(
        namespace: String,
        key: String,
        value: String,
    ): Boolean
}
