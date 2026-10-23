package com.optimizer.tools.data

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.BatteryManager
import android.os.Build
import android.os.Environment
import android.os.PowerManager
import android.os.StatFs
import android.os.SystemClock
import dev.adboptimizer.core.model.DeviceInfo
import dev.adboptimizer.core.model.DeviceMetrics
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** Lecturas del telefono donde corre la app (el destino local). */
class LocalDeviceRepository(
    private val context: Context,
) {
    suspend fun readInfo(): DeviceInfo =
        withContext(Dispatchers.IO) {
            val uiMode = context.resources.configuration.uiMode and Configuration.UI_MODE_TYPE_MASK
            val isTv =
                uiMode == Configuration.UI_MODE_TYPE_TELEVISION ||
                    context.packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK)
            DeviceInfo(
                id = "local:${Build.MODEL}",
                model = Build.MODEL,
                manufacturer = Build.MANUFACTURER,
                sdk = Build.VERSION.SDK_INT,
                androidVersion = Build.VERSION.RELEASE ?: "?",
                isTv = isTv,
                hasRoot = false,
                abi = Build.SUPPORTED_ABIS.firstOrNull() ?: "?",
                hardwareInfo = LocalHardwareReader(context).readInfo(),
                displayInfo = LocalDisplayReader(context).readInfo(),
                sensors = LocalSensorReader(context).readSensors(),
                networkInterfaces = LocalNetworkReader().readInterfaces(),
            )
        }

    suspend fun readMetrics(): DeviceMetrics =
        withContext(Dispatchers.IO) {
            val activity = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val memory = ActivityManager.MemoryInfo().also(activity::getMemoryInfo)
            val data = StatFs(Environment.getDataDirectory().absolutePath)
            val totalStorage = data.totalBytes / 1024
            val availableStorage = data.availableBytes / 1024
            val usedStorage = (totalStorage - availableStorage).coerceAtLeast(0)

            val load1 =
                runCatching {
                    File("/proc/loadavg")
                        .readText()
                        .trim()
                        .split(Regex("\\s+"))
                        .first()
                        .toFloat()
                }.getOrDefault(0f)
            val cores = Runtime.getRuntime().availableProcessors().coerceAtLeast(1)
            val cpuBusy = (load1 / cores * 100f).coerceIn(0f, 100f)

            val power = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
            val thermalStatus =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    power?.currentThermalStatus ?: 0
                } else {
                    0
                }

            val battery =
                runCatching {
                    context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
                }.getOrNull()?.takeIf { it.getBooleanExtra(BatteryManager.EXTRA_PRESENT, true) }
            val temperature =
                battery
                    ?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Int.MIN_VALUE)
                    ?.let(DeviceDiagnosticsParser::parseBatteryTemperature)
            val level = battery?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
            val scale = battery?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
            val batteryPercent = DeviceDiagnosticsParser.parseBatteryPercent(level, scale)

            DeviceMetrics(
                totalRamKb = memory.totalMem / 1024,
                usedRamKb = (memory.totalMem - memory.availMem) / 1024,
                freeRamKb = memory.availMem / 1024,
                cpuBusyPercent = cpuBusy,
                load1 = load1,
                storageTotalKb = totalStorage,
                storageUsedKb = usedStorage,
                storageAvailableKb = availableStorage,
                temperatureC = temperature,
                maxThermalStatus = thermalStatus,
                uptimeSeconds = SystemClock.elapsedRealtime() / 1000,
                backgroundProcesses = activity.runningAppProcesses?.size ?: 0,
                batteryPercent = batteryPercent,
                batteryDiagnostics = LocalBatteryReader(context).readDiagnostics(battery),
                cpuFrequencies = DeviceDiagnosticsReader().readCpuFrequencies(cores),
                lowMemoryThresholdKb = memory.threshold / 1024,
                isLowMemory = memory.lowMemory,
            )
        }
}
