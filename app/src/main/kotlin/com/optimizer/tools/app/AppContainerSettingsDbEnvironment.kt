package com.optimizer.tools.app

import com.optimizer.tools.data.AppContainer
import com.optimizer.tools.data.SettingEntry
import com.optimizer.tools.feature.settingsdb.NightMode
import com.optimizer.tools.feature.settingsdb.QuickSettings
import com.optimizer.tools.feature.settingsdb.SettingsDbEnvironment
import dev.adboptimizer.core.model.DeviceInfo
import java.io.IOException

/** Adaptador de composición para el editor de ajustes. */
internal class AppContainerSettingsDbEnvironment(
    private val container: AppContainer,
) : SettingsDbEnvironment {
    override fun activeDevice(): DeviceInfo? = container.device

    override suspend fun list(namespace: String): List<SettingEntry> =
        container.settingsDb?.list(namespace) ?: throw IOException("No hay un dispositivo remoto conectado.")

    override suspend fun quickSettings(): QuickSettings = requireRepository().quickSettings()

    override suspend fun setNightMode(mode: NightMode): Boolean = requireRepository().setNightMode(mode)

    override suspend fun setScreenTimeout(milliseconds: Int): Boolean = requireRepository().setScreenTimeout(milliseconds)

    override suspend fun setBrightness(value: Int): Boolean = requireRepository().setBrightness(value)

    override suspend fun setBatterySaver(enabled: Boolean): Boolean = requireRepository().setBatterySaver(enabled)

    private fun requireRepository(): com.optimizer.tools.data.SettingsDbRepository =
        container.settingsDb ?: throw IOException("No hay un dispositivo remoto conectado.")

    override suspend fun save(
        namespace: String,
        key: String,
        value: String,
    ): Boolean = container.settingsDb?.save(namespace, key, value) ?: throw IOException("No hay un dispositivo remoto conectado.")
}
