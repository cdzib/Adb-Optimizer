@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.optimizer.tools.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.optimizer.tools.R
import com.optimizer.tools.app.navigation.AppNavGraph
import com.optimizer.tools.app.navigation.HomeRoute
import com.optimizer.tools.app.navigation.RemoteControlRoute
import com.optimizer.tools.app.navigation.ToolsRoute
import com.optimizer.tools.app.navigation.mainRoutes
import com.optimizer.tools.app.navigation.toRoute
import com.optimizer.tools.app.navigation.toScreen
import com.optimizer.tools.app.tv.LocalTelevisionUi
import com.optimizer.tools.app.tv.TvNavigationItem
import com.optimizer.tools.app.tv.TvScaffold
import com.optimizer.tools.core.ui.BottomNavBar
import com.optimizer.tools.core.ui.BottomNavItem
import com.optimizer.tools.core.ui.Translate
import com.optimizer.tools.core.ui.resolve
import com.optimizer.tools.domain.model.ShizukuLocalStatus
import com.optimizer.tools.feature.apps.AppsViewModel
import com.optimizer.tools.feature.connection.ConnectionEvent
import com.optimizer.tools.feature.connection.ConnectionViewModel
import com.optimizer.tools.feature.console.ConsoleViewModel
import com.optimizer.tools.feature.dashboard.DashboardViewModel
import com.optimizer.tools.feature.debloat.DebloatViewModel
import com.optimizer.tools.feature.doze.DozeViewModel
import com.optimizer.tools.feature.dpc.DpcViewModel
import com.optimizer.tools.feature.files.FilesViewModel
import com.optimizer.tools.feature.inspector.InspectorViewModel
import com.optimizer.tools.feature.install.InstallEvent
import com.optimizer.tools.feature.install.InstallViewModel
import com.optimizer.tools.feature.localadb.presentation.LocalAdbViewModel
import com.optimizer.tools.feature.logcat.LogcatViewModel
import com.optimizer.tools.feature.mirror.MirrorViewModel
import com.optimizer.tools.feature.optimize.OptimizeEvent
import com.optimizer.tools.feature.optimize.OptimizeViewModel
import com.optimizer.tools.feature.profiles.ProfilesViewModel
import com.optimizer.tools.feature.remote.RemoteControlViewModel
import com.optimizer.tools.feature.settings.SettingsViewModel
import com.optimizer.tools.feature.settingsdb.SettingsDbViewModel
import com.optimizer.tools.feature.shizuku.ShizukuViewModel
import com.optimizer.tools.feature.tvlauncher.TvLauncherViewModel

@Composable
fun OptimizerApp(
    viewModel: MainViewModel,
    settingsViewModel: SettingsViewModel,
    connectionViewModel: ConnectionViewModel,
    dashboardViewModel: DashboardViewModel,
    appsViewModel: AppsViewModel,
    optimizeViewModel: OptimizeViewModel,
    installViewModel: InstallViewModel,
    tvLauncherViewModel: TvLauncherViewModel,
    shizukuViewModel: ShizukuViewModel,
    dpcViewModel: DpcViewModel,
    remoteControlViewModel: RemoteControlViewModel,
    mirrorViewModel: MirrorViewModel,
    logcatViewModel: LogcatViewModel,
    settingsDbViewModel: SettingsDbViewModel,
    inspectorViewModel: InspectorViewModel,
    dozeViewModel: DozeViewModel,
    profilesViewModel: ProfilesViewModel,
    debloatViewModel: DebloatViewModel,
    filesViewModel: FilesViewModel,
    consoleViewModel: ConsoleViewModel,
    localAdbViewModel: LocalAdbViewModel,
    localAdbReady: Boolean,
    dictionary: Map<String, String>,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val t: Translate = remember(dictionary) { { key -> dictionary[key] ?: key } }
    val snackbar = remember { SnackbarHostState() }
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination
    val currentScreen = currentDestination?.toScreen()

    val connectionState by connectionViewModel.state.collectAsStateWithLifecycle()
    val shizukuStatus by shizukuViewModel.status.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        installViewModel.events.collect { event ->
            when (event) {
                is InstallEvent.Fail -> {
                    val base = t(event.key)
                    val detail = event.detail?.resolve(context)
                    snackbar.showSnackbar(detail?.let { "$base\n$it" } ?: base)
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        optimizeViewModel.events.collect { event ->
            when (event) {
                is OptimizeEvent.Fail -> {
                    val base = t(event.key)
                    val detail = event.detail?.resolve(context)
                    snackbar.showSnackbar(detail?.let { "$base\n$it" } ?: base)
                }

                is OptimizeEvent.Done -> {
                    snackbar.showSnackbar(t(event.key))
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        connectionViewModel.events.collect { event ->
            when (event) {
                is ConnectionEvent.Fail -> {
                    val base = t(event.key)
                    val detail = event.detail?.resolve(context)
                    snackbar.showSnackbar(detail?.let { "$base\n$it" } ?: base)
                }

                is ConnectionEvent.Connected -> {
                    viewModel.onDeviceConnected()
                    snackbar.showSnackbar(
                        context.getString(R.string.devices_success_connected, event.deviceName),
                    )
                }
            }
        }
    }

    LaunchedEffect(state.errorKey, state.messageKey) {
        val key = state.errorKey ?: state.messageKey
        if (key != null) {
            val base = t(key)
            val detail = state.errorDetail?.resolve(context)
            snackbar.showSnackbar(if (detail.isNullOrBlank()) base else "$base\n$detail")
            viewModel.consumeFeedback()
        }
    }

    // La navegación es una fuente de verdad única: el back stack. El ViewModel solo
    // emite solicitudes de navegación de una sola vez.
    LaunchedEffect(Unit) {
        viewModel.navigation.collect { screen ->
            navController.navigateToScreen(screen)
        }
    }

    // El botón Atrás del sistema no pasa por el ViewModel; solo hay que descartar
    // la intención de conexión remota cuando se abandona el alta de dispositivo.
    var previousScreen by remember { mutableStateOf<Screen?>(null) }
    LaunchedEffect(currentScreen) {
        if (previousScreen == Screen.ADD_DEVICE && currentScreen != Screen.ADD_DEVICE) {
            viewModel.clearRemoteConnectIntent()
        }
        previousScreen = currentScreen
    }

    AdaptiveAppScaffold(
        televisionItems =
            tvNavigationItems(
                activeTarget = state.activeTarget,
                shizukuReady = shizukuStatus == ShizukuLocalStatus.READY,
                t = t,
                onHome = viewModel::goHome,
                onApps = viewModel::openApps,
                onConnect = viewModel::openConnect,
                onOptimize = { viewModel.navigateTo(Screen.OPTIMIZE) },
                onTools = { navController.navigate(ToolsRoute) { launchSingleTop = true } },
                onSettings = { viewModel.navigateTo(Screen.SETTINGS) },
            ),
        selectedKey =
            when {
                currentDestination?.hasRoute<ToolsRoute>() == true -> "tools"
                currentScreen == Screen.HOME -> "home"
                currentScreen == Screen.APPS -> "apps"
                currentScreen == Screen.CONNECT -> "connect"
                currentScreen == Screen.OPTIMIZE -> "optimize"
                currentScreen == Screen.SETTINGS -> "settings"
                else -> null
            },
        televisionStatus =
            when {
                state.activeTarget == dev.adboptimizer.core.model.AppTarget.LOCAL -> {
                    stringResource(R.string.tv_status_local)
                }

                connectionState.connected != null -> {
                    stringResource(R.string.tv_status_remote, connectionState.connected?.displayName.orEmpty())
                }

                else -> {
                    stringResource(R.string.tv_status_disconnected)
                }
            },
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            when {
                currentScreen != null && currentScreen.toRoute() in mainRoutes -> {
                    MainNavigationBar(
                        currentScreen = currentScreen,
                        modules =
                            mainNavigationScreens(
                                state.activeTarget,
                                shizukuReady = shizukuStatus == ShizukuLocalStatus.READY,
                            ),
                        t = t,
                        onHome = viewModel::goHome,
                        onApps = viewModel::openApps,
                        onConnect = viewModel::openConnect,
                        onOptimize = { viewModel.navigateTo(Screen.OPTIMIZE) },
                        onSettings = { viewModel.navigateTo(Screen.SETTINGS) },
                    )
                }
            }
        },
    ) { innerPadding ->
        val fullscreenRemote =
            currentDestination?.hasRoute<RemoteControlRoute>() == true &&
                (
                    connectionState.connected?.isTv == false ||
                        androidx.compose.ui.platform.LocalConfiguration.current.orientation ==
                        android.content.res.Configuration.ORIENTATION_LANDSCAPE
                )
        Box(Modifier.fillMaxSize().then(if (fullscreenRemote) Modifier else Modifier.padding(innerPadding))) {
            AppNavGraph(
                navController = navController,
                state = state,
                t = t,
                viewModel = viewModel,
                settingsViewModel = settingsViewModel,
                connectionViewModel = connectionViewModel,
                dashboardViewModel = dashboardViewModel,
                appsViewModel = appsViewModel,
                optimizeViewModel = optimizeViewModel,
                installViewModel = installViewModel,
                tvLauncherViewModel = tvLauncherViewModel,
                shizukuViewModel = shizukuViewModel,
                dpcViewModel = dpcViewModel,
                remoteControlViewModel = remoteControlViewModel,
                mirrorViewModel = mirrorViewModel,
                logcatViewModel = logcatViewModel,
                settingsDbViewModel = settingsDbViewModel,
                inspectorViewModel = inspectorViewModel,
                dozeViewModel = dozeViewModel,
                profilesViewModel = profilesViewModel,
                debloatViewModel = debloatViewModel,
                filesViewModel = filesViewModel,
                consoleViewModel = consoleViewModel,
                localAdbViewModel = localAdbViewModel,
                localAdbReady = localAdbReady,
            )
        }
    }
}

/** Construye el rail de TV en el orden declarado por [tvNavigationKeys]. */
@Composable
private fun tvNavigationItems(
    activeTarget: dev.adboptimizer.core.model.AppTarget,
    shizukuReady: Boolean,
    t: Translate,
    onHome: () -> Unit,
    onApps: () -> Unit,
    onConnect: () -> Unit,
    onOptimize: () -> Unit,
    onTools: () -> Unit,
    onSettings: () -> Unit,
): List<TvNavigationItem> {
    val showOptimize = Screen.OPTIMIZE in mainNavigationScreens(activeTarget, shizukuReady)
    return tvNavigationKeys.mapNotNull { key ->
        when (key) {
            "home" -> {
                TvNavigationItem("home", stringResource(R.string.nav_home), Icons.Filled.Home, onHome)
            }

            "apps" -> {
                TvNavigationItem("apps", t("nav.apps"), Icons.Filled.Apps, onApps)
            }

            "connect" -> {
                TvNavigationItem(
                    "connect",
                    stringResource(R.string.nav_destination),
                    Icons.Filled.Devices,
                    onConnect,
                )
            }

            "optimize" -> {
                if (showOptimize) {
                    TvNavigationItem("optimize", t("nav.optimize"), Icons.Filled.Bolt, onOptimize)
                } else {
                    null
                }
            }

            "tools" -> {
                TvNavigationItem("tools", stringResource(R.string.tools_title), Icons.Filled.Tune, onTools)
            }

            "settings" -> {
                TvNavigationItem("settings", stringResource(R.string.nav_settings), Icons.Filled.Settings, onSettings)
            }

            else -> {
                null
            }
        }
    }
}

@Composable
private fun AdaptiveAppScaffold(
    televisionItems: List<TvNavigationItem>,
    selectedKey: String?,
    televisionStatus: String,
    snackbarHost: @Composable () -> Unit,
    bottomBar: @Composable () -> Unit,
    content: @Composable (PaddingValues) -> Unit,
) {
    if (LocalTelevisionUi.current) {
        TvScaffold(televisionItems, selectedKey, televisionStatus, snackbarHost, content)
    } else {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            snackbarHost = snackbarHost,
            bottomBar = bottomBar,
            content = content,
        )
    }
}

private fun NavHostController.navigateToScreen(screen: Screen) {
    if (currentDestination?.toScreen() == screen) return
    val target = screen.toRoute()
    if (target in mainRoutes) {
        navigate(target) {
            popUpTo(HomeRoute) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    } else {
        navigate(target) { launchSingleTop = true }
    }
}

@Composable
private fun MainNavigationBar(
    currentScreen: Screen,
    modules: List<Screen>,
    t: Translate,
    onHome: () -> Unit,
    onApps: () -> Unit,
    onConnect: () -> Unit,
    onOptimize: () -> Unit,
    onSettings: () -> Unit,
) {
    val items =
        modules.map { module ->
            when (module) {
                Screen.HOME -> {
                    BottomNavItem(stringResource(R.string.nav_home), Icons.Filled.Home, currentScreen == Screen.HOME, onHome)
                }

                Screen.APPS -> {
                    BottomNavItem(t("nav.apps"), Icons.Filled.Apps, currentScreen == Screen.APPS, onApps)
                }

                Screen.CONNECT -> {
                    BottomNavItem(
                        stringResource(R.string.nav_destination),
                        Icons.Filled.Devices,
                        currentScreen == Screen.CONNECT,
                        onConnect,
                    )
                }

                Screen.OPTIMIZE -> {
                    BottomNavItem(t("nav.optimize"), Icons.Filled.Bolt, currentScreen == Screen.OPTIMIZE, onOptimize)
                }

                Screen.SETTINGS -> {
                    BottomNavItem(
                        stringResource(R.string.nav_settings),
                        Icons.Filled.Settings,
                        currentScreen == Screen.SETTINGS,
                        onSettings,
                    )
                }

                else -> {
                    null
                }
            }
        }
    BottomNavBar(items.filterNotNull())
}
