package com.optimizer.tools.feature.apps

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.optimizer.tools.R
import com.optimizer.tools.core.ui.UiText
import com.optimizer.tools.core.ui.asUiText
import com.optimizer.tools.core.ui.uiText
import dev.adboptimizer.core.model.ActionSpec
import dev.adboptimizer.core.model.AppAction
import dev.adboptimizer.core.model.AppFilter
import dev.adboptimizer.core.model.AppSafety
import dev.adboptimizer.core.model.AppSort
import dev.adboptimizer.core.model.AppTarget
import dev.adboptimizer.core.model.DeviceInfo
import dev.adboptimizer.core.model.ManagedApp
import dev.adboptimizer.core.model.ParamKind
import dev.adboptimizer.core.model.Risk
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PendingAdvancedAction(
    val action: ActionSpec,
    val packageName: String,
)

/** Estado exclusivo de la pantalla Apps. */
data class AppsState(
    val target: AppTarget = AppTarget.LOCAL,
    val deviceInfo: DeviceInfo? = null,
    val apps: List<ManagedApp> = emptyList(),
    val loading: Boolean = false,
    val query: String = "",
    val busy: String? = null,
    val filter: Set<AppFilter> = setOf(AppFilter.USER),
    val sort: AppSort = AppSort.NAME,
    val detail: ManagedApp? = null,
    val detailsLoading: Boolean = false,
    val pendingAction: PendingAppAction? = null,
    val pendingAdvancedAction: PendingAdvancedAction? = null,
    val pendingBatch: AppAction? = null,
    val selection: Set<String> = emptySet(),
    val privileged: Boolean = true,
    val message: UiText? = null,
)

class AppsViewModel(
    private val environment: AppsEnvironment,
) : ViewModel() {
    private val _state = MutableStateFlow(AppsState())
    val state: StateFlow<AppsState> = _state.asStateFlow()

    private var managedAppsJob: Job? = null
    private var filterTouched = false

    init {
        viewModelScope.launch {
            environment.settings.collect { settings ->
                if (!filterTouched) {
                    _state.update {
                        it.copy(
                            filter =
                                if (settings.showSystemApps) {
                                    setOf(AppFilter.USER, AppFilter.SYSTEM)
                                } else {
                                    setOf(AppFilter.USER)
                                },
                        )
                    }
                }
            }
        }
    }

    /** La pantalla adopta el rol global elegido por el usuario; inicia en local. */
    fun onEnter(target: AppTarget = AppTarget.LOCAL) {
        setTarget(target)
    }

    fun setTarget(target: AppTarget) {
        val privileged = environment.privileged(target)
        if (target == AppTarget.REMOTE && environment.activeSessionId() == null) {
            _state.update { it.copy(message = uiText(R.string.apps_error_remote_needs_connection)) }
            return
        }
        _state.update {
            it.copy(
                target = target,
                privileged = privileged,
                deviceInfo = if (target == AppTarget.LOCAL) it.deviceInfo else null,
                query = "",
                selection = emptySet(),
                detail = null,
                pendingAction = null,
                pendingAdvancedAction = null,
                pendingBatch = null,
                message = null,
            )
        }
        if (target == AppTarget.LOCAL) loadLocalDeviceInfo()
        loadApps()
    }

    private fun loadLocalDeviceInfo() {
        viewModelScope.launch {
            val device = runCatching { environment.localDevice() }.getOrNull()
            _state.update { current ->
                if (current.target == AppTarget.LOCAL) current.copy(deviceInfo = device) else current
            }
        }
    }

    fun loadApps() {
        managedAppsJob?.cancel()
        val target = _state.value.target
        val connectedId = environment.activeSessionId()
        if (target == AppTarget.REMOTE && !environment.hasRemoteApps()) {
            _state.update {
                it.copy(
                    loading = false,
                    apps = emptyList(),
                    message = uiText(R.string.apps_error_remote_not_connected),
                )
            }
            return
        }
        managedAppsJob =
            viewModelScope.launch {
                _state.update { it.copy(loading = true, message = null) }
                val result = runCatching { environment.listApps(target) }
                val list = result.getOrDefault(emptyList())
                if (environment.activeSessionId() != connectedId && target == AppTarget.REMOTE) return@launch
                _state.update {
                    it.copy(
                        loading = false,
                        privileged = environment.privileged(target),
                        apps = list,
                        message =
                            result.exceptionOrNull()?.message?.asUiText()
                                ?: if (list.isEmpty()) uiText(R.string.apps_error_empty) else null,
                    )
                }
            }
    }

    fun setQuery(query: String) = _state.update { it.copy(query = query) }

    fun setFilter(filter: AppFilter) {
        filterTouched = true
        _state.update {
            val next = if (filter in it.filter) it.filter - filter else it.filter + filter
            it.copy(filter = next)
        }
    }

    fun setSort(sort: AppSort) = _state.update { it.copy(sort = sort) }

    fun toggleSelection(packageName: String) {
        _state.update {
            val next = it.selection.toMutableSet()
            if (!next.add(packageName)) next.remove(packageName)
            it.copy(selection = next)
        }
    }

    fun clearSelection() = _state.update { it.copy(selection = emptySet()) }

    fun selectAll(visiblePackages: Collection<String>) {
        _state.update { it.copy(selection = visiblePackages.toSet()) }
    }

    fun loadDetails(app: ManagedApp) {
        val target = _state.value.target
        _state.update { it.copy(detail = app, detailsLoading = true, message = null) }
        viewModelScope.launch {
            val detail = runCatching { environment.appDetails(target, app.packageName) }.getOrNull()
            _state.update {
                it.copy(
                    detail = detail ?: it.detail,
                    detailsLoading = false,
                    message = if (detail == null) uiText(R.string.apps_error_detail, app.packageName) else it.message,
                )
            }
        }
    }

    fun dismissDetails() = _state.update { it.copy(detail = null, detailsLoading = false) }

    fun requestAction(
        app: ManagedApp,
        action: AppAction,
    ) {
        _state.update { it.copy(detail = null) }
        val destructive =
            action == AppAction.UNINSTALL ||
                action == AppAction.DISABLE ||
                action == AppAction.CLEAR_DATA
        if (destructive) {
            _state.update { it.copy(pendingAction = PendingAppAction(app, action)) }
        } else {
            executeAction(app, action)
        }
    }

    fun dismissAction() = _state.update { it.copy(pendingAction = null) }

    fun confirmAction() {
        val pending = _state.value.pendingAction ?: return
        _state.update { it.copy(pendingAction = null) }
        executeAction(pending.app, pending.action)
    }

    private fun executeAction(
        app: ManagedApp,
        action: AppAction,
    ) {
        val target = _state.value.target
        val blocked: UiText? =
            when {
                AppSafety.isCritical(app.packageName) &&
                    (action == AppAction.UNINSTALL || action == AppAction.DISABLE || action == AppAction.CLEAR_DATA) -> {
                    uiText(R.string.apps_error_critical)
                }

                action == AppAction.UNINSTALL && app.isSystem -> {
                    uiText(R.string.apps_error_system_uninstall)
                }

                else -> {
                    null
                }
            }
        if (blocked != null) {
            _state.update { it.copy(message = blocked) }
            return
        }

        viewModelScope.launch {
            _state.update { it.copy(busy = "${action.name}:${app.packageName}", message = null) }
            val result =
                runCatching {
                    environment.runAction(target, app.packageName, action)
                }.getOrNull()

            _state.update {
                it.copy(
                    busy = null,
                    message =
                        result?.message?.asUiText() ?: if (result == null) {
                            uiText(R.string.apps_error_action_failed)
                        } else {
                            null
                        },
                )
            }

            if (result?.success == true &&
                action in setOf(AppAction.UNINSTALL, AppAction.DISABLE, AppAction.ENABLE, AppAction.CLEAR_DATA)
            ) {
                loadApps()
                _state.value.detail?.let { loadDetails(it) }
            }
        }
    }

    fun requestBatch(action: AppAction) {
        if (_state.value.selection.isEmpty()) {
            _state.update { it.copy(message = uiText(R.string.apps_error_select_one)) }
            return
        }
        _state.update { it.copy(pendingBatch = action) }
    }

    fun dismissBatch() = _state.update { it.copy(pendingBatch = null) }

    fun confirmBatch() {
        val action = _state.value.pendingBatch ?: return
        val target = _state.value.target
        val apps = _state.value.apps.filter { it.packageName in _state.value.selection }
        _state.update { it.copy(pendingBatch = null, busy = "batch", message = null) }

        viewModelScope.launch {
            if (target == AppTarget.REMOTE && !environment.hasRemoteApps()) {
                _state.update {
                    it.copy(busy = null, message = uiText(R.string.apps_error_remote_not_connected))
                }
                return@launch
            }
            var success = 0
            var skipped = 0
            val failures = mutableListOf<String>()
            for (app in apps) {
                val blocked =
                    AppSafety.isCritical(app.packageName) ||
                        (action == AppAction.UNINSTALL && app.isSystem)
                if (blocked) {
                    skipped++
                    continue
                }
                val result =
                    runCatching {
                        environment.runAction(target, app.packageName, action)
                    }.getOrNull()
                if (result?.success == true) success++ else failures += app.packageName
            }
            val summary =
                when {
                    skipped > 0 && failures.isNotEmpty() -> {
                        uiText(R.string.apps_batch_summary_both, action.name, success, skipped, failures.size)
                    }

                    skipped > 0 -> {
                        uiText(R.string.apps_batch_summary_skipped, action.name, success, skipped)
                    }

                    failures.isNotEmpty() -> {
                        uiText(R.string.apps_batch_summary_failures, action.name, success, failures.size)
                    }

                    else -> {
                        uiText(R.string.apps_batch_summary, action.name, success)
                    }
                }
            _state.update { it.copy(busy = null, message = summary, selection = emptySet()) }
            loadApps()
        }
    }

    fun dismissMessage() = _state.update { it.copy(message = null) }

    fun runAdvancedAction(
        actionId: String,
        packageName: String,
    ) {
        val action = environment.actions.firstOrNull { it.id == actionId } ?: return
        if (action.paramKind != ParamKind.PACKAGE) {
            _state.update { it.copy(message = uiText(R.string.apps_error_advanced_not_individual)) }
            return
        }
        if (action.risk == Risk.ADVANCED) {
            _state.update { it.copy(pendingAdvancedAction = PendingAdvancedAction(action, packageName)) }
            return
        }
        executeAdvancedAction(action, packageName)
    }

    fun dismissAdvancedAction() = _state.update { it.copy(pendingAdvancedAction = null) }

    fun confirmAdvancedAction() {
        val pending = _state.value.pendingAdvancedAction ?: return
        _state.update { it.copy(pendingAdvancedAction = null) }
        executeAdvancedAction(pending.action, pending.packageName)
    }

    private fun executeAdvancedAction(
        action: ActionSpec,
        packageName: String,
    ) {
        val target = _state.value.target
        viewModelScope.launch {
            _state.update { it.copy(busy = "${action.id}:$packageName", message = null) }
            val result =
                runCatching {
                    environment.runAdvancedAction(target, action, packageName)
                }
            val step = result.getOrNull()
            _state.update {
                it.copy(
                    busy = null,
                    message =
                        if (step?.status?.name == "OK") {
                            uiText(R.string.apps_advanced_applied, action.id, packageName)
                        } else {
                            step?.error?.asUiText()
                                ?: result.exceptionOrNull()?.message?.asUiText()
                                ?: uiText(R.string.apps_error_not_completed)
                        },
                )
            }
            loadApps()
        }
    }

    companion object {
        fun factory(environment: AppsEnvironment): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T = AppsViewModel(environment) as T
            }
    }
}
