package com.optimizer.tools.app.navigation

import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import com.optimizer.tools.app.Screen
import kotlinx.serialization.Serializable

/** Destinos de navegación tipados. */
@Serializable
sealed interface AppDestination

@Serializable
data object HomeRoute : AppDestination

@Serializable
data object ConnectRoute : AppDestination

@Serializable
data object AddDeviceRoute : AppDestination

@Serializable
data object AppsRoute : AppDestination

@Serializable
data object HostAppPickerRoute : AppDestination

@Serializable
data object OptimizeRoute : AppDestination

@Serializable
data object TvLauncherRoute : AppDestination

@Serializable
data object SettingsRoute : AppDestination

@Serializable
data object DpcRoute : AppDestination

@Serializable
data object RemoteControlRoute : AppDestination

@Serializable
data object MirrorRoute : AppDestination

@Serializable
data object LogcatRoute : AppDestination

@Serializable
data class DashboardDetailRoute(
    val category: String,
) : AppDestination

/** Destinos que muestran la barra de navegación principal. */
val mainRoutes: Set<AppDestination> =
    setOf(
        HomeRoute,
        ConnectRoute,
        AppsRoute,
        OptimizeRoute,
        SettingsRoute,
    )

fun Screen.toRoute(): AppDestination =
    when (this) {
        Screen.HOME -> HomeRoute
        Screen.CONNECT -> ConnectRoute
        Screen.ADD_DEVICE -> AddDeviceRoute
        Screen.APPS -> AppsRoute
        Screen.HOST_APP_PICKER -> HostAppPickerRoute
        Screen.OPTIMIZE -> OptimizeRoute
        Screen.TV_LAUNCHER -> TvLauncherRoute
        Screen.SETTINGS -> SettingsRoute
        Screen.DPC -> DpcRoute
    }

fun AppDestination.toScreen(): Screen? =
    when (this) {
        HomeRoute -> Screen.HOME
        ConnectRoute -> Screen.CONNECT
        AddDeviceRoute -> Screen.ADD_DEVICE
        AppsRoute -> Screen.APPS
        HostAppPickerRoute -> Screen.HOST_APP_PICKER
        OptimizeRoute -> Screen.OPTIMIZE
        TvLauncherRoute -> Screen.TV_LAUNCHER
        SettingsRoute -> Screen.SETTINGS
        DpcRoute -> Screen.DPC
        RemoteControlRoute, MirrorRoute, LogcatRoute, is DashboardDetailRoute -> null
    }

/** Traduce el destino actual del back stack al estado de pantalla del host. */
fun NavDestination.toScreen(): Screen? =
    when {
        hasRoute<HomeRoute>() -> Screen.HOME
        hasRoute<ConnectRoute>() -> Screen.CONNECT
        hasRoute<AddDeviceRoute>() -> Screen.ADD_DEVICE
        hasRoute<AppsRoute>() -> Screen.APPS
        hasRoute<HostAppPickerRoute>() -> Screen.HOST_APP_PICKER
        hasRoute<OptimizeRoute>() -> Screen.OPTIMIZE
        hasRoute<TvLauncherRoute>() -> Screen.TV_LAUNCHER
        hasRoute<SettingsRoute>() -> Screen.SETTINGS
        hasRoute<DpcRoute>() -> Screen.DPC
        else -> null
    }

/** Destinos que deben animarse como una pantalla de detalle. */
fun NavDestination.isPushDestination(): Boolean =
    hasRoute<DashboardDetailRoute>() ||
        hasRoute<AddDeviceRoute>() ||
        hasRoute<HostAppPickerRoute>() ||
        hasRoute<TvLauncherRoute>() ||
        hasRoute<RemoteControlRoute>() ||
        hasRoute<MirrorRoute>() ||
        hasRoute<LogcatRoute>()
