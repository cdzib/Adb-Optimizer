package com.optimizer.tools.app

import com.optimizer.tools.data.AppContainer
import com.optimizer.tools.domain.model.AppSettings
import com.optimizer.tools.feature.settings.SettingsEnvironment
import kotlinx.coroutines.flow.Flow

/** Adaptador de composición para Configuraciones. */
internal class AppContainerSettingsEnvironment(
    private val container: AppContainer,
) : SettingsEnvironment {
    override val settings: Flow<AppSettings> = container.settingsRepository.settings

    override suspend fun save(settings: AppSettings) = container.settingsRepository.save(settings)
}
