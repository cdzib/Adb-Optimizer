package dev.adboptimizer.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Parser estricto a proposito: si el catalogo compartido cambia de forma,
 * la app falla al arrancar en lugar de ejecutar comandos mal formados.
 */
val CatalogJson: Json =
    Json {
        ignoreUnknownKeys = false
        explicitNulls = false
        encodeDefaults = true
        prettyPrint = false
    }

@Serializable
enum class Risk {
    @SerialName("safe")
    SAFE,

    @SerialName("moderate")
    MODERATE,

    @SerialName("advanced")
    ADVANCED,
}

@Serializable
enum class Category {
    @SerialName("performance")
    PERFORMANCE,

    @SerialName("storage")
    STORAGE,

    @SerialName("battery")
    BATTERY,

    @SerialName("tv")
    TV,

    @SerialName("privacy")
    PRIVACY,

    @SerialName("startup")
    STARTUP,
}

@Serializable
enum class Impact {
    @SerialName("low")
    LOW,

    @SerialName("medium")
    MEDIUM,

    @SerialName("high")
    HIGH,
}

@Serializable
data class VerifySpec(
    val run: String,
    @SerialName("equals") val expected: String? = null,
    val contains: String? = null,
    val matches: String? = null,
    val expectEmptyOrExitCode: Int? = null,
)

@Serializable
enum class RecipeStepType {
    @SerialName("shell")
    SHELL,

    @SerialName("installSystemApk")
    INSTALL_SYSTEM_APK,
}

@Serializable
data class RecipeStep(
    val type: RecipeStepType = RecipeStepType.SHELL,
    val run: String,
    val verify: VerifySpec,
    val undo: String? = null,
    val undoIfUnset: String? = null,
    val timeoutMs: Long,
    val optional: Boolean = false,
    val apkPath: String? = null,
    val targetPath: String? = null,
    val mode: String? = null,
    val owner: String? = null,
    val selinuxContext: String? = null,
    val disablePackages: List<String>? = null,
)

@Serializable
data class AppliesTo(
    val minSdk: Int? = null,
    val maxSdk: Int? = null,
    val isTv: Boolean? = null,
    val manufacturers: List<String>? = null,
)

@Serializable
data class Recipe(
    val id: String,
    val category: Category,
    val risk: Risk,
    val requiresRoot: Boolean,
    val impact: Impact,
    val appliesTo: AppliesTo = AppliesTo(),
    val titleKey: String,
    val summaryKey: String,
    val steps: List<RecipeStep>,
)

@Serializable
enum class ParamKind {
    @SerialName("package")
    PACKAGE,

    @SerialName("none")
    NONE,
}

@Serializable
data class ActionSpec(
    val id: String,
    val titleKey: String,
    val paramKind: ParamKind,
    val risk: Risk,
    val run: String,
    val verify: VerifySpec,
    val undo: String? = null,
    val timeoutMs: Long,
    val minSdk: Int? = null,
    val blocklist: List<String>? = null,
)

@Serializable
data class Catalog(
    val schemaVersion: Int,
    val catalogVersion: String,
    val note: String? = null,
    val recipes: List<Recipe>,
)

@Serializable
data class ActionCatalog(
    val schemaVersion: Int,
    val note: String? = null,
    val actions: List<ActionSpec>,
)

@Serializable
enum class StepStatus { PENDING, RUNNING, OK, FAILED, SKIPPED, UNSUPPORTED }

@Serializable
enum class RunStatus { OK, PARTIAL, FAILED, UNSUPPORTED }

@Serializable
data class StepResult(
    val index: Int,
    val run: String,
    val status: StepStatus,
    val output: String = "",
    val error: String? = null,
    val previousValue: String? = null,
    val durationMs: Long = 0L,
)

@Serializable
data class RecipeRun(
    val recipeId: String,
    val status: RunStatus,
    val steps: List<StepResult>,
    val startedAt: String,
    val finishedAt: String,
    val dryRun: Boolean,
)

@Serializable
data class JournalStep(
    val index: Int,
    val run: String,
    val undo: String? = null,
    val undoIfUnset: String? = null,
    val previousValue: String? = null,
    val output: String = "",
    val reversible: Boolean = false,
)

@Serializable
data class JournalEntry(
    val id: String,
    val deviceId: String,
    val deviceModel: String,
    val recipeId: String,
    val titleKey: String,
    val risk: Risk,
    val appliedAt: String,
    val revertedAt: String? = null,
    val steps: List<JournalStep>,
)

data class DeviceInfo(
    val id: String,
    val model: String,
    val manufacturer: String,
    val sdk: Int,
    val androidVersion: String,
    val isTv: Boolean,
    val hasRoot: Boolean,
    val abi: String,
    val hardwareInfo: DeviceHardwareInfo? = null,
    val displayInfo: DeviceDisplayInfo? = null,
    val sensors: List<DeviceSensorInfo> = emptyList(),
    val networkInterfaces: List<DeviceNetworkInterface> = emptyList(),
) {
    val displayName: String get() = "$manufacturer $model".trim()
}

data class DeviceMetrics(
    val totalRamKb: Long = 0,
    val usedRamKb: Long = 0,
    val freeRamKb: Long = 0,
    val cachedKb: Long = 0,
    val cpuBusyPercent: Float = 0f,
    val load1: Float = 0f,
    val storageTotalKb: Long = 0,
    val storageUsedKb: Long = 0,
    val storageAvailableKb: Long = 0,
    val temperatureC: Float? = null,
    val maxThermalStatus: Int = 0,
    val uptimeSeconds: Long = 0,
    val backgroundProcesses: Int = 0,
    /** Nivel de bateria 0..100; -1 cuando no aplica o no se pudo leer. */
    val batteryPercent: Int = -1,
    val batteryDiagnostics: BatteryDiagnostics? = null,
    val cpuFrequencies: List<CpuCoreFrequency> = emptyList(),
    val lowMemoryThresholdKb: Long? = null,
    val isLowMemory: Boolean? = null,
) {
    val ramRatio: Float get() = if (totalRamKb > 0) usedRamKb.toFloat() / totalRamKb else 0f
    val storageRatio: Float get() = if (storageTotalKb > 0) storageUsedKb.toFloat() / storageTotalKb else 0f

    /** 100 = mejor salud. Se calcula como 100 - presion (RAM, CPU, temperatura). */
    fun healthScore(): Int {
        val pressure = ramRatio * 40f + cpuBusyPercent.coerceAtMost(100f) * 0.45f + maxThermalStatus * 3f
        return (100f - pressure.coerceAtMost(100f)).toInt().coerceIn(0, 100)
    }

    fun healthLevel(): HealthLevel =
        when {
            maxThermalStatus >= 4 || healthScore() < 45 -> HealthLevel.BAD
            healthScore() < 70 -> HealthLevel.WARN
            else -> HealthLevel.OK
        }
}

enum class HealthLevel { OK, WARN, BAD }

data class PlanItem(
    val recipe: Recipe,
    val supported: Boolean,
    val reason: String? = null,
)

enum class Profile { CONSERVATIVE, RECOMMENDED, AGGRESSIVE }

object Compatibility {
    fun check(
        recipe: Recipe,
        device: DeviceInfo,
    ): String? {
        if (recipe.steps.any { it.type == RecipeStepType.INSTALL_SYSTEM_APK }) return "specializedFlow"
        if (recipe.requiresRoot && !device.hasRoot) return "requiresRoot"
        recipe.appliesTo.minSdk?.let { if (device.sdk < it) return "minSdk" }
        recipe.appliesTo.maxSdk?.let { if (device.sdk > it) return "maxSdk" }
        when (recipe.appliesTo.isTv) {
            true -> if (!device.isTv) return "tvOnly"
            false -> if (device.isTv) return "phoneOnly"
            null -> Unit
        }
        val manufacturers = recipe.appliesTo.manufacturers
        if (!manufacturers.isNullOrEmpty() && manufacturers.none { it.equals(device.manufacturer, true) }) {
            return "manufacturer"
        }
        return null
    }

    fun maxRisk(profile: Profile): Risk =
        when (profile) {
            Profile.CONSERVATIVE -> Risk.SAFE
            Profile.RECOMMENDED -> Risk.MODERATE
            Profile.AGGRESSIVE -> Risk.ADVANCED
        }
}
