package com.optimizer.tools.feature.inspector

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.optimizer.tools.R
import com.optimizer.tools.core.ui.UiText
import com.optimizer.tools.core.ui.asUiText
import com.optimizer.tools.core.ui.uiText
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Pestañas del inspector. */
enum class InspectorTab { PERMISSIONS, APP_OPS, COMPONENTS, SIGNATURE }

data class InspectorState(
    val packageName: String = "",
    val tab: InspectorTab = InspectorTab.PERMISSIONS,
    val inspection: AppInspection = AppInspection(),
    val loading: Boolean = false,
    val error: UiText? = null,
    val connected: Boolean = false,
)

class InspectorViewModel(
    private val environment: InspectorEnvironment,
) : ViewModel() {
    private val _state = MutableStateFlow(InspectorState())
    val state: StateFlow<InspectorState> = _state.asStateFlow()

    fun onEnter(packageName: String) {
        _state.update { it.copy(packageName = packageName, connected = environment.hasDevice()) }
        load()
    }

    fun selectTab(tab: InspectorTab) {
        _state.update { it.copy(tab = tab) }
    }

    fun load() {
        val packageName = _state.value.packageName
        if (packageName.isBlank() || _state.value.loading) return
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            try {
                val inspection = environment.inspect(packageName)
                _state.update { it.copy(inspection = inspection, loading = false) }
            } catch (error: Exception) {
                _state.update {
                    it.copy(loading = false, error = error.message?.asUiText() ?: uiText(R.string.inspector_error))
                }
            }
        }
    }

    companion object {
        fun factory(environment: InspectorEnvironment): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T = InspectorViewModel(environment) as T
            }
    }
}
