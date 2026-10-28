package com.optimizer.tools.feature.dashboard

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.BatteryFull
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.optimizer.tools.R
import com.optimizer.tools.core.ui.DesignInfoRow
import com.optimizer.tools.core.ui.DesignTone
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

/** Fila clave/valor de un bloque de diagnostico, con el icono tonal del bloque. */
@Composable
private fun InfoRows(
    rows: List<Pair<Int, String?>>,
    icon: ImageVector,
    tone: DesignTone,
) {
    rows.forEachIndexed { index, (label, value) ->
        DesignInfoRow(
            icon = icon,
            title = stringResource(label),
            value = value ?: stringResource(R.string.dashboard_unavailable),
            tone = tone,
            isFirst = index == 0,
        )
    }
}

/** Placa y hardware del equipo. */
@Composable
internal fun HardwareDiagnosticRows(info: DeviceHardwareInfo) {
    InfoRows(
        rows =
            listOf(
                R.string.diagnostics_board to info.board,
                R.string.diagnostics_hardware to info.hardware,
            ),
        icon = Icons.Filled.Smartphone,
        tone = DesignTone.Neutral,
    )
}

@Composable
internal fun SystemDiagnosticRows(info: DeviceHardwareInfo) {
    InfoRows(
        rows =
            listOf(
                R.string.diagnostics_security_patch to info.securityPatch,
                R.string.diagnostics_build to info.buildDisplay,
                R.string.diagnostics_kernel to info.kernelVersion,
                R.string.diagnostics_vm to info.virtualMachineVersion,
                R.string.diagnostics_baseband to info.baseband,
                R.string.diagnostics_gles to info.openGlEsVersion,
                R.string.diagnostics_vulkan to info.vulkanVersion,
            ),
        icon = Icons.Filled.Layers,
        tone = DesignTone.Green,
    )
}

@Composable
internal fun DisplayDiagnosticRows(display: DeviceDisplayInfo?) {
    val rows = mutableListOf<Pair<Int, String?>>()
    rows += R.string.diagnostics_display_physical to formatResolution(display?.physicalWidthPx, display?.physicalHeightPx)
    val activeSize = formatResolution(display?.activeWidthPx, display?.activeHeightPx)
    if (activeSize != null &&
        (display?.activeWidthPx != display?.physicalWidthPx || display?.activeHeightPx != display?.physicalHeightPx)
    ) {
        rows += R.string.diagnostics_display_active to activeSize
    }
    rows +=
        R.string.diagnostics_display_density_physical to
        display?.physicalDensityDpi?.let { stringResource(R.string.diagnostics_dpi, it) }
    val activeDensity = display?.activeDensityDpi
    if (activeDensity != null && activeDensity != display?.physicalDensityDpi) {
        rows += R.string.diagnostics_display_density_active to stringResource(R.string.diagnostics_dpi, activeDensity)
    }
    rows +=
        R.string.diagnostics_display_refresh to
        display?.refreshRateHz?.let { stringResource(R.string.diagnostics_hz, it) }
    InfoRows(rows, Icons.Filled.AspectRatio, DesignTone.Cyan)

    val modes = display?.modes.orEmpty()
    if (modes.isNotEmpty()) {
        Text(
            stringResource(R.string.diagnostics_display_modes),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
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
                DesignInfoRow(
                    icon = Icons.Filled.AspectRatio,
                    title = "${size.first} × ${size.second} px",
                    value = "$rates Hz",
                    tone = DesignTone.Cyan,
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
        InfoRows(
            rows = listOf(R.string.diagnostics_sensors to null),
            icon = Icons.Filled.Sensors,
            tone = DesignTone.Amber,
        )
        return
    }
    DesignInfoRow(
        icon = Icons.Filled.Sensors,
        title = stringResource(R.string.diagnostics_sensors),
        value = sensors.size.toString(),
        tone = DesignTone.Amber,
        isFirst = true,
    )
    sensors.forEach { sensor ->
        DesignInfoRow(
            icon = Icons.Filled.Sensors,
            title = sensor.name,
            subtitle =
                listOfNotNull(
                    sensor.vendor?.takeIf { it.isNotBlank() },
                    sensor.typeName?.takeIf { it.isNotBlank() },
                ).joinToString(" · ").takeIf { it.isNotBlank() },
            value = null,
            tone = DesignTone.Amber,
        )
    }
}

@Composable
internal fun NetworkDiagnosticRows(interfaces: List<DeviceNetworkInterface>) {
    if (interfaces.isEmpty()) {
        InfoRows(
            rows = listOf(R.string.diagnostics_network_interfaces to null),
            icon = Icons.Filled.Wifi,
            tone = DesignTone.Cyan,
        )
        return
    }
    DesignInfoRow(
        icon = Icons.Filled.Wifi,
        title = stringResource(R.string.diagnostics_network_interfaces),
        value = interfaces.size.toString(),
        tone = DesignTone.Cyan,
        isFirst = true,
    )
    interfaces.forEach { network ->
        DesignInfoRow(
            icon = Icons.Filled.Wifi,
            title = network.name,
            value = network.addresses.joinToString("\n").takeIf { it.isNotBlank() },
            tone = DesignTone.Cyan,
        )
    }
}

@Composable
internal fun CpuCoreRows(frequencies: List<CpuCoreFrequency>) {
    if (frequencies.isEmpty()) {
        InfoRows(
            rows = listOf(R.string.dashboard_cores to null),
            icon = Icons.Filled.Memory,
            tone = DesignTone.Red,
        )
        return
    }
    frequencies.forEachIndexed { index, core ->
        DesignInfoRow(
            icon = Icons.Filled.Memory,
            title = stringResource(R.string.diagnostics_core, core.index),
            subtitle = "${formatFrequency(core.minimumMhz)} – ${formatFrequency(core.maximumMhz)}",
            value = formatFrequency(core.currentMhz),
            tone = DesignTone.Red,
            isFirst = index == 0,
        )
    }
}

@Composable
private fun formatFrequency(value: Float?): String =
    value?.let { stringResource(R.string.diagnostics_mhz, it) } ?: stringResource(R.string.dashboard_unavailable)

@Composable
internal fun BatteryDiagnosticRows(battery: BatteryDiagnostics?) {
    InfoRows(
        rows =
            listOf(
                R.string.diagnostics_battery_temperature to
                    battery?.temperatureC?.let { stringResource(R.string.diagnostics_celsius, it) },
                R.string.diagnostics_battery_state to
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
                R.string.diagnostics_battery_health to
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
                R.string.diagnostics_battery_source to
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
                R.string.diagnostics_battery_technology to battery?.technology,
                R.string.diagnostics_battery_voltage to battery?.voltageVolts?.let { stringResource(R.string.diagnostics_volts, it) },
                R.string.diagnostics_battery_current to
                    battery?.currentMilliamps?.let { stringResource(R.string.diagnostics_milliamps, it) },
                R.string.diagnostics_battery_remaining to
                    battery?.remainingChargeMah?.let { stringResource(R.string.diagnostics_mah, it) },
            ),
        icon = Icons.Filled.BatteryFull,
        tone = DesignTone.Amber,
    )
    Text(
        stringResource(R.string.diagnostics_battery_note),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
    )
}
