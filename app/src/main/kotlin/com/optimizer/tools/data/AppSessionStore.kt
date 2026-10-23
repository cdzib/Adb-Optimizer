package com.optimizer.tools.data

import dev.adboptimizer.core.model.DeviceInfo
import dev.adboptimizer.core.shell.AdbShell
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.Closeable

data class DeviceSession(
    val address: String,
    val device: DeviceInfo,
    val shell: AdbShell,
)

data class SessionsState(
    val devices: List<DeviceInfo> = emptyList(),
    val activeId: String? = null,
)

/**
 * Estado compartido de sesiones ADB. Lo escribe ConnectionViewModel y lo observan
 * las demas vistas sin acoplarse entre si.
 */
class AppSessionStore {
    private val sessions = LinkedHashMap<String, DeviceSession>()
    private val _state = MutableStateFlow(SessionsState())
    val state: StateFlow<SessionsState> = _state.asStateFlow()

    fun add(session: DeviceSession) {
        sessions[session.address]?.let { previous ->
            (previous.shell as? Closeable)?.let { runCatching { it.close() } }
        }
        sessions[session.address] = session
        publish(activeId = session.address)
    }

    fun select(deviceId: String): Boolean {
        if (!sessions.containsKey(deviceId)) return false
        publish(activeId = deviceId)
        return true
    }

    fun remove(deviceId: String): Boolean {
        val session = sessions.remove(deviceId) ?: return false
        (session.shell as? Closeable)?.let { runCatching { it.close() } }
        publish(activeId = if (activeId() == deviceId) sessions.keys.lastOrNull() else activeId())
        return true
    }

    fun clear() {
        sessions.values.forEach { session ->
            (session.shell as? Closeable)?.let { runCatching { it.close() } }
        }
        sessions.clear()
        publish(activeId = null)
    }

    fun activeSession(): DeviceSession? = activeId()?.let { sessions[it] }

    fun session(deviceId: String): DeviceSession? = sessions[deviceId]

    fun isEmpty(): Boolean = sessions.isEmpty()

    fun size(): Int = sessions.size

    fun activeId(): String? = _state.value.activeId

    fun activeDevice(): DeviceInfo? = activeSession()?.device

    fun activeShell(): AdbShell? = activeSession()?.shell

    private fun publish(activeId: String?) {
        _state.value = SessionsState(sessions.values.map { it.device }, activeId)
    }
}
