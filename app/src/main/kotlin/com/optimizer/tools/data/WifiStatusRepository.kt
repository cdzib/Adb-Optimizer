package com.optimizer.tools.data

import android.content.Context
import android.net.wifi.WifiManager

/**
 * Consulta el estado del Wi-Fi del telefono anfitrion.
 * Android 10+ no permite que una app normal lo encienda; la UI abre el panel del sistema.
 */
class WifiStatusRepository(
    context: Context,
) {
    private val appContext = context.applicationContext

    fun isWifiEnabled(): Boolean {
        val manager = manager() ?: return true
        return manager.isWifiEnabled
    }

    /**
     * Intento directo. En Android 10+ el sistema devuelve false para apps normales;
     * en ese caso la UI debe abrir el panel de Wi-Fi.
     */
    @Suppress("DEPRECATION")
    fun enable(): Boolean {
        val manager = manager() ?: return true
        if (manager.isWifiEnabled) return true
        return manager.setWifiEnabled(true)
    }

    private fun manager(): WifiManager? = appContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
}
