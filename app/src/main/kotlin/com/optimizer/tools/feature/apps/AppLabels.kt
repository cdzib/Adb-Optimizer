package com.optimizer.tools.feature.apps

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.optimizer.tools.R
import dev.adboptimizer.core.model.AppAction
import dev.adboptimizer.core.model.AppFilter
import dev.adboptimizer.core.model.AppSort

internal val NATIVE_ACTION_IDS = setOf("kill-app", "disable-app", "clear-app-cache")

@Composable
internal fun appFilterLabel(filter: AppFilter): String =
    when (filter) {
        AppFilter.USER -> stringResource(R.string.apps_filter_user)
        AppFilter.SYSTEM -> stringResource(R.string.apps_filter_system)
        AppFilter.DISABLED -> stringResource(R.string.apps_filter_disabled)
    }

@Composable
internal fun appSortLabel(sort: AppSort): String =
    when (sort) {
        AppSort.NAME -> stringResource(R.string.apps_sort_name)
        AppSort.SIZE -> stringResource(R.string.apps_sort_size)
        AppSort.UPDATED -> stringResource(R.string.apps_sort_updated)
        AppSort.VERSION -> stringResource(R.string.apps_sort_version)
    }

@Composable
internal fun appActionLabel(action: AppAction): String =
    when (action) {
        AppAction.OPEN -> stringResource(R.string.apps_open)
        AppAction.FORCE_STOP -> stringResource(R.string.apps_force_stop)
        AppAction.ENABLE -> stringResource(R.string.apps_enable)
        AppAction.DISABLE -> stringResource(R.string.apps_disable)
        AppAction.CLEAR_DATA -> stringResource(R.string.apps_clear_data)
        AppAction.CLEAR_CACHE -> stringResource(R.string.apps_clear_cache)
        AppAction.UNINSTALL -> stringResource(R.string.apps_uninstall)
        AppAction.EXTRACT_APK -> stringResource(R.string.apps_extract_apk)
        AppAction.APP_INFO -> stringResource(R.string.apps_info)
        AppAction.COMPILE_AOT -> stringResource(R.string.apps_compile_aot)
        AppAction.RESTRICT_STANDBY -> stringResource(R.string.apps_restrict_standby)
        AppAction.RESTRICT_BACKGROUND -> stringResource(R.string.apps_restrict_background)
    }

@Composable
internal fun appActionWarning(action: AppAction): String =
    when (action) {
        AppAction.UNINSTALL -> stringResource(R.string.apps_warning_uninstall)
        AppAction.DISABLE -> stringResource(R.string.apps_warning_disable)
        AppAction.CLEAR_DATA -> stringResource(R.string.apps_warning_clear_data)
        AppAction.CLEAR_CACHE -> stringResource(R.string.apps_warning_clear_cache)
        AppAction.FORCE_STOP -> stringResource(R.string.apps_warning_force_stop)
        AppAction.EXTRACT_APK -> stringResource(R.string.apps_warning_extract_apk)
        else -> ""
    }
