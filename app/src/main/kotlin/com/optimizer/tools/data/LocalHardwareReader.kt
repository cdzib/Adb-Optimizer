package com.optimizer.tools.data

import android.app.ActivityManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import dev.adboptimizer.core.model.DeviceHardwareInfo

internal class LocalHardwareReader(
    private val context: Context,
) {
    fun readInfo(): DeviceHardwareInfo =
        DeviceHardwareInfo(
            brand = normalize(Build.BRAND),
            product = normalize(Build.PRODUCT),
            board = normalize(Build.BOARD),
            hardware = normalize(Build.HARDWARE),
            securityPatch = normalize(Build.VERSION.SECURITY_PATCH),
            buildDisplay = normalize(Build.DISPLAY),
            kernelVersion = readOptional { System.getProperty("os.version") },
            virtualMachineVersion = readOptional { System.getProperty("java.vm.version") },
            baseband = readOptional { Build.getRadioVersion() },
            supportedAbis = Build.SUPPORTED_ABIS.toList(),
            socModel = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) normalize(Build.SOC_MODEL) else null,
            socManufacturer = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) normalize(Build.SOC_MANUFACTURER) else null,
            openGlEsVersion =
                readOptional {
                    context.getSystemService(ActivityManager::class.java)?.deviceConfigurationInfo?.glEsVersion
                },
            vulkanVersion =
                readOptional {
                    context.packageManager.systemAvailableFeatures
                        .firstOrNull { it.name == PackageManager.FEATURE_VULKAN_HARDWARE_VERSION }
                        ?.version
                        ?.let(DeviceDiagnosticsParser::parseVulkanVersion)
                },
            supportsUsbHost = runCatching { context.packageManager.hasSystemFeature(PackageManager.FEATURE_USB_HOST) }.getOrNull(),
        )

    private fun readOptional(read: () -> String?): String? = normalize(runCatching(read).getOrNull())

    private fun normalize(value: String?): String? =
        value?.trim()?.takeUnless { it.isEmpty() || it.equals(Build.UNKNOWN, ignoreCase = true) }
}
