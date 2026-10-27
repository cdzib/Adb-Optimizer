package com.optimizer.tools.app.navigation

import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import com.optimizer.tools.app.MainViewModel
import com.optimizer.tools.app.Screen
import com.optimizer.tools.app.UiState
import com.optimizer.tools.core.ui.Translate
import com.optimizer.tools.feature.apps.AppsScreen
import com.optimizer.tools.feature.apps.AppsViewModel
import com.optimizer.tools.feature.apps.appsVisible
import com.optimizer.tools.feature.connection.ConnectScreen
import com.optimizer.tools.feature.connection.ConnectionViewModel
import com.optimizer.tools.feature.connection.DevicesScreen
import com.optimizer.tools.feature.dashboard.DashboardCategory
import com.optimizer.tools.feature.dashboard.DashboardDetailScreen
import com.optimizer.tools.feature.dashboard.DashboardScreen
import com.optimizer.tools.feature.dashboard.DashboardViewModel
import com.optimizer.tools.feature.doze.DozeScreen
import com.optimizer.tools.feature.doze.DozeViewModel
import com.optimizer.tools.feature.dpc.DpcScreen
import com.optimizer.tools.feature.dpc.DpcViewModel
import com.optimizer.tools.feature.inspector.InspectorScreen
import com.optimizer.tools.feature.inspector.InspectorViewModel
import com.optimizer.tools.feature.install.HostAppsInstallScreen
import com.optimizer.tools.feature.install.InstallViewModel
import com.optimizer.tools.feature.logcat.LogcatScreen
import com.optimizer.tools.feature.logcat.LogcatViewModel
import com.optimizer.tools.feature.mirror.MirrorScreen
import com.optimizer.tools.feature.mirror.MirrorViewModel
import com.optimizer.tools.feature.optimize.OptimizeScreen
import com.optimizer.tools.feature.optimize.OptimizeViewModel
import com.optimizer.tools.feature.remote.RemoteControlScreen
import com.optimizer.tools.feature.remote.RemoteControlViewModel
import com.optimizer.tools.feature.settings.SettingsScreen
import com.optimizer.tools.feature.settings.SettingsViewModel
import com.optimizer.tools.feature.settingsdb.SettingsDbScreen
import com.optimizer.tools.feature.settingsdb.SettingsDbViewModel
import com.optimizer.tools.feature.shizuku.ShizukuViewModel
import com.optimizer.tools.feature.tvlauncher.TvLauncherScreen
import com.optimizer.tools.feature.tvlauncher.TvLauncherViewModel
import dev.adboptimizer.core.model.AppTarget

@Composable
internal fun AppNavGraph(
    navController: NavHostController,
    state: UiState,
    t: Translate,
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
) {
    val connectionState by connectionViewModel.state.collectAsStateWithLifecycle()
    val dashboardState by dashboardViewModel.state.collectAsStateWithLifecycle()
    val appsState by appsViewModel.state.collectAsStateWithLifecycle()
    val optimizeState by optimizeViewModel.state.collectAsStateWithLifecycle()
    val installState by installViewModel.state.collectAsStateWithLifecycle()
    val tvLauncherState by tvLauncherViewModel.state.collectAsStateWithLifecycle()
    val shizukuStatus by shizukuViewModel.status.collectAsStateWithLifecycle()
    val dpcState by dpcViewModel.state.collectAsStateWithLifecycle()
    val remoteControlState by remoteControlViewModel.state.collectAsStateWithLifecycle()
    val mirrorState by mirrorViewModel.state.collectAsStateWithLifecycle()
    val logcatState by logcatViewModel.state.collectAsStateWithLifecycle()
    val settingsDbState by settingsDbViewModel.state.collectAsStateWithLifecycle()
    val inspectorState by inspectorViewModel.state.collectAsStateWithLifecycle()
    val dozeState by dozeViewModel.state.collectAsStateWithLifecycle()
    NavHost(
        navController = navController,
        startDestination = HomeRoute,
        enterTransition = {
            if (targetState.destination.isPushDestination()) {
                slideInHorizontally { it / 4 } + fadeIn()
            } else {
                fadeIn()
            }
        },
        exitTransition = {
            if (targetState.destination.isPushDestination()) {
                slideOutHorizontally { -it / 4 } + fadeOut()
            } else {
                fadeOut()
            }
        },
        popEnterTransition = {
            if (initialState.destination.isPushDestination()) {
                slideInHorizontally { -it / 4 } + fadeIn()
            } else {
                fadeIn()
            }
        },
        popExitTransition = {
            if (initialState.destination.isPushDestination()) {
                slideOutHorizontally { it / 4 } + fadeOut()
            } else {
                fadeOut()
            }
        },
    ) {
        composable<HomeRoute> {
            LaunchedEffect(state.activeTarget) {
                if (dashboardState.target != state.activeTarget || dashboardState.device == null) {
                    dashboardViewModel.setTarget(state.activeTarget)
                }
            }
            DashboardScreen(
                onRequestPowerAction = dashboardViewModel::requestPowerAction,
                onConfirmPowerAction = dashboardViewModel::confirmPowerAction,
                onDismissPowerAction = dashboardViewModel::dismissPowerAction,
                state = dashboardState,
                target = state.activeTarget,
                shizukuStatus = shizukuStatus,
                t = t,
                onRefresh = dashboardViewModel::refresh,
                onOptimize = { viewModel.navigateTo(Screen.OPTIMIZE) },
                onOpenApps = viewModel::openApps,
                onRequestShizuku = shizukuViewModel::requestPermission,
                onRefreshShizuku = shizukuViewModel::refresh,
                onOpenTvLauncher = viewModel::openTvLauncher,
                onOpenRemote = { navController.navigate(RemoteControlRoute) { launchSingleTop = true } },
                onOpenDashboardDetail = { category ->
                    navController.navigate(DashboardDetailRoute(category.name)) { launchSingleTop = true }
                },
            )
        }

        composable<DashboardDetailRoute> { entry ->
            LaunchedEffect(state.activeTarget) {
                if (dashboardState.target != state.activeTarget || dashboardState.device == null) {
                    dashboardViewModel.setTarget(state.activeTarget)
                }
            }
            val route = entry.toRoute<DashboardDetailRoute>()
            val category = DashboardCategory.entries.firstOrNull { it.name == route.category }
            if (category != null) {
                DashboardDetailScreen(
                    category = category,
                    state = dashboardState,
                    target = state.activeTarget,
                    shizukuStatus = shizukuStatus,
                    onBack = {
                        if (!navController.popBackStack()) {
                            navController.navigate(HomeRoute) { launchSingleTop = true }
                        }
                    },
                )
            } else {
                LaunchedEffect(Unit) {
                    if (!navController.popBackStack()) {
                        navController.navigate(HomeRoute) { launchSingleTop = true }
                    }
                }
            }
        }

        composable<ConnectRoute> {
            DevicesScreen(
                state = connectionState,
                activeTarget = state.activeTarget,
                onAddDevice = viewModel::openAddDevice,
                onSelectTarget = viewModel::setActiveTarget,
                onSelectDevice = connectionViewModel::selectConnectedDevice,
                onDisconnectDevice = connectionViewModel::disconnectDevice,
                onConnectKnown = connectionViewModel::connectKnown,
                onForgetKnown = connectionViewModel::forgetKnown,
                onCancelConnect = connectionViewModel::cancelConnect,
            )
        }

        composable<AddDeviceRoute> {
            ConnectScreen(
                state = connectionState,
                t = t,
                onBack = {
                    viewModel.clearRemoteConnectIntent()
                    connectionViewModel.cancelQrPairing()
                    if (connectionState.connecting || connectionState.connectTimedOut) {
                        connectionViewModel.cancelConnect()
                    }
                    if (!navController.popBackStack()) viewModel.openConnect()
                },
                onMode = connectionViewModel::setConnectMode,
                onDiscover = connectionViewModel::startDiscovery,
                onConnectDiscovered = connectionViewModel::connectDiscovered,
                onConnectManual = connectionViewModel::connectManual,
                onConnectSecure = connectionViewModel::connectSecure,
                onPairWithCode = connectionViewModel::pairWithCode,
                onStartQr = connectionViewModel::startQrPairing,
                onCancelQr = connectionViewModel::cancelQrPairing,
                onConnectKnown = connectionViewModel::connectKnown,
                onForgetKnown = connectionViewModel::forgetKnown,
                onCancelConnect = connectionViewModel::cancelConnect,
                onRetryConnect = connectionViewModel::retryConnect,
                onRefreshWifi = connectionViewModel::refreshWifiStatus,
                onEnableWifi = connectionViewModel::enableWifi,
            )
        }

        composable<AppsRoute> {
            LaunchedEffect(state.activeTarget) { appsViewModel.onEnter(state.activeTarget) }
            LaunchedEffect(installState.message) {
                if (installState.message != null) appsViewModel.loadApps()
            }
            AppsScreen(
                state = appsState,
                t = t,
                device =
                    if (appsState.target == dev.adboptimizer.core.model.AppTarget.LOCAL) {
                        appsState.deviceInfo
                    } else {
                        state.connected
                    },
                actions = state.actions,
                apkInstallRunning = installState.running,
                apkInstallProgress = installState.progress,
                apkInstallMessage = installState.message,
                onInstallApks = installViewModel::installApks,
                onOpenHostAppPicker = viewModel::openHostAppPicker,
                onQuery = appsViewModel::setQuery,
                onFilter = appsViewModel::setFilter,
                onSort = appsViewModel::setSort,
                onReload = appsViewModel::loadApps,
                onOpen = appsViewModel::requestAction,
                onDetails = appsViewModel::loadDetails,
                onInspect = { packageName -> navController.navigate(InspectorRoute(packageName)) },
                onDismissDetails = appsViewModel::dismissDetails,
                onToggle = appsViewModel::toggleSelection,
                onSelectAll = { appsViewModel.selectAll(appsVisible(appsState)) },
                onClear = appsViewModel::clearSelection,
                onBatch = appsViewModel::requestBatch,
                onConfirmAction = appsViewModel::confirmAction,
                onDismissAction = appsViewModel::dismissAction,
                onConfirmBatch = appsViewModel::confirmBatch,
                onDismissBatch = appsViewModel::dismissBatch,
                onDismissMessage = appsViewModel::dismissMessage,
                onAdvancedAction = appsViewModel::runAdvancedAction,
                onConfirmAdvancedAction = appsViewModel::confirmAdvancedAction,
                onDismissAdvancedAction = appsViewModel::dismissAdvancedAction,
            )
        }

        composable<HostAppPickerRoute> {
            LaunchedEffect(Unit) { installViewModel.onEnter() }
            HostAppsInstallScreen(
                state = installState,
                shizukuStatus = shizukuStatus,
                onBack = {
                    if (!navController.popBackStack()) viewModel.openApps()
                },
                onInstall = installViewModel::installHostApps,
                onInstallApks = installViewModel::installApks,
                onOptions = installViewModel::setInstallOptions,
            )
        }

        composable<OptimizeRoute> {
            LaunchedEffect(state.activeTarget, shizukuStatus) {
                if (state.activeTarget == AppTarget.LOCAL &&
                    shizukuStatus != com.optimizer.tools.domain.model.ShizukuLocalStatus.READY
                ) {
                    viewModel.navigateTo(Screen.HOME)
                } else {
                    optimizeViewModel.setTarget(state.activeTarget)
                }
            }
            OptimizeScreen(
                state = optimizeState,
                t = t,
                onProfile = optimizeViewModel::setProfile,
                onToggle = optimizeViewModel::toggle,
                onApply = optimizeViewModel::optimize,
            )
        }

        composable<TvLauncherRoute> {
            LaunchedEffect(Unit) { tvLauncherViewModel.onEnter() }
            TvLauncherScreen(
                state = tvLauncherState,
                onBack = {
                    if (!navController.popBackStack()) viewModel.goHome()
                },
                onRefresh = tvLauncherViewModel::refresh,
                onSelect = tvLauncherViewModel::requestChange,
                onConfirmChange = tvLauncherViewModel::confirmChange,
                onDismissChange = tvLauncherViewModel::dismissChange,
            )
        }

        composable<LogcatRoute> {
            LaunchedEffect(Unit) { logcatViewModel.onEnter() }
            LogcatScreen(
                state = logcatState,
                onBack = {
                    if (!navController.popBackStack()) viewModel.goHome()
                },
                onRefresh = logcatViewModel::refresh,
                onQuery = logcatViewModel::setQuery,
                onLevel = logcatViewModel::setLevel,
            )
        }

        composable<SettingsDbRoute> {
            LaunchedEffect(Unit) { settingsDbViewModel.onEnter() }
            SettingsDbScreen(
                state = settingsDbState,
                onBack = {
                    if (!navController.popBackStack()) viewModel.goHome()
                },
                onNamespace = settingsDbViewModel::setNamespace,
                onQuery = settingsDbViewModel::setQuery,
                onSave = settingsDbViewModel::save,
                onRetry = settingsDbViewModel::reload,
                onNightMode = settingsDbViewModel::setNightMode,
                onScreenTimeout = settingsDbViewModel::setScreenTimeout,
                onBrightness = settingsDbViewModel::setBrightness,
                onBatterySaver = settingsDbViewModel::setBatterySaver,
            )
        }

        composable<InspectorRoute> { entry ->
            val route = entry.toRoute<InspectorRoute>()
            LaunchedEffect(route.packageName) { inspectorViewModel.onEnter(route.packageName) }
            InspectorScreen(
                state = inspectorState,
                onBack = {
                    if (!navController.popBackStack()) viewModel.goHome()
                },
                onTab = inspectorViewModel::selectTab,
            )
        }

        composable<DozeRoute> {
            LaunchedEffect(Unit) { dozeViewModel.onEnter() }
            DozeScreen(
                state = dozeState,
                onBack = {
                    if (!navController.popBackStack()) viewModel.goHome()
                },
                onRefresh = dozeViewModel::refresh,
                onAction = dozeViewModel::run,
            )
        }

        composable<RemoteControlRoute> {
            LaunchedEffect(Unit) { remoteControlViewModel.onEnter() }
            DisposableEffect(Unit) {
                onDispose { mirrorViewModel.stop() }
            }
            RemoteControlScreen(
                state = remoteControlState,
                onBack = {
                    if (!navController.popBackStack()) viewModel.goHome()
                },
                onPress = remoteControlViewModel::press,
                onDismissError = remoteControlViewModel::dismissError,
                mirrorState = mirrorState,
                showRemoteButtons = false,
                isPhone = connectionState.connected?.isTv == false,
                onSurface = mirrorViewModel::onSurfaceChanged,
                onTouch = mirrorViewModel::sendTouch,
                onControlsEnabled = { enabled ->
                    mirrorViewModel.setControlsEnabled(enabled)
                    remoteControlViewModel.setControlsEnabled(enabled)
                },
            )
        }

        composable<MirrorRoute> {
            LaunchedEffect(Unit) { mirrorViewModel.start() }
            DisposableEffect(Unit) {
                onDispose { mirrorViewModel.stop() }
            }
            MirrorScreen(
                state = mirrorState,
                onBack = {
                    mirrorViewModel.stop()
                    if (!navController.popBackStack()) viewModel.goHome()
                },
                onSurface = mirrorViewModel::onSurfaceChanged,
                onTouch = mirrorViewModel::sendTouch,
                onKey = mirrorViewModel::pressKey,
            )
        }

        composable<SettingsRoute> {
            val settings by settingsViewModel.settings.collectAsStateWithLifecycle()
            SettingsScreen(
                settings = settings,
                onThemeMode = settingsViewModel::setThemeMode,
                onShowSystemApps = settingsViewModel::setShowSystemApps,
                onKeepScreenOn = settingsViewModel::setKeepScreenOn,
                onAutoRefreshMetrics = settingsViewModel::setAutoRefreshMetrics,
                onMetricsRefreshSeconds = settingsViewModel::setMetricsRefreshSeconds,
                onAutoReconnect = settingsViewModel::setAutoReconnect,
                remoteRole = state.activeTarget == AppTarget.REMOTE,
                onOpenDpc = viewModel::openDpc,
                onOpenLogcat = { navController.navigate(LogcatRoute) },
                onOpenSettingsDb = { navController.navigate(SettingsDbRoute) },
                onOpenDoze = { navController.navigate(DozeRoute) },
            )
        }

        composable<DpcRoute> {
            LaunchedEffect(Unit) { dpcViewModel.onEnter() }
            DpcScreen(
                state = dpcState,
                onBack = { if (!navController.popBackStack()) viewModel.navigateTo(Screen.SETTINGS) },
                onMode = dpcViewModel::setMode,
                onPreset = dpcViewModel::selectPreset,
                onComponent = dpcViewModel::setComponent,
                onUrl = dpcViewModel::setUrl,
                onRefresh = dpcViewModel::refreshPreflight,
                onDetectReceivers = dpcViewModel::detectReceivers,
                onUseReceiver = dpcViewModel::useReceiver,
                onInstallLocal = dpcViewModel::installFromUris,
                onInstallUrl = dpcViewModel::installFromUrl,
                onSetOwner = dpcViewModel::setOwner,
                onRemoveOwner = dpcViewModel::removeOwner,
                onGenerateQr = dpcViewModel::generateProvisioningQr,
                onDismissMessage = dpcViewModel::dismissMessage,
            )
        }
    }
}
