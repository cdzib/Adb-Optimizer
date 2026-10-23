package com.optimizer.tools.app

import dev.adboptimizer.core.model.AppTarget

enum class Screen { HOME, CONNECT, ADD_DEVICE, TV_LAUNCHER, OPTIMIZE, APPS, HOST_APP_PICKER, SETTINGS, DPC }

/** Módulos principales que puede usar cada rol; Optimizar local requiere Shizuku autorizado. */
fun mainNavigationScreens(
    target: AppTarget,
    shizukuReady: Boolean,
): List<Screen> =
    buildList {
        add(Screen.HOME)
        add(Screen.APPS)
        add(Screen.CONNECT)
        if (target == AppTarget.REMOTE || shizukuReady) add(Screen.OPTIMIZE)
        add(Screen.SETTINGS)
    }
