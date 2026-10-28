package com.optimizer.tools.app.navigation

import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import com.optimizer.tools.app.MainViewModel
import com.optimizer.tools.app.Screen
import com.optimizer.tools.app.UiState
import com.optimizer.tools.core.ui.Translate
import com.optimizer.tools.domain.model.ShizukuLocalStatus
import com.optimizer.tools.feature.apps.AppsScreen
import com.optimizer.tools.feature.apps.AppsViewModel
import com.optimizer.tools.feature.apps.appsVisible
import com.optimizer.tools.feature.connection.ConnectScreen
import com.optimizer.tools.feature.connection.ConnectionViewModel
import com.optimizer.tools.feature.connection.DevicesScreen
import com.optimizer.tools.feature.console.ConsoleScreen
import com.optimizer.tools.feature.console.ConsoleViewModel
import com.optimizer.tools.feature.dashboard.DashboardCategory
import com.optimizer.tools.feature.dashboard.DashboardDetailScreen
import com.optimizer.tools.feature.dashboard.DashboardScreen
import com.optimizer.tools.feature.dashboard.DashboardViewModel
import com.optimizer.tools.feature.dashboard.PowerOptionsScreen
import com.optimizer.tools.feature.debloat.DebloatScreen
import com.optimizer.tools.feature.debloat.DebloatViewModel
import com.optimizer.tools.feature.doze.DozeScreen
import com.optimizer.tools.feature.doze.DozeViewModel
import com.optimizer.tools.feature.dpc.DpcScreen
import com.optimizer.tools.feature.dpc.DpcViewModel
import com.optimizer.tools.feature.files.FilesScreen
import com.optimizer.tools.feature.files.FilesViewModel
import com.optimizer.tools.feature.inspector.InspectorScreen
import com.optimizer.tools.feature.inspector.InspectorViewModel
import com.optimizer.tools.feature.install.HostAppsInstallScreen
import com.optimizer.tools.feature.install.InstallViewModel
import com.optimizer.tools.feature.localadb.presentation.LocalAdbScreen
import com.optimizer.tools.feature.localadb.presentation.LocalAdbViewModel
import com.optimizer.tools.feature.logcat.LogcatScreen
import com.optimizer.tools.feature.logcat.LogcatViewModel
import com.optimizer.tools.feature.mirror.MirrorScreen
import com.optimizer.tools.feature.mirror.MirrorViewModel
import com.optimizer.tools.feature.optimize.OptimizeScreen
import com.optimizer.tools.feature.optimize.OptimizeViewModel
import com.optimizer.tools.feature.profiles.ProfilesScreen
import com.optimizer.tools.feature.profiles.ProfilesViewModel
import com.optimizer.tools.feature.remote.RemoteControlScreen
import com.optimizer.tools.feature.remote.RemoteControlViewModel
import com.optimizer.tools.feature.settings.SettingsScreen
import com.optimizer.tools.feature.settings.SettingsViewModel
import com.optimizer.tools.feature.settingsdb.SettingsDbScreen
import com.optimizer.tools.feature.settingsdb.SettingsDbViewModel
import com.optimizer.tools.feature.shizuku.ShizukuViewModel
import com.optimizer.tools.feature.tools.ToolsScreen
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
    profilesViewModel: ProfilesViewModel,
    debloatViewModel: DebloatViewModel,
    filesViewModel: FilesViewModel,
    consoleViewModel: ConsoleViewModel,
    localAdbViewModel: LocalAdbViewModel,
    localAdbReady: Boolean,
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
    val profilesState by profilesViewModel.state.collectAsStateWithLifecycle()
    val debloatState by debloatViewModel.state.collectAsStateWithLifecycle()
    val filesState by filesViewModel.state.collectAsStateWithLifecycle()
    val consoleState by consoleViewModel.state.collectAsStateWithLifecycle()
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
                localAdbReady = localAdbReady,
                t = t,
                onRefresh = dashboardViewModel::refresh,
                onOptimize = { viewModel.navigateTo(Screen.OPTIMIZE) },
                onOpenApps = viewModel::openApps,
                onRequestShizuku = shizukuViewModel::requestPermission,
                onRefreshShizuku = shizukuViewModel::refresh,
                onOpenTvLauncher = viewModel::openTvLauncher,
                onOpenRemote = { navController.navigate(RemoteControlRoute) { launchSingleTop = true } },
                onOpenTools = { navController.navigate(ToolsRoute) { launchSingleTop = true } },
                onOpenProfiles = { navController.navigate(ProfilesRoute) { launchSingleTop = true } },
                onOpenPowerOptions = { navController.navigate(PowerRoute) { launchSingleTop = true } },
                onOpenDashboardDetail = { category ->
                    navController.navigate(DashboardDetailRoute(category.name)) { launchSingleTop = true }
                },
            )
        }

        composable<LocalAdbRoute> {
            LocalAdbScreen(
                viewModel = localAdbViewModel,
                onBack = { if (!navController.popBackStack()) viewModel.goHome() },
            )
        }

        composable<PowerRoute> {
            PowerOptionsScreen(
                state = dashboardState,
                target = state.activeTarget,
                canModify =
                    state.activeTarget == AppTarget.REMOTE ||
                        shizukuStatus == ShizukuLocalStatus.READY ||
                        localAdbReady,
                onOpenLocalAdb = { navController.navigate(LocalAdbRoute) { launchSingleTop = true } },
                onBack = { if (!navController.popBackStack()) viewModel.goHome() },
                onRequest = dashboardViewModel::requestPowerAction,
                onConfirm = dashboardViewModel::confirmPowerAction,
                onDismiss = dashboardViewModel::dismissPowerAction,
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
            Column(Modifier.fillMaxSize()) {
                Box(Modifier.weight(1f)) {
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
                        onOpenRemote = { navController.navigate(RemoteControlRoute) { launchSingleTop = true } },
                        onOpenMirror = { navController.navigate(MirrorRoute) { launchSingleTop = true } },
                        onOpenTools = { navController.navigate(ToolsRoute) { launchSingleTop = true } },
                    )
                }
            }
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
                onConnect = { navController.navigate(LocalAdbRoute) { launchSingleTop = true } },
            )
        }

        composable<HostAppPickerRoute> {
            LaunchedEffect(Unit) { installViewModel.onEnter() }
            HostAppsInstallScreen(
                state = installState,
                shizukuStatus =
                    if (localAdbReady) {
                        ShizukuLocalStatus.READY
                    } else {
                        shizukuStatus
                    },
                onBack = {
                    if (!navController.popBackStack()) viewModel.openApps()
                },
                onInstall = installViewModel::installHostApps,
                onInstallApks = installViewModel::installApks,
                onOptions = installViewModel::setInstallOptions,
            )
        }

        composable<OptimizeRoute> {
            LaunchedEffect(state.activeTarget, shizukuStatus, localAdbReady) {
                optimizeViewModel.setTarget(state.activeTarget)
            }
            OptimizeScreen(
                state = optimizeState,
                t = t,
                onProfile = optimizeViewModel::setProfile,
                onToggle = optimizeViewModel::toggle,
                onApply = optimizeViewModel::optimize,
                onOpenLocalAdb = { navController.navigate(LocalAdbRoute) { launchSingleTop = true } },
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
                onToggleLive = logcatViewModel::toggleLive,
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
                onUndo = settingsDbViewModel::undoChange,
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

        composable<ProfilesRoute> {
            LaunchedEffect(Unit) { profilesViewModel.onEnter() }
            ProfilesScreen(
                state = profilesState,
                onBack = {
                    if (!navController.popBackStack()) viewModel.goHome()
                },
                onRefresh = profilesViewModel::refresh,
                onStartCreate = profilesViewModel::startCreate,
                onCancelCreate = profilesViewModel::cancelCreate,
                onNameDraft = profilesViewModel::setNameDraft,
                onCreate = profilesViewModel::createProfile,
                onPreview = profilesViewModel::preview,
                onDismissPreview = profilesViewModel::dismissPreview,
                onApply = profilesViewModel::apply,
                onToggleWatch = profilesViewModel::toggleWatch,
                onRequestDelete = profilesViewModel::requestDelete,
                onCancelDelete = profilesViewModel::cancelDelete,
                onConfirmDelete = profilesViewModel::confirmDelete,
                onUndo = profilesViewModel::undoLastRun,
                onDismissMessage = profilesViewModel::dismissMessage,
                onDismissError = profilesViewModel::dismissError,
                onDismissDrift = profilesViewModel::dismissDrift,
            )
        }

        composable<DebloatRoute> {
            LaunchedEffect(Unit) { debloatViewModel.onEnter() }
            DebloatScreen(
                state = debloatState,
                onBack = {
                    if (!navController.popBackStack()) viewModel.goHome()
                },
                onRefresh = debloatViewModel::refresh,
                onDownload = debloatViewModel::downloadCatalog,
                onQuery = debloatViewModel::setQuery,
                onToggleRisk = debloatViewModel::toggleRisk,
                onToggle = debloatViewModel::toggleSelection,
                onSelectAll = debloatViewModel::selectAllVisible,
                onClearSelection = debloatViewModel::clearSelection,
                onRequestAction = debloatViewModel::requestAction,
                onCancelAction = debloatViewModel::cancelAction,
                onConfirmAction = debloatViewModel::confirmAction,
                onUndo = debloatViewModel::undo,
                onDismissMessage = debloatViewModel::dismissMessage,
                onDismissError = debloatViewModel::dismissError,
            )
        }

        composable<FilesRoute> {
            LaunchedEffect(Unit) { filesViewModel.onEnter() }
            FilesScreen(
                state = filesState,
                onBack = {
                    if (!navController.popBackStack()) viewModel.goHome()
                },
                onRefresh = filesViewModel::refresh,
                onOpen = filesViewModel::open,
                onUp = filesViewModel::up,
                onEntry = filesViewModel::openEntry,
                onQuery = filesViewModel::setQuery,
                onPreview = filesViewModel::previewText,
                onDismissPreview = filesViewModel::dismissPreview,
                onRename = filesViewModel::startRename,
                onRenameName = filesViewModel::setRenameName,
                onConfirmRename = filesViewModel::confirmRename,
                onCancelRename = filesViewModel::cancelRename,
                onRequestDelete = filesViewModel::requestDelete,
                onConfirmDelete = filesViewModel::confirmDelete,
                onCancelDelete = filesViewModel::cancelDelete,
                onStartCreateFolder = filesViewModel::startCreateFolder,
                onFolderName = filesViewModel::setFolderName,
                onConfirmCreateFolder = filesViewModel::confirmCreateFolder,
                onCancelCreateFolder = filesViewModel::cancelCreateFolder,
                onInstall = filesViewModel::installApk,
                onDismissSelection = filesViewModel::dismissSelection,
                onDismissMessage = filesViewModel::dismissMessage,
                onDismissError = filesViewModel::dismissError,
            )
        }

        composable<ConsoleRoute> {
            LaunchedEffect(Unit) { consoleViewModel.onEnter() }
            ConsoleScreen(
                state = consoleState,
                onBack = {
                    if (!navController.popBackStack()) viewModel.goHome()
                },
                onCommand = consoleViewModel::setCommand,
                onRun = consoleViewModel::run,
                onUseHistory = consoleViewModel::useHistory,
                onClear = consoleViewModel::clear,
                onDismissMessage = consoleViewModel::dismissMessage,
                onDismissError = consoleViewModel::dismissError,
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

        composable<ToolsRoute> {
            ToolsScreen(
                onBack = { if (!navController.popBackStack()) viewModel.goHome() },
                onOpenLogcat = { navController.navigate(LogcatRoute) { launchSingleTop = true } },
                onOpenConsole = { navController.navigate(ConsoleRoute) { launchSingleTop = true } },
                onOpenSettingsDb = { navController.navigate(SettingsDbRoute) { launchSingleTop = true } },
                onOpenDoze = { navController.navigate(DozeRoute) { launchSingleTop = true } },
                onOpenProfiles = { navController.navigate(ProfilesRoute) { launchSingleTop = true } },
                onOpenDebloat = { navController.navigate(DebloatRoute) { launchSingleTop = true } },
                onOpenFiles = { navController.navigate(FilesRoute) { launchSingleTop = true } },
                onOpenLocalAdb = { navController.navigate(LocalAdbRoute) { launchSingleTop = true } },
                onOpenDpc = if (state.activeTarget == AppTarget.REMOTE) viewModel::openDpc else null,
                targetLabel = dashboardState.device?.displayName,
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
                onOpenProfiles = { navController.navigate(ProfilesRoute) },
                onOpenDebloat = { navController.navigate(DebloatRoute) },
                onOpenFiles = { navController.navigate(FilesRoute) },
                onOpenConsole = { navController.navigate(ConsoleRoute) },
                onOpenTools = { navController.navigate(ToolsRoute) { launchSingleTop = true } },
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
