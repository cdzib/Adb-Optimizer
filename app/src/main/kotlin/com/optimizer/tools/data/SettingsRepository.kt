package com.optimizer.tools.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.SharedPreferencesMigration
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.optimizer.tools.domain.model.AppSettings
import com.optimizer.tools.domain.model.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** DataStore-backed preferences, migrated automatically from the legacy SharedPreferences file. */
class SettingsRepository(
    context: Context,
) {
    private val dataStore = context.settingsDataStore

    val settings: Flow<AppSettings> = dataStore.data.map(::toAppSettings)

    suspend fun save(settings: AppSettings) {
        dataStore.edit { preferences ->
            preferences[KEY_THEME] = settings.themeMode.name
            preferences[KEY_SHOW_SYSTEM] = settings.showSystemApps
            preferences[KEY_KEEP_SCREEN_ON] = settings.keepScreenOn
            preferences[KEY_AUTO_REFRESH] = settings.autoRefreshMetrics
            preferences[KEY_REFRESH_SECONDS] =
                settings.metricsRefreshSeconds
                    .coerceIn(MIN_REFRESH_SECONDS, MAX_REFRESH_SECONDS)
            preferences[KEY_AUTO_RECONNECT] = settings.autoReconnect
        }
    }

    private fun toAppSettings(preferences: Preferences): AppSettings =
        AppSettings(
            themeMode =
                runCatching {
                    ThemeMode.valueOf(preferences[KEY_THEME] ?: ThemeMode.SYSTEM.name)
                }.getOrDefault(ThemeMode.SYSTEM),
            showSystemApps = preferences[KEY_SHOW_SYSTEM] ?: false,
            keepScreenOn = preferences[KEY_KEEP_SCREEN_ON] ?: false,
            autoRefreshMetrics = preferences[KEY_AUTO_REFRESH] ?: false,
            metricsRefreshSeconds =
                (preferences[KEY_REFRESH_SECONDS] ?: DEFAULT_REFRESH_SECONDS)
                    .coerceIn(MIN_REFRESH_SECONDS, MAX_REFRESH_SECONDS),
            autoReconnect = preferences[KEY_AUTO_RECONNECT] ?: true,
        )

    private companion object {
        const val PREFS_NAME = "adboptimizer-settings"
        const val DEFAULT_REFRESH_SECONDS = 30
        const val MIN_REFRESH_SECONDS = 10
        const val MAX_REFRESH_SECONDS = 300

        val KEY_THEME = stringPreferencesKey("theme")
        val KEY_SHOW_SYSTEM = booleanPreferencesKey("show_system_apps")
        val KEY_KEEP_SCREEN_ON = booleanPreferencesKey("keep_screen_on")
        val KEY_AUTO_REFRESH = booleanPreferencesKey("auto_refresh_metrics")
        val KEY_REFRESH_SECONDS = intPreferencesKey("metrics_refresh_seconds")
        val KEY_AUTO_RECONNECT = booleanPreferencesKey("auto_reconnect")
    }
}

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "adboptimizer-settings-store",
    produceMigrations = { context ->
        listOf(SharedPreferencesMigration(context, "adboptimizer-settings"))
    },
)
