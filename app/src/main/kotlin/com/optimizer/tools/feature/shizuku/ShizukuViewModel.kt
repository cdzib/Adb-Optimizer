package com.optimizer.tools.feature.shizuku

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.optimizer.tools.domain.model.ShizukuLocalStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Estado y acciones de Shizuku, compartidos por Instalar y Configuracion. */
class ShizukuViewModel(
    private val environment: ShizukuEnvironment,
) : ViewModel() {
    private val _status = MutableStateFlow(ShizukuLocalStatus.NOT_INSTALLED)
    val status: StateFlow<ShizukuLocalStatus> = _status.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        _status.value = environment.status()
    }

    fun requestPermission() {
        runCatching { environment.requestPermission() }
        refresh()
    }

    fun onPermissionResult(granted: Boolean) {
        refresh()
        if (!granted) _status.value = environment.status()
    }

    companion object {
        fun factory(environment: ShizukuEnvironment): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T = ShizukuViewModel(environment) as T
            }
    }
}
