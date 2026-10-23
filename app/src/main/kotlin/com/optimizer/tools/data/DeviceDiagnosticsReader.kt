package com.optimizer.tools.data

import dev.adboptimizer.core.model.CpuCoreFrequency
import java.io.File

/** Small injectable file boundary: access restrictions are normal on production devices. */
internal class DeviceDiagnosticsReader(
    private val readText: (String) -> String = { File(it).readText() },
) {
    fun readCpuFrequencies(coreCount: Int): List<CpuCoreFrequency> =
        (0 until coreCount.coerceIn(0, 256)).map { index ->
            val directory = "/sys/devices/system/cpu/cpu$index/cpufreq"
            CpuCoreFrequency(
                index = index,
                currentMhz = readFrequency("$directory/scaling_cur_freq"),
                minimumMhz = readFrequency("$directory/cpuinfo_min_freq"),
                maximumMhz = readFrequency("$directory/cpuinfo_max_freq"),
            )
        }

    private fun readFrequency(path: String): Float? =
        try {
            readText(path)
                .trim()
                .toLongOrNull()
                ?.takeIf { it > 0 }
                ?.div(1000f)
        } catch (_: java.io.IOException) {
            null
        } catch (_: SecurityException) {
            null
        }
}
