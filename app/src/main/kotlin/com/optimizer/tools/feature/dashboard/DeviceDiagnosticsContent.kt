package com.optimizer.tools.feature.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.optimizer.tools.R
import dev.adboptimizer.core.model.BatteryChargeState
import dev.adboptimizer.core.model.BatteryDiagnostics
import dev.adboptimizer.core.model.BatteryHealth
import dev.adboptimizer.core.model.BatteryPowerSource
import dev.adboptimizer.core.model.CpuCoreFrequency
import dev.adboptimizer.core.model.DeviceDisplayInfo
import dev.adboptimizer.core.model.DeviceHardwareInfo
import dev.adboptimizer.core.model.DeviceNetworkInterface
import dev.adboptimizer.core.model.DeviceSensorInfo
import java.util.Locale

@Composable
internal fun HardwareDiagnosticRows(info: DeviceHardwareInfo) {
    DiagnosticRow(R.string.diagnostics_brand, info.brand)
    DiagnosticRow(R.string.diagnostics_product, info.product)
    DiagnosticRow(R.string.diagnostics_board, info.board)
    DiagnosticRow(R.string.diagnostics_hardware, info.hardware)
    DiagnosticRow(
        R.string.diagnostics_usb_host,
        info.supportsUsbHost?.let {
            stringResource(if (it) R.string.diagnostics_yes else R.string.diagnostics_no)
        },
    )
}

@Composable
internal fun SystemDiagnosticRows(info: DeviceHardwareInfo) {
    DiagnosticRow(R.string.diagnostics_security_patch, info.securityPatch)
    DiagnosticRow(R.string.diagnostics_build, info.buildDisplay)
    DiagnosticRow(R.string.diagnostics_kernel, info.kernelVersion)
    DiagnosticRow(R.string.diagnostics_vm, info.virtualMachineVersion)
    DiagnosticRow(R.string.diagnostics_baseband, info.baseband)
    DiagnosticRow(R.string.diagnostics_gles, info.openGlEsVersion)
    DiagnosticRow(R.string.diagnostics_vulkan, info.vulkanVersion)
}

@Composable
internal fun DisplayDiagnosticRows(display: DeviceDisplayInfo?) {
    DiagnosticRow(
        R.string.diagnostics_display_physical,
        formatResolution(display?.physicalWidthPx, display?.physicalHeightPx),
    )
    val activeSize = formatResolution(display?.activeWidthPx, display?.activeHeightPx)
    if (activeSize != null &&
        (display?.activeWidthPx != display?.physicalWidthPx || display?.activeHeightPx != display?.physicalHeightPx)
    ) {
        DiagnosticRow(R.string.diagnostics_display_active, activeSize)
    }
    DiagnosticRow(
        R.string.diagnostics_display_density_physical,
        display?.physicalDensityDpi?.let { stringResource(R.string.diagnostics_dpi, it) },
    )
    val activeDensity = display?.activeDensityDpi
    if (activeDensity != null && activeDensity != display?.physicalDensityDpi) {
        DiagnosticRow(R.string.diagnostics_display_density_active, stringResource(R.string.diagnostics_dpi, activeDensity))
    }
    DiagnosticRow(
        R.string.diagnostics_display_refresh,
        display?.refreshRateHz?.let { stringResource(R.string.diagnostics_hz, it) },
    )
    val modes = display?.modes.orEmpty()
    if (modes.isNotEmpty()) {
        Text(
            stringResource(R.string.diagnostics_display_modes),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        modes
            .groupBy { it.widthPx to it.heightPx }
            .entries
            .sortedByDescending { it.key.first.toLong() * it.key.second }
            .forEach { (size, entries) ->
                val rates =
                    entries
                        .map { it.refreshRateHz }
                        .distinct()
                        .sortedDescending()
                        .joinToString(" / ") { String.format(Locale.getDefault(), "%.1f", it) }
                Text(
                    stringResource(R.string.diagnostics_display_mode_line, size.first, size.second, rates),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
    }
}

@Composable
private fun formatResolution(
    width: Int?,
    height: Int?,
): String? =
    if (width != null && height != null) {
        stringResource(R.string.diagnostics_resolution, width, height)
    } else {
        null
    }

@Composable
internal fun SensorDiagnosticRows(sensors: List<DeviceSensorInfo>) {
    if (sensors.isEmpty()) {
        DiagnosticRow(R.string.diagnostics_sensors, null)
        return
    }
    Text(
        stringResource(R.string.diagnostics_sensor_count, sensors.size),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    sensors.forEach { sensor ->
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(sensor.name, style = MaterialTheme.typography.bodyMedium)
            val detail =
                listOfNotNull(
                    sensor.vendor?.takeIf { it.isNotBlank() },
                    sensor.typeName?.takeIf { it.isNotBlank() },
                ).joinToString(" · ")
            if (detail.isNotBlank()) {
                Text(
                    detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
internal fun NetworkDiagnosticRows(interfaces: List<DeviceNetworkInterface>) {
    if (interfaces.isEmpty()) {
        DiagnosticRow(R.string.diagnostics_network_interfaces, null)
        return
    }
    Text(
        stringResource(R.string.diagnostics_network_count, interfaces.size),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    interfaces.forEach { network ->
        DeviceInfoRow(network.name, network.addresses.joinToString("\n"))
    }
}

@Composable
internal fun CpuDiagnosticRows(
    info: DeviceHardwareInfo?,
    frequencies: List<CpuCoreFrequency>,
) {
    if (info != null) {
        DiagnosticRow(R.string.diagnostics_soc, info.socModel)
        DiagnosticRow(R.string.diagnostics_soc_manufacturer, info.socManufacturer)
        DiagnosticRow(R.string.diagnostics_abis, info.supportedAbis.takeIf { it.isNotEmpty() }?.joinToString())
    }
    if (frequencies.isNotEmpty()) {
        Text(
            stringResource(R.string.diagnostics_cpu_note),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        frequencies.forEach { core ->
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                DeviceInfoRow(stringResource(R.string.diagnostics_core, core.index), formatFrequency(core.currentMhz))
                Text(
                    stringResource(R.string.diagnostics_core_range, formatFrequency(core.minimumMhz), formatFrequency(core.maximumMhz)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun formatFrequency(value: Float?): String =
    value?.let { stringResource(R.string.diagnostics_mhz, it) } ?: stringResource(R.string.dashboard_unavailable)

@Composable
internal fun BatteryDiagnosticRows(battery: BatteryDiagnostics?) {
    DiagnosticRow(
        R.string.diagnostics_battery_temperature,
        battery?.temperatureC?.let { stringResource(R.string.diagnostics_celsius, it) },
    )
    DiagnosticRow(
        R.string.diagnostics_battery_state,
        battery?.let {
            stringResource(
                when (it.chargeState) {
                    BatteryChargeState.CHARGING -> R.string.diagnostics_charging
                    BatteryChargeState.DISCHARGING -> R.string.diagnostics_discharging
                    BatteryChargeState.FULL -> R.string.diagnostics_full
                    BatteryChargeState.NOT_CHARGING -> R.string.diagnostics_not_charging
                    BatteryChargeState.UNKNOWN -> R.string.dashboard_unavailable
                },
            )
        },
    )
    DiagnosticRow(
        R.string.diagnostics_battery_health,
        battery?.let {
            stringResource(
                when (it.health) {
                    BatteryHealth.GOOD -> R.string.diagnostics_good
                    BatteryHealth.COLD -> R.string.diagnostics_cold
                    BatteryHealth.DEAD -> R.string.diagnostics_dead
                    BatteryHealth.OVERHEAT -> R.string.diagnostics_overheat
                    BatteryHealth.OVER_VOLTAGE -> R.string.diagnostics_over_voltage
                    BatteryHealth.FAILURE -> R.string.diagnostics_failure
                    BatteryHealth.UNKNOWN -> R.string.dashboard_unavailable
                },
            )
        },
    )
    DiagnosticRow(
        R.string.diagnostics_battery_source,
        battery?.let {
            stringResource(
                when (it.powerSource) {
                    BatteryPowerSource.BATTERY -> R.string.diagnostics_power_battery
                    BatteryPowerSource.AC -> R.string.diagnostics_power_ac
                    BatteryPowerSource.USB -> R.string.diagnostics_power_usb
                    BatteryPowerSource.WIRELESS -> R.string.diagnostics_power_wireless
                    BatteryPowerSource.DOCK -> R.string.diagnostics_power_dock
                    BatteryPowerSource.UNKNOWN -> R.string.dashboard_unavailable
                },
            )
        },
    )
    DiagnosticRow(R.string.diagnostics_battery_technology, battery?.technology)
    DiagnosticRow(R.string.diagnostics_battery_voltage, battery?.voltageVolts?.let { stringResource(R.string.diagnostics_volts, it) })
    DiagnosticRow(
        R.string.diagnostics_battery_current,
        battery?.currentMilliamps?.let { stringResource(R.string.diagnostics_milliamps, it) },
    )
    DiagnosticRow(R.string.diagnostics_battery_remaining, battery?.remainingChargeMah?.let { stringResource(R.string.diagnostics_mah, it) })
    Text(
        stringResource(R.string.diagnostics_battery_note),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun DiagnosticRow(
    label: Int,
    value: String?,
) {
    DeviceInfoRow(stringResource(label), value ?: stringResource(R.string.dashboard_unavailable))
}
