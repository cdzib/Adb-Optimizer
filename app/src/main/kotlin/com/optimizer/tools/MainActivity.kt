package com.optimizer.tools

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.optimizer.tools.app.AppContainerAppsEnvironment
import com.optimizer.tools.app.AppContainerConnectionEnvironment
import com.optimizer.tools.app.AppContainerDashboardEnvironment
import com.optimizer.tools.app.AppContainerDpcEnvironment
import com.optimizer.tools.app.AppContainerInspectorEnvironment
import com.optimizer.tools.app.AppContainerInstallEnvironment
import com.optimizer.tools.app.AppContainerLogcatEnvironment
import com.optimizer.tools.app.AppContainerMainEnvironment
import com.optimizer.tools.app.AppContainerMirrorEnvironment
import com.optimizer.tools.app.AppContainerOptimizeEnvironment
import com.optimizer.tools.app.AppContainerRemoteControlEnvironment
import com.optimizer.tools.app.AppContainerSettingsDbEnvironment
import com.optimizer.tools.app.AppContainerSettingsEnvironment
import com.optimizer.tools.app.AppContainerShizukuEnvironment
import com.optimizer.tools.app.AppContainerTvLauncherEnvironment
import com.optimizer.tools.app.MainViewModel
import com.optimizer.tools.app.OptimizerApp
import com.optimizer.tools.core.ui.AdbOptimizerTheme
import com.optimizer.tools.data.AppContainer
import com.optimizer.tools.data.ShizukuLocalAccess
import com.optimizer.tools.feature.apps.AppsViewModel
import com.optimizer.tools.feature.connection.ConnectionViewModel
import com.optimizer.tools.feature.dashboard.DashboardViewModel
import com.optimizer.tools.feature.dpc.DpcViewModel
import com.optimizer.tools.feature.inspector.InspectorViewModel
import com.optimizer.tools.feature.install.InstallViewModel
import com.optimizer.tools.feature.logcat.LogcatViewModel
import com.optimizer.tools.feature.mirror.MirrorViewModel
import com.optimizer.tools.feature.optimize.OptimizeViewModel
import com.optimizer.tools.feature.remote.RemoteControlViewModel
import com.optimizer.tools.feature.settings.SettingsViewModel
import com.optimizer.tools.feature.settingsdb.SettingsDbViewModel
import com.optimizer.tools.feature.shizuku.ShizukuViewModel
import com.optimizer.tools.feature.tvlauncher.TvLauncherViewModel
import rikka.shizuku.Shizuku

class MainActivity : ComponentActivity() {
    private val shizukuPermissionListener =
        Shizuku.OnRequestPermissionResultListener { requestCode, grantResult ->
            if (requestCode == ShizukuLocalAccess.REQUEST_CODE) {
                shizukuViewModel.onPermissionResult(grantResult == android.content.pm.PackageManager.PERMISSION_GRANTED)
            }
        }

    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    private val container: AppContainer get() = (application as AdbOptimizerApplication).container
    private val viewModel: MainViewModel by viewModels {
        MainViewModel.factory(AppContainerMainEnvironment(container))
    }
    private val settingsViewModel: SettingsViewModel by viewModels {
        SettingsViewModel.factory(AppContainerSettingsEnvironment(container))
    }
    private val connectionViewModel: ConnectionViewModel by viewModels {
        ConnectionViewModel.factory(AppContainerConnectionEnvironment(container))
    }
    private val dashboardViewModel: DashboardViewModel by viewModels {
        DashboardViewModel.factory(AppContainerDashboardEnvironment(container))
    }
    private val appsViewModel: AppsViewModel by viewModels {
        AppsViewModel.factory(AppContainerAppsEnvironment(container))
    }
    private val optimizeViewModel: OptimizeViewModel by viewModels {
        OptimizeViewModel.factory(AppContainerOptimizeEnvironment(container))
    }
    private val installViewModel: InstallViewModel by viewModels {
        InstallViewModel.factory(AppContainerInstallEnvironment(container))
    }
    private val tvLauncherViewModel: TvLauncherViewModel by viewModels {
        TvLauncherViewModel.factory(AppContainerTvLauncherEnvironment(container))
    }
    private val shizukuViewModel: ShizukuViewModel by viewModels {
        ShizukuViewModel.factory(AppContainerShizukuEnvironment(container))
    }
    private val dpcViewModel: DpcViewModel by viewModels {
        DpcViewModel.factory(AppContainerDpcEnvironment(container))
    }
    private val remoteControlViewModel: RemoteControlViewModel by viewModels {
        RemoteControlViewModel.factory(AppContainerRemoteControlEnvironment(container))
    }
    private val mirrorViewModel: MirrorViewModel by viewModels {
        MirrorViewModel.factory(AppContainerMirrorEnvironment(container))
    }
    private val logcatViewModel: LogcatViewModel by viewModels {
        LogcatViewModel.factory(AppContainerLogcatEnvironment(container))
    }
    private val settingsDbViewModel: SettingsDbViewModel by viewModels {
        SettingsDbViewModel.factory(AppContainerSettingsDbEnvironment(container))
    }
    private val inspectorViewModel: InspectorViewModel by viewModels {
        InspectorViewModel.factory(AppContainerInspectorEnvironment(container))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        Shizuku.addRequestPermissionResultListener(shizukuPermissionListener)
        requestNotificationPermissionIfNeeded()
        setContent {
            val settings by settingsViewModel.settings.collectAsStateWithLifecycle()

            LaunchedEffect(settings.keepScreenOn) {
                if (settings.keepScreenOn) {
                    window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                } else {
                    window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                }
            }

            AdbOptimizerTheme(themeMode = settings.themeMode) {
                OptimizerApp(
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
                    dictionary = container.dictionary,
                )
            }
        }
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val granted =
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
        if (!granted) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    override fun onDestroy() {
        Shizuku.removeRequestPermissionResultListener(shizukuPermissionListener)
        super.onDestroy()
    }
}
