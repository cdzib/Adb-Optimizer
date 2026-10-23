package com.optimizer.tools.data

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import com.optimizer.tools.domain.model.ShizukuLocalStatus
import rikka.shizuku.Shizuku

class ShizukuLocalAccess(
    private val context: Context,
) {
    fun status(): ShizukuLocalStatus {
        val installed =
            runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    context.packageManager.getPackageInfo(
                        MANAGER_PACKAGE,
                        PackageManager.PackageInfoFlags.of(0),
                    )
                } else {
                    @Suppress("DEPRECATION")
                    context.packageManager.getPackageInfo(MANAGER_PACKAGE, 0)
                }
            }.isSuccess
        if (!installed) return ShizukuLocalStatus.NOT_INSTALLED
        if (!Shizuku.pingBinder()) return ShizukuLocalStatus.STOPPED
        return if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
            ShizukuLocalStatus.READY
        } else {
            ShizukuLocalStatus.PERMISSION_REQUIRED
        }
    }

    fun requireCommandPermission() {
        if (!Shizuku.pingBinder()) {
            throw SecurityException("Shizuku no está iniciado. Inicia el servicio y vuelve a intentar.")
        }
        if (Shizuku.getVersion() < MIN_USER_SERVICE_API) {
            throw IllegalStateException("Actualiza Shizuku: se requiere UserService API 10 o superior.")
        }
        if (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
            throw SecurityException("Concede el permiso de Shizuku a ADB Optimizer.")
        }
    }

    fun requestPermission(requestCode: Int) {
        check(Shizuku.pingBinder()) { "Inicia Shizuku antes de conceder acceso." }
        Shizuku.requestPermission(requestCode)
    }

    companion object {
        const val MANAGER_PACKAGE = "moe.shizuku.privileged.api"
        const val REQUEST_CODE = 7401
        const val MIN_USER_SERVICE_API = 10
    }
}
