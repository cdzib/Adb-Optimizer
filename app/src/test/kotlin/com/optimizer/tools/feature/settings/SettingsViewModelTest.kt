package com.optimizer.tools.feature.settings

import com.optimizer.tools.domain.model.AppSettings
import com.optimizer.tools.domain.model.ThemeMode
import com.optimizer.tools.testutil.runViewModelTest
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runCurrent
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {
    @Test
    fun `loads persisted settings`() =
        runViewModelTest {
            val environment =
                FakeSettingsEnvironment(
                    AppSettings(themeMode = ThemeMode.DARK, metricsRefreshSeconds = 45),
                )
            val viewModel = SettingsViewModel(environment)

            runCurrent()

            assertEquals(ThemeMode.DARK, viewModel.settings.value.themeMode)
            assertEquals(45, viewModel.settings.value.metricsRefreshSeconds)
        }

    @Test
    fun `theme change is persisted`() =
        runViewModelTest {
            val environment = FakeSettingsEnvironment()
            val viewModel = SettingsViewModel(environment)
            runCurrent()

            viewModel.setThemeMode(ThemeMode.LIGHT)
            runCurrent()

            assertEquals(ThemeMode.LIGHT, environment.saved.single().themeMode)
            assertEquals(ThemeMode.LIGHT, viewModel.settings.value.themeMode)
        }

    @Test
    fun `metrics refresh seconds are clamped before saving`() =
        runViewModelTest {
            val environment = FakeSettingsEnvironment()
            val viewModel = SettingsViewModel(environment)
            runCurrent()

            viewModel.setMetricsRefreshSeconds(1)
            runCurrent()
            assertEquals(10, environment.saved.last().metricsRefreshSeconds)

            viewModel.setMetricsRefreshSeconds(999)
            runCurrent()
            assertEquals(300, environment.saved.last().metricsRefreshSeconds)
        }
}

private class FakeSettingsEnvironment(
    initial: AppSettings = AppSettings(),
) : SettingsEnvironment {
    override val settings = MutableStateFlow(initial)
    val saved = mutableListOf<AppSettings>()

    override suspend fun save(settings: AppSettings) {
        saved += settings
        this.settings.value = settings
    }
}
