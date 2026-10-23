package com.optimizer.tools.data.remote

import com.optimizer.tools.domain.model.RemoteKey
import dev.adboptimizer.core.shell.AdbShell
import java.io.IOException

class RemoteControlRepository(
    private val shell: AdbShell,
) {
    suspend fun press(key: RemoteKey) {
        val result = shell.exec("input keyevent ${key.keycode}", 10_000)
        if (result.exitCode != 0) {
            throw IOException(result.output.ifBlank { "El dispositivo no aceptó la tecla ${key.name}." })
        }
    }
}
