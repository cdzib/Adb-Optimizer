@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.optimizer.tools.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.optimizer.tools.R
import com.optimizer.tools.app.navigation.AppNavGraph
import com.optimizer.tools.app.navigation.HomeRoute
import com.optimizer.tools.app.navigation.RemoteControlRoute
import com.optimizer.tools.app.navigation.mainRoutes
import com.optimizer.tools.app.navigation.toRoute
import com.optimizer.tools.app.navigation.toScreen
import com.optimizer.tools.core.ui.Translate
import com.optimizer.tools.core.ui.resolve
import com.optimizer.tools.domain.model.ShizukuLocalStatus
import com.optimizer.tools.feature.apps.AppsViewModel
import com.optimizer.tools.feature.connection.ConnectionEvent
import com.optimizer.tools.feature.connection.ConnectionViewModel
import com.optimizer.tools.feature.dashboard.DashboardViewModel
import com.optimizer.tools.feature.dpc.DpcViewModel
import com.optimizer.tools.feature.install.InstallEvent
import com.optimizer.tools.feature.install.InstallViewModel
import com.optimizer.tools.feature.logcat.LogcatViewModel
import com.optimizer.tools.feature.mirror.MirrorViewModel
import com.optimizer.tools.feature.optimize.OptimizeEvent
import com.optimizer.tools.feature.optimize.OptimizeViewModel
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
    val dashboardState by dashboardViewModel.state.collectAsStateWithLifecycle()
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

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            if (currentScreen != null && currentScreen.toRoute() in mainRoutes && currentScreen != Screen.CONNECT) {
                DestinationIndicator(
                    name =
                        resolveActiveDestinationName(
                            state.activeTarget,
                            dashboardState.target,
                            dashboardState.device,
                            connectionState.connected,
                            "${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}".trim(),
                        ) ?: stringResource(R.string.known_device_unknown),
                    loading = dashboardState.loading,
                    onRefresh = if (currentScreen == Screen.HOME) dashboardViewModel::refresh else null,
                )
            }
        },
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
            )
        }
    }
}

@Composable
private fun DestinationIndicator(
    name: String,
    loading: Boolean,
    onRefresh: (() -> Unit)?,
) {
    Surface(color = MaterialTheme.colorScheme.surface) {
        Row(
            Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 20.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Filled.Devices, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(12.dp))
            Column(
                Modifier.weight(1f),
                verticalArrangement =
                    androidx.compose.foundation.layout.Arrangement
                        .spacedBy(4.dp),
            ) {
                Text(
                    stringResource(R.string.app_active_destination),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    name,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (onRefresh != null) {
                IconButton(onClick = onRefresh, enabled = !loading) {
                    if (loading) {
                        CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Filled.Refresh, contentDescription = stringResource(R.string.dashboard_refresh))
                    }
                }
            }
        }
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
    NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
        modules.forEach { module ->
            when (module) {
                Screen.HOME -> {
                    NavItem(currentScreen == Screen.HOME, Icons.Filled.Home, stringResource(R.string.nav_home), onHome)
                }

                Screen.APPS -> {
                    NavItem(currentScreen == Screen.APPS, Icons.Filled.Apps, t("nav.apps"), onApps)
                }

                Screen.CONNECT -> {
                    NavItem(
                        currentScreen == Screen.CONNECT,
                        Icons.Filled.Devices,
                        stringResource(R.string.nav_destination),
                        onConnect,
                    )
                }

                Screen.OPTIMIZE -> {
                    NavItem(currentScreen == Screen.OPTIMIZE, Icons.Filled.Bolt, t("nav.optimize"), onOptimize)
                }

                Screen.SETTINGS -> {
                    NavItem(currentScreen == Screen.SETTINGS, Icons.Filled.Settings, stringResource(R.string.nav_settings), onSettings)
                }

                else -> {
                    Unit
                }
            }
        }
    }
}

@Composable
private fun RowScope.NavItem(
    selected: Boolean,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
) {
    NavigationBarItem(
        selected = selected,
        onClick = onClick,
        icon = { Icon(icon, contentDescription = label) },
        label = { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
    )
}
