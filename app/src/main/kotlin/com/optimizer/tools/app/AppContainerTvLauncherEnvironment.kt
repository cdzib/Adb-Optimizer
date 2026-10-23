package com.optimizer.tools.app

import com.optimizer.tools.data.AppContainer
import com.optimizer.tools.feature.tvlauncher.TvLauncherEnvironment
import dev.adboptimizer.core.model.DeviceInfo
import dev.adboptimizer.core.model.RemoteLauncher

/** Adaptador de composición para Launchers de TV. */
internal class AppContainerTvLauncherEnvironment(
    private val container: AppContainer,
) : TvLauncherEnvironment {
    override fun activeDevice(): DeviceInfo? = container.device

    override suspend fun homeLaunchers(): List<RemoteLauncher> = container.remoteHomeLaunchers()

    override suspend fun currentHomeLauncher(): RemoteLauncher? = container.currentRemoteHomeLauncher()

    override suspend fun setHomeLauncher(packageName: String): RemoteLauncher = container.setRemoteHomeLauncher(packageName)
}
