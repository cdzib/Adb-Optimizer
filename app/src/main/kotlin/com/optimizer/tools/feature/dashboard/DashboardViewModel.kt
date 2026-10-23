package com.optimizer.tools.feature.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.optimizer.tools.R
import com.optimizer.tools.core.ui.UiText
import com.optimizer.tools.core.ui.asUiText
import com.optimizer.tools.core.ui.uiText
import com.optimizer.tools.domain.model.DevicePowerAction
import dev.adboptimizer.core.model.AppTarget
import dev.adboptimizer.core.model.DeviceInfo
import dev.adboptimizer.core.model.DeviceMetrics
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Estado del inicio para el rol local o el dispositivo remoto seleccionado. */
data class DashboardState(
    val target: AppTarget = AppTarget.LOCAL,
    val device: DeviceInfo? = null,
    val devices: List<DeviceInfo> = emptyList(),
    val metrics: DeviceMetrics? = null,
    val loading: Boolean = false,
    val error: UiText? = null,
    val autoRefresh: Boolean = false,
    val refreshSeconds: Int = 30,
    val pendingPowerAction: PowerActionRequest? = null,
    val executingPowerAction: Boolean = false,
    val powerMessage: UiText? = null,
)

data class PowerActionRequest(
    val action: DevicePowerAction,
    val target: AppTarget,
    val deviceId: String,
    val deviceName: String,
)

class DashboardViewModel(
    private val environment: DashboardEnvironment,
) : ViewModel() {
    private val _state = MutableStateFlow(DashboardState(devices = environment.connectedDevices()))
    val state: StateFlow<DashboardState> = _state.asStateFlow()

    private var metricsJob: Job? = null
    private var tick = 0

    init {
        viewModelScope.launch {
            environment.sessions.collect { sessions ->
                val active = sessions.activeDevice
                val oldDeviceId = _state.value.device?.id
                _state.update { current ->
                    if (current.target == AppTarget.REMOTE) {
                        current.copy(
                            device = active,
                            devices = sessions.devices,
                            metrics = current.metrics.takeIf { active?.id == oldDeviceId },
                            pendingPowerAction = current.pendingPowerAction.takeIf { active?.id == oldDeviceId },
                        )
                    } else {
                        current.copy(devices = sessions.devices)
                    }
                }
                if (_state.value.target == AppTarget.REMOTE && active != null && active.id != oldDeviceId) refresh()
            }
        }
        viewModelScope.launch {
            environment.settings.collect { settings ->
                _state.update {
                    it.copy(
                        autoRefresh = settings.autoRefreshMetrics,
                        refreshSeconds = settings.metricsRefreshSeconds,
                    )
                }
            }
        }
        viewModelScope.launch {
            environment.ticks.collect {
                val current = _state.value
                if (current.autoRefresh && current.device != null && !current.executingPowerAction) {
                    tick += 1
                    if (tick >= current.refreshSeconds.coerceAtLeast(10)) {
                        tick = 0
                        refresh()
                    }
                } else {
                    tick = 0
                }
            }
        }
    }

    fun setTarget(target: AppTarget) {
        metricsJob?.cancel()
        _state.update { it.copy(pendingPowerAction = null, powerMessage = null) }
        if (target == AppTarget.REMOTE && environment.activeDevice() == null) {
            _state.update {
                it.copy(
                    target = target,
                    device = null,
                    metrics = null,
                    loading = false,
                    error = uiText(R.string.dashboard_error_connect),
                )
            }
            return
        }
        _state.update { it.copy(target = target, device = null, metrics = null, loading = true, error = null) }
        viewModelScope.launch {
            val device =
                if (target == AppTarget.LOCAL) {
                    runCatching { environment.localDevice() }.getOrNull()
                } else {
                    environment.activeDevice()
                }
            _state.update { current ->
                if (current.target != target) {
                    current
                } else {
                    current.copy(
                        device = device,
                        devices = environment.connectedDevices(),
                        loading = device != null,
                        error = if (device == null) uiText(R.string.dashboard_error_read) else null,
                    )
                }
            }
            if (device != null && _state.value.target == target) refresh()
        }
    }

    fun refresh() {
        if (_state.value.executingPowerAction) return
        val snapshot = _state.value
        metricsJob?.cancel()
        metricsJob =
            viewModelScope.launch {
                _state.update { it.copy(loading = true, error = null) }
                val result =
                    runCatching {
                        val device =
                            when (snapshot.target) {
                                AppTarget.LOCAL -> {
                                    snapshot.device ?: environment.localDevice()
                                }

                                AppTarget.REMOTE -> {
                                    environment.activeDevice()
                                        ?: error("Conecta un dispositivo ADB para consultar sus datos.")
                                }
                            }
                        val metrics =
                            when (snapshot.target) {
                                AppTarget.LOCAL -> {
                                    environment.localMetrics()
                                }

                                AppTarget.REMOTE -> {
                                    environment.remoteMetrics()
                                        ?: error("No se recibieron métricas del dispositivo.")
                                }
                            }
                        device to metrics
                    }.onFailure { if (it is CancellationException) throw it }
                if (_state.value.target != snapshot.target) return@launch
                val data = result.getOrNull()
                if (snapshot.target == AppTarget.REMOTE && data?.first?.id != environment.activeDevice()?.id) return@launch
                _state.update { current ->
                    current.copy(
                        device = data?.first ?: current.device,
                        devices = environment.connectedDevices(),
                        loading = false,
                        metrics = data?.second ?: current.metrics,
                        error = result.exceptionOrNull()?.message?.asUiText(),
                    )
                }
            }
    }

    fun requestPowerAction(action: DevicePowerAction) {
        val current = _state.value
        val device = current.device ?: return
        if (current.executingPowerAction) return
        _state.update {
            it.copy(pendingPowerAction = PowerActionRequest(action, current.target, device.id, device.displayName), powerMessage = null)
        }
    }

    fun dismissPowerAction() {
        _state.update { it.copy(pendingPowerAction = null) }
    }

    fun confirmPowerAction() {
        val current = _state.value
        val request = current.pendingPowerAction ?: return
        if (current.executingPowerAction || request.target != current.target || request.deviceId != current.device?.id) {
            dismissPowerAction()
            return
        }
        metricsJob?.cancel()
        _state.update { it.copy(pendingPowerAction = null, executingPowerAction = true, loading = false, powerMessage = null) }
        viewModelScope.launch {
            try {
                environment.executePowerAction(request.target, request.deviceId, request.action)
                _state.update { it.copy(powerMessage = uiText(R.string.power_request_sent, request.deviceName)) }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                _state.update {
                    it.copy(
                        powerMessage = uiText(R.string.power_request_unconfirmed, request.deviceName, error.message.orEmpty()),
                    )
                }
            } finally {
                _state.update { it.copy(executingPowerAction = false) }
            }
        }
    }

    companion object {
        fun factory(environment: DashboardEnvironment): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T = DashboardViewModel(environment) as T
            }
    }
}
