package com.optimizer.tools.data

import android.content.Context
import android.content.Intent
import android.os.BatteryManager
import dev.adboptimizer.core.model.BatteryChargeState
import dev.adboptimizer.core.model.BatteryDiagnostics
import dev.adboptimizer.core.model.BatteryHealth
import dev.adboptimizer.core.model.BatteryPowerSource

internal class LocalBatteryReader(
    private val context: Context,
) {
    fun readDiagnostics(intent: Intent?): BatteryDiagnostics? {
        if (intent == null || !intent.getBooleanExtra(BatteryManager.EXTRA_PRESENT, true)) return null
        val manager = context.getSystemService(BatteryManager::class.java)
        return BatteryDiagnostics(
            chargeState =
                when (intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)) {
                    BatteryManager.BATTERY_STATUS_CHARGING -> BatteryChargeState.CHARGING
                    BatteryManager.BATTERY_STATUS_DISCHARGING -> BatteryChargeState.DISCHARGING
                    BatteryManager.BATTERY_STATUS_FULL -> BatteryChargeState.FULL
                    BatteryManager.BATTERY_STATUS_NOT_CHARGING -> BatteryChargeState.NOT_CHARGING
                    else -> BatteryChargeState.UNKNOWN
                },
            health =
                when (intent.getIntExtra(BatteryManager.EXTRA_HEALTH, -1)) {
                    BatteryManager.BATTERY_HEALTH_GOOD -> BatteryHealth.GOOD
                    BatteryManager.BATTERY_HEALTH_COLD -> BatteryHealth.COLD
                    BatteryManager.BATTERY_HEALTH_DEAD -> BatteryHealth.DEAD
                    BatteryManager.BATTERY_HEALTH_OVERHEAT -> BatteryHealth.OVERHEAT
                    BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> BatteryHealth.OVER_VOLTAGE
                    BatteryManager.BATTERY_HEALTH_UNSPECIFIED_FAILURE -> BatteryHealth.FAILURE
                    else -> BatteryHealth.UNKNOWN
                },
            powerSource =
                when (intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1)) {
                    0 -> BatteryPowerSource.BATTERY
                    BatteryManager.BATTERY_PLUGGED_AC -> BatteryPowerSource.AC
                    BatteryManager.BATTERY_PLUGGED_USB -> BatteryPowerSource.USB
                    BatteryManager.BATTERY_PLUGGED_WIRELESS -> BatteryPowerSource.WIRELESS
                    BatteryManager.BATTERY_PLUGGED_DOCK -> BatteryPowerSource.DOCK
                    else -> BatteryPowerSource.UNKNOWN
                },
            technology = intent.getStringExtra(BatteryManager.EXTRA_TECHNOLOGY)?.takeIf { it.isNotBlank() },
            voltageVolts = DeviceDiagnosticsParser.parseBatteryVoltage(intent.getIntExtra(BatteryManager.EXTRA_VOLTAGE, -1)),
            currentMilliamps =
                readProperty(manager, BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)
                    ?.let(DeviceDiagnosticsParser::parseBatteryCurrent),
            remainingChargeMah =
                readProperty(manager, BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER)
                    ?.takeIf { it >= 0 }
                    ?.div(1000f),
            temperatureC =
                DeviceDiagnosticsParser.parseBatteryTemperature(
                    intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Int.MIN_VALUE),
                ),
        )
    }

    private fun readProperty(
        manager: BatteryManager?,
        property: Int,
    ): Int? = runCatching { manager?.getIntProperty(property) }.getOrNull()?.takeUnless { it == Int.MIN_VALUE }
}
