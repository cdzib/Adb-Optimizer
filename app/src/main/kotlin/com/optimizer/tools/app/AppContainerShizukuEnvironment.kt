package com.optimizer.tools.app

import com.optimizer.tools.data.AppContainer
import com.optimizer.tools.data.ShizukuLocalAccess
import com.optimizer.tools.domain.model.ShizukuLocalStatus
import com.optimizer.tools.feature.shizuku.ShizukuEnvironment

/** Adaptador de composición para Shizuku. */
internal class AppContainerShizukuEnvironment(
    private val container: AppContainer,
) : ShizukuEnvironment {
    override fun status(): ShizukuLocalStatus = container.shizukuLocalAccess.status()

    override fun requestPermission() {
        container.shizukuLocalAccess.requestPermission(ShizukuLocalAccess.REQUEST_CODE)
    }
}
