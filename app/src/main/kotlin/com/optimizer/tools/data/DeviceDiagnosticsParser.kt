package com.optimizer.tools.data

internal object DeviceDiagnosticsParser {
    fun parseBatteryPercent(
        level: Int,
        scale: Int,
    ): Int = if (scale > 0 && level in 0..scale) (level.toLong() * 100 / scale).toInt() else -1

    fun parseBatteryCurrent(microamps: Int): Float? = microamps.takeUnless { it == Int.MIN_VALUE }?.div(1000f)

    fun parseBatteryTemperature(tenthsCelsius: Int): Float? = tenthsCelsius.takeUnless { it == Int.MIN_VALUE }?.div(10f)

    fun parseBatteryVoltage(millivolts: Int): Float? = millivolts.takeIf { it > 0 }?.div(1000f)

    fun parseVulkanVersion(version: Int): String? {
        if (version <= 0) return null
        return "${(version ushr 22) and 0x7f}.${(version ushr 12) and 0x3ff}.${version and 0xfff}"
    }
}
