package com.optimizer.tools.feature.apps

import dev.adboptimizer.core.model.AppAction
import dev.adboptimizer.core.model.ManagedApp

data class PendingAppAction(
    val app: ManagedApp,
    val action: AppAction,
)
