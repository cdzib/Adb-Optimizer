package com.optimizer.tools.feature.debloat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.optimizer.tools.R
import com.optimizer.tools.core.ui.UiText
import com.optimizer.tools.core.ui.asUiText
import com.optimizer.tools.core.ui.uiText
import com.optimizer.tools.domain.debloat.DebloatAction
import com.optimizer.tools.domain.debloat.DebloatEntry
import com.optimizer.tools.domain.debloat.DebloatRisk
import com.optimizer.tools.domain.debloat.DebloatRun
import com.optimizer.tools.domain.debloat.DebloatSuggestion
import com.optimizer.tools.domain.profile.ProfileAppState
import dev.adboptimizer.core.model.ManagedApp
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class DebloatState(
    val deviceAvailable: Boolean = true,
    val loading: Boolean = false,
    val catalogReady: Boolean = false,
    val catalogUpdatedAt: Long? = null,
    val catalogSize: Int = 0,
    val downloading: Boolean = false,
    val downloadProgress: Int = 0,
    val query: String = "",
    val risks: Set<DebloatRisk> = setOf(DebloatRisk.RECOMMENDED, DebloatRisk.ADVANCED),
    val suggestions: List<DebloatSuggestion> = emptyList(),
    val visible: List<DebloatSuggestion> = emptyList(),
    val selection: Set<String> = emptySet(),
    val pendingAction: DebloatAction? = null,
    val running: Boolean = false,
    val done: Int = 0,
    val total: Int = 0,
    val current: String? = null,
    val lastRun: DebloatRun? = null,
    val message: UiText? = null,
    val error: UiText? = null,
)

class DebloatViewModel(
    private val environment: DebloatEnvironment,
) : ViewModel() {
    private val _state = MutableStateFlow(DebloatState())
    val state: StateFlow<DebloatState> = _state.asStateFlow()

    private var catalog: List<DebloatEntry> = emptyList()
    private var apps: List<ManagedApp> = emptyList()
    private var states: Map<String, ProfileAppState> = emptyMap()

    fun onEnter() {
        val available = environment.hasDevice()
        catalog = environment.cachedCatalog().orEmpty()
        _state.update {
            it.copy(
                deviceAvailable = available,
                catalogReady = catalog.isNotEmpty(),
                catalogSize = catalog.size,
                catalogUpdatedAt = environment.catalogUpdatedAt(),
                error = if (available) null else uiText(R.string.debloat_error_no_device),
            )
        }
        if (available) refresh()
    }

    fun refresh() {
        if (!environment.hasDevice()) {
            _state.update { it.copy(deviceAvailable = false, error = uiText(R.string.debloat_error_no_device)) }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            try {
                apps = environment.apps()
                states = environment.states()
                _state.update {
                    it.copy(
                        loading = false,
                        suggestions = buildSuggestions(),
                        visible = filterSuggestions(buildSuggestions(), it.query, it.risks),
                    )
                }
            } catch (error: Exception) {
                _state.update {
                    it.copy(
                        loading = false,
                        error = error.message?.asUiText() ?: uiText(R.string.debloat_error),
                    )
                }
            }
        }
    }

    fun downloadCatalog() {
        if (_state.value.downloading) return
        viewModelScope.launch {
            _state.update { it.copy(downloading = true, downloadProgress = 0, error = null) }
            try {
                val entries = environment.downloadCatalog { percent -> _state.update { it.copy(downloadProgress = percent) } }
                catalog = entries
                _state.update {
                    it.copy(
                        downloading = false,
                        catalogReady = true,
                        catalogSize = entries.size,
                        catalogUpdatedAt = environment.catalogUpdatedAt(),
                        suggestions = buildSuggestions(),
                        visible = filterSuggestions(buildSuggestions(), it.query, it.risks),
                        message = uiText(R.string.debloat_message_downloaded, entries.size),
                    )
                }
            } catch (error: Exception) {
                _state.update {
                    it.copy(
                        downloading = false,
                        error = error.message?.asUiText() ?: uiText(R.string.debloat_error_catalog),
                    )
                }
            }
        }
    }

    fun setQuery(query: String) {
        _state.update { current ->
            current.copy(
                query = query,
                visible = filterSuggestions(current.suggestions, query, current.risks),
            )
        }
    }

    fun toggleRisk(risk: DebloatRisk) {
        _state.update { current ->
            val risks = if (risk in current.risks) current.risks - risk else current.risks + risk
            current.copy(
                risks = risks,
                visible = filterSuggestions(current.suggestions, current.query, risks),
            )
        }
    }

    fun toggleSelection(packageName: String) {
        _state.update { current ->
            current.copy(
                selection =
                    if (packageName in current.selection) {
                        current.selection - packageName
                    } else {
                        current.selection + packageName
                    },
            )
        }
    }

    fun selectAllVisible() {
        _state.update { it.copy(selection = it.visible.map(DebloatSuggestion::packageName).toSet()) }
    }

    fun clearSelection() {
        _state.update { it.copy(selection = emptySet()) }
    }

    fun requestAction(action: DebloatAction) {
        if (_state.value.selectedTargets(action).isEmpty()) {
            _state.update { it.copy(message = uiText(R.string.debloat_message_nothing_applicable)) }
            return
        }
        _state.update { it.copy(pendingAction = action) }
    }

    fun cancelAction() {
        _state.update { it.copy(pendingAction = null) }
    }

    fun confirmAction() {
        val action = _state.value.pendingAction ?: return
        val targets = _state.value.selectedTargets(action)
        if (targets.isEmpty()) {
            _state.update { it.copy(pendingAction = null, message = uiText(R.string.debloat_message_nothing_applicable)) }
            return
        }
        viewModelScope.launch {
            _state.update {
                it.copy(
                    pendingAction = null,
                    running = true,
                    done = 0,
                    total = targets.size,
                    current = null,
                    message = null,
                    error = null,
                )
            }
            try {
                val run =
                    environment.apply(action, targets) { done, total, packageName ->
                        _state.update { it.copy(done = done, total = total, current = packageName) }
                    }
                _state.update {
                    it.copy(
                        running = false,
                        current = null,
                        selection = emptySet(),
                        lastRun = run,
                        message =
                            if (run.failures.isEmpty()) {
                                uiText(action.messageResource(), run.count)
                            } else {
                                uiText(R.string.debloat_message_partial, run.count, run.failures.size)
                            },
                    )
                }
                refresh()
            } catch (error: Exception) {
                _state.update {
                    it.copy(
                        running = false,
                        current = null,
                        error = error.message?.asUiText() ?: uiText(R.string.debloat_error),
                    )
                }
            }
        }
    }

    fun undo() {
        val run = _state.value.lastRun ?: return
        if (_state.value.running || run.changes.isEmpty()) return
        viewModelScope.launch {
            _state.update { it.copy(running = true, message = null, error = null) }
            try {
                val restored = environment.undo(run)
                _state.update {
                    it.copy(
                        running = false,
                        lastRun = null,
                        message = uiText(R.string.debloat_message_undone, restored),
                    )
                }
                refresh()
            } catch (error: Exception) {
                _state.update {
                    it.copy(
                        running = false,
                        error = error.message?.asUiText() ?: uiText(R.string.debloat_error),
                    )
                }
            }
        }
    }

    fun dismissMessage() {
        _state.update { it.copy(message = null) }
    }

    fun dismissError() {
        _state.update { it.copy(error = null) }
    }

    private fun buildSuggestions(): List<DebloatSuggestion> {
        val byPackage = apps.associateBy { it.packageName }
        return catalog
            .mapNotNull { entry ->
                val state = states[entry.packageName] ?: return@mapNotNull null
                if (entry.risk == DebloatRisk.UNSAFE || entry.risk == DebloatRisk.UNKNOWN) return@mapNotNull null
                DebloatSuggestion(
                    entry = entry,
                    label = byPackage[entry.packageName]?.displayName ?: entry.packageName,
                    app = byPackage[entry.packageName],
                    state = state,
                )
            }.sortedWith(compareBy<DebloatSuggestion> { it.entry.risk.ordinal }.thenBy { it.label.lowercase() })
    }

    private fun filterSuggestions(
        suggestions: List<DebloatSuggestion>,
        query: String,
        risks: Set<DebloatRisk>,
    ): List<DebloatSuggestion> {
        val text = query.trim().lowercase()
        return suggestions.filter { suggestion ->
            (suggestion.entry.risk in risks) &&
                (
                    text.isEmpty() ||
                        suggestion.label.lowercase().contains(text) ||
                        suggestion.packageName.lowercase().contains(text)
                )
        }
    }

    private fun DebloatState.selectedTargets(action: DebloatAction): List<String> =
        suggestions
            .filter { it.packageName in selection }
            .filter { suggestion ->
                when (action) {
                    DebloatAction.FREEZE -> suggestion.state == ProfileAppState.ENABLED
                    DebloatAction.UNFREEZE -> suggestion.state == ProfileAppState.DISABLED
                    DebloatAction.UNINSTALL -> suggestion.state != ProfileAppState.UNINSTALLED
                }
            }.map(DebloatSuggestion::packageName)

    companion object {
        fun factory(environment: DebloatEnvironment): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T = DebloatViewModel(environment) as T
            }
    }
}

internal fun DebloatAction.messageResource(): Int =
    when (this) {
        DebloatAction.FREEZE -> R.string.debloat_message_frozen
        DebloatAction.UNFREEZE -> R.string.debloat_message_unfrozen
        DebloatAction.UNINSTALL -> R.string.debloat_message_uninstalled
    }
