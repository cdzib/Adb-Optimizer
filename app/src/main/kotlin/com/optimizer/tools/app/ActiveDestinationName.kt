package com.optimizer.tools.app

import dev.adboptimizer.core.model.AppTarget
import dev.adboptimizer.core.model.DeviceInfo

internal fun resolveActiveDestinationName(
    target: AppTarget,
    dashboardTarget: AppTarget,
    dashboardDevice: DeviceInfo?,
    selectedRemote: DeviceInfo?,
    localFallbackName: String,
): String? =
    when (target) {
        AppTarget.LOCAL -> {
            dashboardDevice
                ?.takeIf { dashboardTarget == AppTarget.LOCAL }
                ?.displayName
                ?.takeIf { it.isNotBlank() } ?: localFallbackName
        }

        AppTarget.REMOTE -> {
            selectedRemote?.displayName?.takeIf { it.isNotBlank() }
        }
    }
