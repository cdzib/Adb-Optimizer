package com.optimizer.tools.feature.settings

import com.optimizer.tools.domain.model.AppSettings
import kotlinx.coroutines.flow.Flow

/** Puertos de Configuraciones: lectura reactiva y persistencia de preferencias. */
interface SettingsEnvironment {
    val settings: Flow<AppSettings>

    suspend fun save(settings: AppSettings)
}
