package com.optimizer.tools.feature.shizuku

import com.optimizer.tools.domain.model.ShizukuLocalStatus

/** Puertos de Shizuku: estado local y solicitud de permiso. */
interface ShizukuEnvironment {
    fun status(): ShizukuLocalStatus

    fun requestPermission()
}
