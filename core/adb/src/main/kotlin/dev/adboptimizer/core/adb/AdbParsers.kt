package dev.adboptimizer.core.adb

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
import dev.adboptimizer.core.shell.AdbShell
import kotlinx.coroutines.CancellationException
import kotlin.math.abs

data class MeminfoParsed(
    val totalKb: Long,
    val freeKb: Long,
    val usedKb: Long,
    val cachedKb: Long,
)

data class CpuinfoParsed(
    val load1: Float,
    val load5: Float,
    val load15: Float,
    val busyPercent: Float,
)

data class StorageParsed(
    val totalKb: Long,
    val usedKb: Long,
    val availableKb: Long,
    val usedPercent: Float,
)

data class ThermalParsed(
    val maxStatus: Int,
    val temperatures: List<Temperature>,
)

data class Temperature(
    val name: String,
    val value: Float,
)

data class BatteryParsed(
    val percent: Int,
    val diagnostics: BatteryDiagnostics,
)

/**
 * Parsers puros. Deben coincidir con desktop/src/main/adb/adb-client.ts
 * validados por shared/conformance/cases.
 */
object AdbParsers {
    private val GETPROP = Regex("""\[([^]]+)]:\s*\[([^]]*)]""")
    private val TOTAL_RAM = Regex("""Total RAM:\s*([\d,]+)K""", RegexOption.IGNORE_CASE)
    private val FREE_RAM = Regex("""Free RAM:\s*([\d,]+)K\s*\(([^)]*)\)""", RegexOption.IGNORE_CASE)
    private val CACHED_PSS = Regex("""([\d,]+)K cached pss""", RegexOption.IGNORE_CASE)
    private val LOAD = Regex("""Load:\s*([\d.]+)\s*/\s*([\d.]+)\s*/\s*([\d.]+)""", RegexOption.IGNORE_CASE)
    private val TOTAL_CPU = Regex("""([\d.]+)%\s*TOTAL""", RegexOption.IGNORE_CASE)
    private val THERMAL_STATUS = Regex("""Thermal Status:\s*(\d+)""", RegexOption.IGNORE_CASE)
    private val TEMPERATURE = Regex("""Temperature\{[^}]*mValue=([\d.]+)[^}]*mName=([^,}]+)""")
    private val WM_SIZE = Regex("""(Physical|Override) size:\s*(\d+)x(\d+)""")
    private val WM_DENSITY = Regex("""(Physical|Override) density:\s*(\d+)""")
    private val DISPLAY_SIZE_FALLBACK = Regex("""DisplayDeviceInfo\{[^}]*?(\d{3,5})\s+x\s+(\d{3,5}),""")
    private val RENDER_FRAME_RATE = Regex("""renderFrameRate\s+([\d.]+)""")
    private val SENSOR_LINE =
        Regex(
            """^0x[0-9a-fA-F]+\)\s*(.+?)\s*\|\s*(.+?)\s*\|\s*ver:\s*(\d+)\s*\|\s*type:\s*([^(|]+)\((\d+)\)""",
        )
    private val NETWORK_ADDRESS = Regex("""^\d+:\s+(\S+)\s+inet6?\s+(\S+).*scope\s+(\S+)""")
    private val VULKAN_FEATURE = Regex("""android\.hardware\.vulkan\.version=(\d+)""")

    fun parseGetprop(raw: String): Map<String, String> =
        GETPROP.findAll(raw).associate { match ->
            (match.groupValues[1]) to (match.groupValues[2])
        }

    fun parseMeminfo(raw: String): MeminfoParsed {
        val total =
            TOTAL_RAM
                .find(raw)
                ?.groupValues
                ?.get(1)
                ?.toNumber() ?: 0L
        val freeMatch = FREE_RAM.find(raw)
        val free = freeMatch?.groupValues?.get(1)?.toNumber() ?: 0L
        val cached =
            freeMatch
                ?.groupValues
                ?.get(2)
                ?.let { CACHED_PSS.find(it)?.groupValues?.get(1) }
                ?.toNumber() ?: 0L
        return MeminfoParsed(total, free, (total - free).coerceAtLeast(0), cached)
    }

    fun parseCpuinfo(raw: String): CpuinfoParsed {
        val load = LOAD.find(raw)?.groupValues
        return CpuinfoParsed(
            load1 = load?.getOrNull(1)?.toFloatOrNull() ?: 0f,
            load5 = load?.getOrNull(2)?.toFloatOrNull() ?: 0f,
            load15 = load?.getOrNull(3)?.toFloatOrNull() ?: 0f,
            busyPercent =
                TOTAL_CPU
                    .find(raw)
                    ?.groupValues
                    ?.get(1)
                    ?.toFloatOrNull() ?: 0f,
        )
    }

    fun parseStorage(raw: String): StorageParsed {
        // `df /data` puede reportar el punto de montaje como /data o /data/user/0
        // segun el fabricante/version de Android.
        val line =
            raw
                .lineSequence()
                .map { it.trim() }
                .firstOrNull {
                    (it.startsWith("/dev/") || it.startsWith("Filesystem")) &&
                        (it.endsWith("/data") || it.contains("/data/"))
                }
                ?: return StorageParsed(0, 0, 0, 0f)

        val parts = line.split(Regex("""\s+"""))
        return StorageParsed(
            totalKb = parts.getOrNull(1)?.toNumber() ?: 0L,
            usedKb = parts.getOrNull(2)?.toNumber() ?: 0L,
            availableKb = parts.getOrNull(3)?.toNumber() ?: 0L,
            usedPercent = parts.getOrNull(4)?.replace("%", "")?.toFloatOrNull() ?: 0f,
        )
    }

    fun parseThermal(raw: String): ThermalParsed {
        val statuses = THERMAL_STATUS.findAll(raw).mapNotNull { it.groupValues[1].toIntOrNull() }.toList()
        val temperatures =
            TEMPERATURE
                .findAll(raw)
                .map { match ->
                    Temperature(
                        name = match.groupValues[2].trim(),
                        value = match.groupValues[1].toFloatOrNull() ?: 0f,
                    )
                }.toList()
        return ThermalParsed(statuses.maxOrNull() ?: 0, temperatures)
    }

    fun parseUptimeSeconds(raw: String): Long =
        raw
            .trim()
            .split(Regex("""\s+"""))
            .firstOrNull()
            ?.toDoubleOrNull()
            ?.toLong() ?: 0L

    fun parseBackgroundProcesses(raw: String): Int = raw.trim().toIntOrNull() ?: 0

    fun parseDeviceInfo(
        serial: String,
        getprop: String,
        hasRoot: Boolean,
    ): DeviceInfo {
        val props = parseGetprop(getprop)
        val characteristics = props["ro.build.characteristics"].orEmpty()
        return DeviceInfo(
            id = serial,
            model = props["ro.product.model"] ?: props["ro.product.device"] ?: "Android",
            manufacturer = props["ro.product.manufacturer"] ?: "unknown",
            sdk = props["ro.build.version.sdk"]?.toIntOrNull() ?: 0,
            androidVersion = props["ro.build.version.release"] ?: "?",
            isTv = characteristics.contains("tv", ignoreCase = true),
            hasRoot = hasRoot,
            abi = props["ro.product.cpu.abi"] ?: "?",
        )
    }

    /** Hardware and system facts available through getprop/features, never inferred locally. */
    fun parseHardwareInfo(
        getprop: String,
        features: String,
        kernelVersion: String?,
    ): DeviceHardwareInfo {
        val props = parseGetprop(getprop)
        val abis =
            props["ro.product.cpu.abilist"]
                ?.split(',')
                ?.map { it.trim() }
                ?.filter { it.isNotEmpty() }
                ?.takeIf { it.isNotEmpty() }
                ?: listOfNotNull(props["ro.product.cpu.abi"]?.trim()?.takeIf { it.isNotEmpty() })
        return DeviceHardwareInfo(
            brand = props.normalized("ro.product.brand"),
            product = props.normalized("ro.product.name") ?: props.normalized("ro.product.device"),
            board = props.normalized("ro.product.board"),
            hardware = props.normalized("ro.hardware"),
            securityPatch = props.normalized("ro.build.version.security_patch"),
            buildDisplay = props.normalized("ro.build.display.id"),
            kernelVersion = kernelVersion?.trim()?.takeIf { it.isNotEmpty() },
            virtualMachineVersion = props.normalized("java.vm.version"),
            baseband = props.normalized("gsm.version.baseband") ?: props.normalized("ro.baseband"),
            supportedAbis = abis,
            socModel = props.normalized("ro.soc.model"),
            socManufacturer = props.normalized("ro.soc.manufacturer"),
            openGlEsVersion = decodeOpenGlEsVersion(props["ro.opengles.version"]),
            vulkanVersion = decodeVulkanVersion(featureInt(features, VULKAN_FEATURE)),
            supportsUsbHost = features.lineSequence().any { it.trim() == "feature:android.hardware.usb.host" },
        )
    }

    fun parseDisplayInfo(raw: String): DeviceDisplayInfo? {
        val sizes = WM_SIZE.findAll(raw).associate { it.groupValues[1] to (it.groupValues[2].toInt() to it.groupValues[3].toInt()) }
        val densities = WM_DENSITY.findAll(raw).associate { it.groupValues[1] to it.groupValues[2].toInt() }
        val fallback = DISPLAY_SIZE_FALLBACK.find(raw)?.let { it.groupValues[1].toInt() to it.groupValues[2].toInt() }
        val physicalSize = sizes["Physical"] ?: fallback
        val overrideSize = sizes["Override"]
        val physicalDensity = densities["Physical"]
        val overrideDensity = densities["Override"]
        if (physicalSize == null && physicalDensity == null && overrideSize == null && overrideDensity == null) return null
        val activeSize = overrideSize ?: physicalSize
        return DeviceDisplayInfo(
            physicalWidthPx = physicalSize?.first,
            physicalHeightPx = physicalSize?.second,
            physicalDensityDpi = physicalDensity,
            activeWidthPx = activeSize?.first,
            activeHeightPx = activeSize?.second,
            activeDensityDpi = overrideDensity ?: physicalDensity,
            refreshRateHz =
                RENDER_FRAME_RATE
                    .find(raw)
                    ?.groupValues
                    ?.get(1)
                    ?.toFloatOrNull(),
        )
    }

    fun parseSensors(raw: String): List<DeviceSensorInfo> =
        raw
            .lineSequence()
            .mapNotNull { line -> SENSOR_LINE.find(line.trim()) }
            .map { match ->
                DeviceSensorInfo(
                    name = match.groupValues[1].normalizedWhitespace(),
                    vendor = match.groupValues[2].normalizedWhitespace(),
                    typeName = match.groupValues[4].trim(),
                    typeCode = match.groupValues[5].toIntOrNull(),
                    version = match.groupValues[3].toIntOrNull(),
                )
            }.distinctBy { it.name to it.typeCode }
            .toList()

    fun parseNetworkInterfaces(raw: String): List<DeviceNetworkInterface> {
        val addresses = linkedMapOf<String, MutableList<String>>()
        raw.lineSequence().forEach { line ->
            val match = NETWORK_ADDRESS.find(line.trim()) ?: return@forEach
            val name = match.groupValues[1].substringBefore('@')
            if (name == "lo" || match.groupValues[3] == "host") return@forEach
            addresses.getOrPut(name) { mutableListOf() }.add(match.groupValues[2])
        }
        return addresses.map { (name, values) ->
            DeviceNetworkInterface(name, values.distinct())
        }
    }

    fun parseBattery(raw: String): BatteryParsed? {
        val values =
            raw
                .lineSequence()
                .mapNotNull { line ->
                    val separator = line.indexOf(':')
                    if (separator <= 0) {
                        null
                    } else {
                        line.substring(0, separator).trim().lowercase() to line.substring(separator + 1).trim()
                    }
                }.toMap()
        if (values.isEmpty() || values["present"] == "false") return null
        val level = values["level"]?.toIntOrNull() ?: -1
        val scale = values["scale"]?.toIntOrNull() ?: -1
        val chargeCounter = values["charge counter"]?.toLongOrNull()?.takeIf { it > 0 }
        val current = values["current now"]?.toLongOrNull()?.takeIf { abs(it) >= 1_000L }
        return BatteryParsed(
            percent = if (scale > 0 && level in 0..scale) (level.toLong() * 100 / scale).toInt() else -1,
            diagnostics =
                BatteryDiagnostics(
                    chargeState =
                        when (values["status"]?.toIntOrNull()) {
                            2 -> BatteryChargeState.CHARGING
                            3 -> BatteryChargeState.DISCHARGING
                            4 -> BatteryChargeState.NOT_CHARGING
                            5 -> BatteryChargeState.FULL
                            else -> BatteryChargeState.UNKNOWN
                        },
                    health =
                        when (values["health"]?.toIntOrNull()) {
                            2 -> BatteryHealth.GOOD
                            3 -> BatteryHealth.OVERHEAT
                            4 -> BatteryHealth.DEAD
                            5 -> BatteryHealth.OVER_VOLTAGE
                            6 -> BatteryHealth.FAILURE
                            7 -> BatteryHealth.COLD
                            else -> BatteryHealth.UNKNOWN
                        },
                    powerSource =
                        when {
                            values["ac powered"] == "true" -> BatteryPowerSource.AC
                            values["usb powered"] == "true" -> BatteryPowerSource.USB
                            values["wireless powered"] == "true" -> BatteryPowerSource.WIRELESS
                            values["dock powered"] == "true" -> BatteryPowerSource.DOCK
                            values["ac powered"] == "false" -> BatteryPowerSource.BATTERY
                            else -> BatteryPowerSource.UNKNOWN
                        },
                    technology = values["technology"]?.takeIf { it.isNotEmpty() && it != "null" },
                    voltageVolts = values["voltage"]?.toLongOrNull()?.takeIf { it > 0 }?.div(1000f),
                    currentMilliamps = current?.div(1000f),
                    remainingChargeMah = chargeCounter?.div(1000f),
                    temperatureC = values["temperature"]?.toLongOrNull()?.takeIf { it != Long.MIN_VALUE }?.div(10f),
                ),
        )
    }

    fun parseCpuFrequencies(raw: String): List<CpuCoreFrequency> =
        raw
            .lineSequence()
            .mapNotNull { line ->
                val parts = line.split('|')
                val index = parts.getOrNull(0)?.trim()?.toIntOrNull() ?: return@mapNotNull null

                fun frequency(position: Int): Float? =
                    parts
                        .getOrNull(position)
                        ?.trim()
                        ?.toLongOrNull()
                        ?.takeIf { it > 0 }
                        ?.div(1000f)
                CpuCoreFrequency(
                    index = index,
                    currentMhz = frequency(1),
                    minimumMhz = frequency(2),
                    maximumMhz = frequency(3),
                )
            }.distinctBy { it.index }
            .sortedBy { it.index }
            .toList()

    fun decodeOpenGlEsVersion(raw: String?): String? {
        val value = raw?.trim()?.toIntOrNull()?.takeIf { it > 0 } ?: return null
        return "${value ushr 16}.${value and 0xffff}"
    }

    fun decodeVulkanVersion(version: Int?): String? {
        if (version == null || version <= 0) return null
        return "${(version ushr 22) and 0x7f}.${(version ushr 12) and 0x3ff}.${version and 0xfff}"
    }

    private fun Map<String, String>.normalized(key: String): String? =
        this[key]?.trim()?.takeUnless { it.isEmpty() || it.equals("unknown", ignoreCase = true) }

    private fun featureInt(
        features: String,
        pattern: Regex,
    ): Int? =
        pattern
            .find(features)
            ?.groupValues
            ?.get(1)
            ?.toIntOrNull()

    private fun String.normalizedWhitespace(): String = replace(Regex("""\s+"""), " ").trim()

    fun buildMetrics(
        meminfo: String,
        cpuinfo: String,
        storage: String,
        thermal: String,
        uptime: String,
        processes: String,
    ): DeviceMetrics {
        val memory = parseMeminfo(meminfo)
        val cpu = parseCpuinfo(cpuinfo)
        val disk = parseStorage(storage)
        val heat = parseThermal(thermal)

        return DeviceMetrics(
            totalRamKb = memory.totalKb,
            usedRamKb = memory.usedKb,
            freeRamKb = memory.freeKb,
            cachedKb = memory.cachedKb,
            cpuBusyPercent = cpu.busyPercent,
            load1 = cpu.load1,
            storageTotalKb = disk.totalKb,
            storageUsedKb = disk.usedKb,
            storageAvailableKb = disk.availableKb,
            // Muchos fabricantes no publican "CPU": usan AP (Application Processor)
            // o SOC. Si no hay coincidencia, se toma la primera lectura valida.
            temperatureC =
                heat.temperatures
                    .firstOrNull {
                        it.name.contains("cpu", true) ||
                            it.name.equals("AP", true) ||
                            it.name.contains("soc", true)
                    }?.value ?: heat.temperatures.firstOrNull { it.value > 0f }?.value,
            maxThermalStatus = heat.maxStatus,
            uptimeSeconds = parseUptimeSeconds(uptime),
            backgroundProcesses = parseBackgroundProcesses(processes),
        )
    }

    private fun String.toNumber(): Long = replace(",", "").toLongOrNull() ?: 0L
}

/** Lecturas de alto nivel sobre el dispositivo conectado. */
class DeviceRepository(
    private val shell: AdbShell,
) {
    suspend fun readInfo(serial: String): DeviceInfo {
        val props = shell.exec("getprop").stdout
        val rootProbe = runCatching { shell.exec("which su || echo no-su", 5_000).stdout }.getOrDefault("")
        val info =
            AdbParsers.parseDeviceInfo(
                serial = serial,
                getprop = props,
                hasRoot = rootProbe.contains("/su") && !rootProbe.contains("no-su"),
            )
        val features = optional("pm list features") ?: ""
        return info.copy(
            hardwareInfo =
                AdbParsers.parseHardwareInfo(
                    getprop = props,
                    features = features,
                    kernelVersion = optional("uname -r"),
                ),
            displayInfo =
                optional("wm size; wm density; dumpsys display | grep -m 1 'DisplayDeviceInfo{'")
                    ?.let(AdbParsers::parseDisplayInfo),
            sensors =
                optional("dumpsys sensorservice | sed -n '/Sensor List:/,\$p'", 12_000L)
                    ?.let(AdbParsers::parseSensors)
                    .orEmpty(),
            networkInterfaces =
                optional("ip -o addr show up")
                    ?.let(AdbParsers::parseNetworkInterfaces)
                    .orEmpty(),
        )
    }

    suspend fun readMetrics(): DeviceMetrics {
        val metrics =
            AdbParsers.buildMetrics(
                meminfo = shell.exec("dumpsys meminfo").stdout,
                cpuinfo = shell.exec("dumpsys cpuinfo").stdout,
                storage = shell.exec("df /data").stdout,
                thermal = shell.exec("dumpsys thermalservice").stdout,
                uptime = shell.exec("cat /proc/uptime").stdout,
                processes =
                    runCatching {
                        shell.exec("dumpsys activity processes | grep -c \"ProcessRecord\"").stdout
                    }.getOrDefault(""),
            )
        val battery = optional("dumpsys battery")?.let(AdbParsers::parseBattery)
        return metrics.copy(
            batteryPercent = battery?.percent ?: -1,
            batteryDiagnostics = battery?.diagnostics,
            cpuFrequencies = optional(CPU_FREQUENCY_COMMAND, 8_000L)?.let(AdbParsers::parseCpuFrequencies).orEmpty(),
        )
    }

    private suspend fun optional(
        command: String,
        timeoutMs: Long = 8_000L,
    ): String? =
        try {
            shell.exec(command, timeoutMs).stdout
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            null
        }

    private companion object {
        const val CPU_FREQUENCY_COMMAND =
            "for d in /sys/devices/system/cpu/cpu[0-9]*/cpufreq; do " +
                "[ -d \"\$d\" ] || continue; " +
                "i=\${d#/sys/devices/system/cpu/cpu}; i=\${i%/cpufreq}; " +
                "printf '%s|%s|%s|%s\\n' \"\$i\" " +
                "\"\$(cat \"\$d/scaling_cur_freq\" 2>/dev/null)\" " +
                "\"\$(cat \"\$d/cpuinfo_min_freq\" 2>/dev/null)\" " +
                "\"\$(cat \"\$d/cpuinfo_max_freq\" 2>/dev/null)\"; done"
    }
}
