package com.optimizer.tools.feature.dashboard

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import androidx.compose.ui.unit.dp
import com.optimizer.tools.core.ui.AdbOptimizerTheme
import com.optimizer.tools.domain.model.ShizukuLocalStatus
import com.optimizer.tools.domain.model.ThemeMode
import dev.adboptimizer.core.model.AppTarget
import dev.adboptimizer.core.model.BatteryChargeState
import dev.adboptimizer.core.model.BatteryDiagnostics
import dev.adboptimizer.core.model.BatteryHealth
import dev.adboptimizer.core.model.BatteryPowerSource
import dev.adboptimizer.core.model.CpuCoreFrequency
import dev.adboptimizer.core.model.DeviceDisplayInfo
import dev.adboptimizer.core.model.DeviceHardwareInfo
import dev.adboptimizer.core.model.DeviceInfo
import dev.adboptimizer.core.model.DeviceMetrics
import dev.adboptimizer.core.model.DeviceNetworkInterface
import dev.adboptimizer.core.model.DeviceSensorInfo
import dev.adboptimizer.core.model.DisplayModeInfo

internal class DiagnosticCategoryPreviewProvider : PreviewParameterProvider<DashboardCategory> {
    override val values = DashboardCategory.entries.asSequence()
}

private val diagnosticPreviewDevice =
    DeviceInfo(
        "preview",
        "Sample phone",
        "Example",
        35,
        "15",
        false,
        false,
        "arm64-v8a",
        DeviceHardwareInfo(
            brand = "Example",
            product = "sample",
            board = "sample-board",
            hardware = "sample",
            securityPatch = "2026-01-01",
            buildDisplay = "EXAMPLE.2026.01",
            kernelVersion = "6.1.0-example",
            virtualMachineVersion = "2.1.0",
            supportedAbis = listOf("arm64-v8a", "armeabi-v7a"),
            socModel = "Example SoC",
            socManufacturer = "Example",
            openGlEsVersion = "3.2",
            vulkanVersion = "1.3.274",
            supportsUsbHost = true,
        ),
        displayInfo =
            DeviceDisplayInfo(
                physicalWidthPx = 1440,
                physicalHeightPx = 3088,
                physicalDensityDpi = 600,
                activeWidthPx = 1080,
                activeHeightPx = 2316,
                activeDensityDpi = 480,
                refreshRateHz = 120f,
                modes =
                    listOf(
                        DisplayModeInfo(1440, 3088, 120f),
                        DisplayModeInfo(1440, 3088, 60f),
                        DisplayModeInfo(1080, 2316, 120f),
                        DisplayModeInfo(1080, 2316, 60f),
                    ),
            ),
        sensors =
            listOf(
                DeviceSensorInfo("LSM6DSO Accelerometer", "STMicro", "android.sensor.accelerometer", 1, 15_933),
                DeviceSensorInfo("AK09918 Magnetometer", "akm", "android.sensor.magnetic_field", 2, 146_988),
            ),
        networkInterfaces =
            listOf(
                DeviceNetworkInterface("wlan0", listOf("192.168.1.42/24", "fe80::1/64")),
                DeviceNetworkInterface("rmnet_data0", listOf("10.93.196.18/30")),
            ),
    )
private val diagnosticPreviewMetrics =
    DeviceMetrics(
        totalRamKb = 8_000_000,
        usedRamKb = 3_000_000,
        freeRamKb = 5_000_000,
        batteryPercent = 78,
        temperatureC = 32.7f,
        batteryDiagnostics =
            BatteryDiagnostics(
                BatteryChargeState.DISCHARGING,
                BatteryHealth.GOOD,
                BatteryPowerSource.BATTERY,
                "Li-ion",
                4.125f,
                -125.5f,
                3100f,
                31.8f,
            ),
        cpuFrequencies = listOf(CpuCoreFrequency(0, 1800f, 400f, 2400f), CpuCoreFrequency(1)),
        lowMemoryThresholdKb = 200_000,
        isLowMemory = false,
    )

@Preview(name = "Diagnóstico local", showBackground = true, widthDp = 400, heightDp = 1100)
@Composable
private fun DeviceDiagnosticsPreview(
    @PreviewParameter(DiagnosticCategoryPreviewProvider::class) category: DashboardCategory,
) {
    AdbOptimizerTheme(ThemeMode.DARK) {
        DashboardCategoryContent(
            category,
            diagnosticPreviewDevice,
            diagnosticPreviewMetrics,
            AppTarget.LOCAL,
            ShizukuLocalStatus.NOT_INSTALLED,
            Modifier.padding(16.dp),
        )
    }
}

@Preview(name = "Datos ausentes · texto grande", showBackground = true, widthDp = 360, heightDp = 1400, fontScale = 1.5f)
@Composable
private fun DeviceDiagnosticsUnavailablePreview(
    @PreviewParameter(DiagnosticCategoryPreviewProvider::class) category: DashboardCategory,
) {
    AdbOptimizerTheme(ThemeMode.LIGHT) {
        DashboardCategoryContent(
            category,
            diagnosticPreviewDevice.copy(
                hardwareInfo = null,
                displayInfo = null,
                sensors = emptyList(),
                networkInterfaces = emptyList(),
            ),
            null,
            AppTarget.REMOTE,
            ShizukuLocalStatus.NOT_INSTALLED,
            Modifier.padding(16.dp),
        )
    }
}
