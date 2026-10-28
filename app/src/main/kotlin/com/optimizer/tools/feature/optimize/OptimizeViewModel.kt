package com.optimizer.tools.feature.optimize

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.optimizer.tools.R
import com.optimizer.tools.core.ui.UiText
import com.optimizer.tools.core.ui.asUiText
import com.optimizer.tools.core.ui.uiText
import com.optimizer.tools.domain.model.ShizukuLocalStatus
import dev.adboptimizer.core.model.AppTarget
import dev.adboptimizer.core.model.DeviceInfo
import dev.adboptimizer.core.model.PlanItem
import dev.adboptimizer.core.model.Profile
import dev.adboptimizer.core.model.RecipeRun
import dev.adboptimizer.core.model.Risk
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Estado exclusivo de la pantalla Optimizar, con destino remoto o teléfono local. */
data class OptimizeState(
    val target: AppTarget = AppTarget.LOCAL,
    val remoteAvailable: Boolean = false,
    val deviceInfo: DeviceInfo? = null,
    val shizukuStatus: ShizukuLocalStatus = ShizukuLocalStatus.NOT_INSTALLED,
    val profile: Profile = Profile.RECOMMENDED,
    val plan: List<PlanItem> = emptyList(),
    val selected: Set<String> = emptySet(),
    val running: Boolean = false,
    val results: List<RecipeRun> = emptyList(),
) {
    val supportedCount: Int get() = plan.count { it.supported }
    val selectedCount: Int get() = selected.size
}

sealed interface OptimizeEvent {
    data class Fail(
        val key: String,
        val detail: UiText?,
    ) : OptimizeEvent

    data class Done(
        val key: String,
    ) : OptimizeEvent
}

class OptimizeViewModel(
    private val environment: OptimizeEnvironment,
) : ViewModel() {
    private val _state = MutableStateFlow(OptimizeState())
    val state: StateFlow<OptimizeState> = _state.asStateFlow()

    private val _events = MutableSharedFlow<OptimizeEvent>(extraBufferCapacity = 8)
    val events: SharedFlow<OptimizeEvent> = _events.asSharedFlow()

    init {
        viewModelScope.launch {
            environment.remoteAvailable.collect { available ->
                _state.update { it.copy(remoteAvailable = available) }
                if (_state.value.target == AppTarget.REMOTE) rebuild()
            }
        }
        viewModelScope.launch { rebuild() }
    }

    fun setTarget(target: AppTarget) {
        if (target == AppTarget.REMOTE && !environment.hasRemoteSession()) {
            emitFail("error.notConnected", uiText(R.string.optimize_error_remote_no_device))
            return
        }
        _state.update { it.copy(target = target, results = emptyList()) }
        viewModelScope.launch { rebuild() }
    }

    fun setProfile(profile: Profile) {
        _state.update { it.copy(profile = profile) }
        viewModelScope.launch { rebuild() }
    }

    fun toggle(recipeId: String) {
        _state.update {
            val next = it.selected.toMutableSet()
            if (!next.add(recipeId)) next.remove(recipeId)
            it.copy(selected = next)
        }
    }

    fun optimize() = runSelected()

    fun refresh() {
        viewModelScope.launch { rebuild() }
    }

    private fun runSelected() {
        val snapshot = _state.value
        val device = snapshot.deviceInfo ?: return emitFail("error.notConnected", uiText(R.string.optimize_error_device_read))
        val engine =
            when (snapshot.target) {
                AppTarget.REMOTE -> {
                    environment.remoteEngine()
                }

                AppTarget.LOCAL -> {
                    if (!environment.localPrivilegeReady()) {
                        return emitFail("error.unknown", uiText(R.string.optimize_error_shizuku))
                    }
                    environment.localEngine()
                }
            } ?: return emitFail("error.notConnected", null)
        val selected = snapshot.plan.filter { it.supported && it.recipe.id in snapshot.selected }
        if (selected.isEmpty()) return emitFail("optimize.nothingSelected", null)

        viewModelScope.launch {
            _state.update { it.copy(running = true, results = emptyList()) }
            val result =
                runCatching {
                    selected.map { item -> engine.run(item.recipe, device) }
                }
            if (result.isFailure) {
                _state.update { it.copy(running = false) }
                emitFail("error.unknown", result.exceptionOrNull()?.message?.asUiText())
                return@launch
            }
            _state.update { it.copy(running = false, results = result.getOrDefault(emptyList())) }
            _events.tryEmit(OptimizeEvent.Done("optimize.done"))
        }
    }

    private suspend fun rebuild() {
        val target = _state.value.target
        val engine: OptimizeEngine
        val device: DeviceInfo?
        val shizukuStatus: ShizukuLocalStatus
        if (target == AppTarget.REMOTE) {
            engine = environment.remoteEngine() ?: run {
                _state.update {
                    it.copy(remoteAvailable = false, deviceInfo = null, plan = emptyList(), selected = emptySet())
                }
                return
            }
            device = environment.remoteDevice()
            shizukuStatus = _state.value.shizukuStatus
        } else {
            shizukuStatus = environment.localPrivilegeStatus()
            device = environment.localDevice()
            engine = environment.localEngine()
        }
        if (device == null) {
            _state.update { it.copy(deviceInfo = null, plan = emptyList(), selected = emptySet()) }
            return
        }
        val plan = engine.plan(device, _state.value.profile)
        val preSelected =
            plan
                .filter { it.recipe.risk == Risk.SAFE && it.supported }
                .map { it.recipe.id }
                .toSet()
        _state.update { current ->
            if (current.target != target) {
                current
            } else {
                current.copy(
                    deviceInfo = device,
                    remoteAvailable = environment.hasRemoteSession(),
                    shizukuStatus = shizukuStatus,
                    plan = plan,
                    selected = preSelected,
                )
            }
        }
    }

    private fun emitFail(
        key: String,
        detail: UiText?,
    ) {
        _events.tryEmit(OptimizeEvent.Fail(key, detail))
    }

    companion object {
        fun factory(environment: OptimizeEnvironment): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T = OptimizeViewModel(environment) as T
            }
    }
}
