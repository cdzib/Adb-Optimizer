package com.optimizer.tools.feature.remote

import com.optimizer.tools.domain.model.RemoteKey
import dev.adboptimizer.core.model.DeviceInfo

/** Puertos de la feature Control remoto. */
interface RemoteControlEnvironment {
    fun activeDevice(): DeviceInfo?

    suspend fun sendKey(key: RemoteKey)

    suspend fun probeMirror(onLog: (String) -> Unit): String
}
