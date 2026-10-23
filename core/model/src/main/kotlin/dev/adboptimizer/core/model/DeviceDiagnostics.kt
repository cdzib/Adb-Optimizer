package dev.adboptimizer.core.model

/** Optional facts about the selected device, never inferred from the controller phone. */
data class DeviceHardwareInfo(
    val brand: String? = null,
    val product: String? = null,
    val board: String? = null,
    val hardware: String? = null,
    val securityPatch: String? = null,
    val buildDisplay: String? = null,
    val kernelVersion: String? = null,
    val virtualMachineVersion: String? = null,
    val baseband: String? = null,
    val supportedAbis: List<String> = emptyList(),
    val socModel: String? = null,
    val socManufacturer: String? = null,
    val openGlEsVersion: String? = null,
    val vulkanVersion: String? = null,
    val supportsUsbHost: Boolean? = null,
)

/** Frequency is not CPU utilization. Null means unavailable (including offline cores). */
data class CpuCoreFrequency(
    val index: Int,
    val currentMhz: Float? = null,
    val minimumMhz: Float? = null,
    val maximumMhz: Float? = null,
)

/** Physical panel facts and the effective size/density, which may be overridden by ADB. */
data class DeviceDisplayInfo(
    val physicalWidthPx: Int? = null,
    val physicalHeightPx: Int? = null,
    val physicalDensityDpi: Int? = null,
    val activeWidthPx: Int? = null,
    val activeHeightPx: Int? = null,
    val activeDensityDpi: Int? = null,
    val refreshRateHz: Float? = null,
    val modes: List<DisplayModeInfo> = emptyList(),
)

data class DisplayModeInfo(
    val widthPx: Int,
    val heightPx: Int,
    val refreshRateHz: Float,
)

/** Inventory only: the app never starts sampling these sensors. */
data class DeviceSensorInfo(
    val name: String,
    val vendor: String? = null,
    val typeName: String? = null,
    val typeCode: Int? = null,
    val version: Int? = null,
)

data class DeviceNetworkInterface(
    val name: String,
    val addresses: List<String> = emptyList(),
)

enum class BatteryChargeState { CHARGING, DISCHARGING, FULL, NOT_CHARGING, UNKNOWN }

enum class BatteryHealth { GOOD, COLD, DEAD, OVERHEAT, OVER_VOLTAGE, FAILURE, UNKNOWN }

enum class BatteryPowerSource { BATTERY, AC, USB, WIRELESS, DOCK, UNKNOWN }

data class BatteryDiagnostics(
    val chargeState: BatteryChargeState = BatteryChargeState.UNKNOWN,
    val health: BatteryHealth = BatteryHealth.UNKNOWN,
    val powerSource: BatteryPowerSource = BatteryPowerSource.UNKNOWN,
    val technology: String? = null,
    val voltageVolts: Float? = null,
    val currentMilliamps: Float? = null,
    /** Remaining charge, not the rated/design capacity. */
    val remainingChargeMah: Float? = null,
    /** Battery cell temperature, distinct from the CPU/AP thermal reading. */
    val temperatureC: Float? = null,
)
