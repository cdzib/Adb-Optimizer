package com.optimizer.tools.app

import dev.adboptimizer.core.model.AppTarget

enum class Screen { HOME, CONNECT, ADD_DEVICE, TV_LAUNCHER, OPTIMIZE, APPS, HOST_APP_PICKER, SETTINGS, DPC }

/**
 * Módulos principales de la barra inferior. Optimizar se muestra siempre para mantener
 * cinco destinos estables, como en el diseño; la propia pantalla explica si falta permiso.
 */
fun mainNavigationScreens(
    target: AppTarget,
    shizukuReady: Boolean,
): List<Screen> =
    buildList {
        add(Screen.HOME)
        add(Screen.APPS)
        add(Screen.CONNECT)
        add(Screen.OPTIMIZE)
        add(Screen.SETTINGS)
    }
