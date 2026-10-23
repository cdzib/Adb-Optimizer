package com.optimizer.tools.app

import android.os.SystemClock
import com.optimizer.tools.data.AppContainer
import com.optimizer.tools.domain.model.AppSettings
import com.optimizer.tools.domain.model.KnownDevice
import com.optimizer.tools.feature.connection.ConnectionEnvironment
import com.optimizer.tools.feature.connection.ConnectionSessionState
import dev.adboptimizer.core.adb.DiscoveredService
import dev.adboptimizer.core.model.DeviceInfo
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/** Adaptador de composición para el flujo de conexión ADB. */
internal class AppContainerConnectionEnvironment(
    private val container: AppContainer,
) : ConnectionEnvironment {
    override val sessions: Flow<ConnectionSessionState> =
        container.sessionStore.state
            .map { sessions ->
                ConnectionSessionState(
                    devices = sessions.devices,
                    activeDevice = sessions.devices.firstOrNull { it.id == sessions.activeId },
                )
            }.distinctUntilChanged()

    override val settings: Flow<AppSettings> = container.settingsRepository.settings

    override fun knownDevices(): List<KnownDevice> = container.knownDevices()

    override fun wifiEnabled(): Boolean = container.wifiStatus.isWifiEnabled()

    override fun enableWifi(): Boolean = container.wifiStatus.enable()

    override fun lastConnectedAddress(): String? = container.lastConnectedAddress()

    override fun elapsedRealtime(): Long = SystemClock.elapsedRealtime()

    override fun log(
        message: String,
        error: Throwable?,
    ) {
        container.logger.debug("AdbPairingFlow", message, error)
    }

    override fun observeDiscovery(serviceType: String): Flow<DiscoveredService> = container.discovery.observe(serviceType)

    override suspend fun connect(
        host: String,
        port: Int,
        secure: Boolean,
    ): DeviceInfo = container.connect(host, port, secure)

    override suspend fun pair(
        host: String,
        port: Int,
        code: String,
    ): Result<Unit> = container.pair(host, port, code)

    override fun disconnectDevice(deviceId: String) {
        container.disconnectDevice(deviceId)
    }

    override fun selectDevice(deviceId: String) {
        container.selectDevice(deviceId)
    }

    override fun refreshSessionNotification() {
        container.refreshSessionNotification()
    }

    override fun stopSessionService() {
        container.stopSessionService()
    }

    override fun clearActiveSession() {
        container.clearActiveSession()
    }

    override fun disconnect() {
        container.disconnect()
    }

    override fun startSessionService(device: DeviceInfo) {
        container.startSessionService(device)
    }

    override fun forgetDevice(address: String) {
        container.forgetDevice(address)
    }
}
