package com.optimizer.tools.domain.debloat

import com.optimizer.tools.domain.profile.ProfileAppState
import com.optimizer.tools.domain.profile.ProfileChange
import com.optimizer.tools.domain.profile.ProfileStepKind
import dev.adboptimizer.core.model.ManagedApp

/**
 * Riesgo que la lista curada asigna a quitar un paquete.
 *
 * La app no inventa recomendaciones: muestra las de la lista UAD-NG que el usuario
 * descarga, y nunca ofrece las marcadas como [UNSAFE].
 */
enum class DebloatRisk {
    RECOMMENDED,
    ADVANCED,
    EXPERT,
    UNSAFE,
    UNKNOWN,
    ;

    companion object {
        fun from(raw: String?): DebloatRisk =
            when (raw?.trim()?.lowercase()) {
                "recommended" -> RECOMMENDED
                "advanced" -> ADVANCED
                "expert" -> EXPERT
                "unsafe" -> UNSAFE
                else -> UNKNOWN
            }
    }
}

/** Una entrada de la lista curada de paquetes. */
data class DebloatEntry(
    val packageName: String,
    val source: String,
    val risk: DebloatRisk,
    val description: String,
)

/** Un paquete de la lista que esta instalado en el destino, con su estado actual. */
data class DebloatSuggestion(
    val entry: DebloatEntry,
    val label: String,
    val app: ManagedApp?,
    val state: ProfileAppState,
) {
    val packageName: String get() = entry.packageName
}

enum class DebloatAction {
    FREEZE,
    UNFREEZE,
    UNINSTALL,
    ;

    val step: ProfileStepKind
        get() =
            when (this) {
                FREEZE -> ProfileStepKind.DISABLE
                UNFREEZE -> ProfileStepKind.ENABLE
                UNINSTALL -> ProfileStepKind.UNINSTALL
            }

    val targetState: ProfileAppState
        get() =
            when (this) {
                FREEZE -> ProfileAppState.DISABLED
                UNFREEZE -> ProfileAppState.ENABLED
                UNINSTALL -> ProfileAppState.UNINSTALLED
            }
}

/** Resultado de una tanda del debloater; conserva el estado anterior para deshacer. */
data class DebloatRun(
    val action: DebloatAction,
    val changes: List<ProfileChange> = emptyList(),
    val failures: List<String> = emptyList(),
) {
    val count: Int get() = changes.size
}
