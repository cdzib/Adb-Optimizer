package com.optimizer.tools.feature.connection

import com.optimizer.tools.domain.model.AppSettings
import com.optimizer.tools.domain.model.KnownDevice
import dev.adboptimizer.core.adb.DiscoveredService
import dev.adboptimizer.core.model.DeviceInfo
import kotlinx.coroutines.flow.Flow

/** Estado de sesiones que muestra el flujo de conexión. */
data class ConnectionSessionState(
    val devices: List<DeviceInfo> = emptyList(),
    val activeDevice: DeviceInfo? = null,
)

/** Puertos del flujo de conexión ADB: red, descubrimiento, pairing y sesiones. */
interface ConnectionEnvironment {
    val sessions: Flow<ConnectionSessionState>
    val settings: Flow<AppSettings>

    fun knownDevices(): List<KnownDevice>

    fun wifiEnabled(): Boolean

    fun enableWifi(): Boolean

    fun lastConnectedAddress(): String?

    fun elapsedRealtime(): Long

    fun log(
        message: String,
        error: Throwable? = null,
    )

    fun observeDiscovery(serviceType: String): Flow<DiscoveredService>

    suspend fun connect(
        host: String,
        port: Int,
        secure: Boolean,
    ): DeviceInfo

    suspend fun pair(
        host: String,
        port: Int,
        code: String,
    ): Result<Unit>

    fun disconnectDevice(deviceId: String)

    fun selectDevice(deviceId: String)

    fun refreshSessionNotification()

    fun stopSessionService()

    fun clearActiveSession()

    fun disconnect()

    fun startSessionService(device: DeviceInfo)

    fun forgetDevice(address: String)
}
