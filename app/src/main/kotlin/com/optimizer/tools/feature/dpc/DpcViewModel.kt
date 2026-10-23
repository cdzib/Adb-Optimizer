package com.optimizer.tools.feature.dpc

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.optimizer.tools.R
import com.optimizer.tools.core.ui.UiText
import com.optimizer.tools.core.ui.asUiText
import com.optimizer.tools.core.ui.uiText
import com.optimizer.tools.domain.dpc.DpcMode
import com.optimizer.tools.domain.dpc.DpcPreflight
import com.optimizer.tools.domain.dpc.DpcReceiver
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

data class DpcPreset(
    val label: String,
    val packageName: String,
    val receiver: String,
    val defaultUrl: String = "",
) {
    val component: String get() = "$packageName/$receiver"
}

val defaultDpcPresets =
    listOf(
        DpcPreset(
            label = "DPC Macro (movil)",
            packageName = "com.macropay.dpcmacro",
            receiver = "com.macropay.dpcmacro.DeviceAdminReceiver",
        ),
        DpcPreset(
            label = "DPC Macro (TV)",
            packageName = "com.macropay.dpcmacro.tv",
            receiver = "com.macropay.dpcmacro.tv.DeviceAdminReceiver",
        ),
        DpcPreset(
            label = "MacroLock Lite",
            packageName = "com.macropay.macrolock.lite",
            receiver = "com.macropay.macrolock.lite.admin.LiteDeviceAdminReceiver",
        ),
        DpcPreset(
            label = "Nouvo (Promobitech)",
            packageName = "com.promobitech.nuovo",
            receiver = "com.promobitech.mobilock.component.MobilockDeviceAdmin",
            defaultUrl = "https://adb-dev.macropay.mx/nuovopay.apk",
        ),
    )

data class DpcState(
    val mode: DpcMode = DpcMode.DEVICE,
    val presetIndex: Int = 0,
    val component: String = defaultDpcPresets.first().component,
    val preflight: DpcPreflight? = null,
    val receivers: List<DpcReceiver> = emptyList(),
    val busy: Boolean = false,
    val progress: UiText? = null,
    val message: UiText? = null,
    val url: String = "",
    val checksum: String? = null,
    val qrPayload: String? = null,
)

class DpcViewModel(
    private val environment: DpcEnvironment,
) : ViewModel() {
    private val _state = MutableStateFlow(DpcState())
    val state: StateFlow<DpcState> = _state.asStateFlow()

    /** Ultimo APK preparado (local o descargado) para calcular checksum y QR. */
    private var preparedApk: java.io.File? = null

    fun setMode(mode: DpcMode) {
        _state.update { it.copy(mode = mode) }
        refreshPreflight()
    }

    fun selectPreset(index: Int) {
        val preset = defaultDpcPresets.getOrNull(index) ?: return
        _state.update {
            it.copy(
                presetIndex = index,
                component = preset.component,
                url = preset.defaultUrl.ifBlank { it.url },
                message = null,
            )
        }
    }

    fun setComponent(component: String) {
        _state.update { it.copy(component = component.trim()) }
    }

    fun setUrl(url: String) {
        _state.update { it.copy(url = url) }
    }

    fun onEnter() {
        refreshPreflight()
    }

    fun refreshPreflight() {
        if (!environment.hasRemoteDevice()) return report(uiText(R.string.dpc_error_no_device))
        viewModelScope.launch {
            _state.update { it.copy(busy = true, message = null) }
            val result = runCatching { environment.preflight(_state.value.mode) }
            _state.update {
                it.copy(
                    busy = false,
                    preflight = result.getOrNull() ?: it.preflight,
                    message = result.exceptionOrNull()?.message?.asUiText(),
                )
            }
        }
    }

    fun detectReceivers() {
        if (!environment.hasRemoteDevice()) return report(uiText(R.string.dpc_error_no_device))
        val packageName = componentPackage()
        viewModelScope.launch {
            _state.update { it.copy(busy = true, message = null) }
            val result = runCatching { environment.receivers(packageName) }
            _state.update {
                it.copy(
                    busy = false,
                    receivers = result.getOrDefault(emptyList()),
                    message =
                        result.exceptionOrNull()?.message?.asUiText()
                            ?: if (result.getOrDefault(emptyList()).isEmpty()) {
                                uiText(R.string.dpc_error_no_receivers, packageName)
                            } else {
                                null
                            },
                )
            }
        }
    }

    fun useReceiver(receiver: DpcReceiver) {
        _state.update { it.copy(component = receiver.component, message = null) }
    }

    fun installFromUris(uris: List<Uri>) {
        if (uris.isEmpty()) return
        if (uris.size == 1) {
            viewModelScope.launch {
                _state.update { it.copy(busy = true, progress = uiText(R.string.dpc_progress_copying), message = null) }
                val file =
                    runCatching { environment.copyToCache(uris.first(), "dpc-local") }.getOrElse { error ->
                        _state.update {
                            it.copy(busy = false, progress = null, message = error.message?.asUiText())
                        }
                        return@launch
                    }
                preparedApk = file
                val result =
                    runCatching {
                        environment.installFiles(listOf(file)) { progress ->
                            _state.update { it.copy(progress = progress.asUiText()) }
                        }
                    }
                val outcomes = result.getOrDefault(emptyList())
                val ok = outcomes.isNotEmpty() && outcomes.all { it.success }
                _state.update {
                    it.copy(
                        busy = false,
                        progress = null,
                        message =
                            when {
                                result.isFailure -> {
                                    result.exceptionOrNull()?.message?.asUiText()
                                }

                                ok -> {
                                    uiText(R.string.dpc_installed)
                                }

                                else -> {
                                    outcomes
                                        .joinToString("\n") { "${it.packageName}: ${it.output}" }
                                        .asUiText()
                                }
                            },
                    )
                }
                if (ok) detectReceivers()
            }
            return
        }
        runInstall { onProgress -> environment.installApks(uris, onProgress) }
    }

    fun generateProvisioningQr() {
        val url = _state.value.url.trim()
        if (url.isEmpty()) return report(uiText(R.string.dpc_error_url_qr))
        val component = _state.value.component
        viewModelScope.launch {
            _state.update { it.copy(busy = true, progress = uiText(R.string.dpc_progress_checksum), message = null) }
            val file =
                preparedApk?.takeIf { it.isFile && it.length() > 0 }
                    ?: runCatching {
                        environment.download(url) { progress ->
                            _state.update { it.copy(progress = progress.asUiText()) }
                        }
                    }.getOrElse { error ->
                        _state.update {
                            it.copy(busy = false, progress = null, message = error.message?.asUiText())
                        }
                        return@launch
                    }
            preparedApk = file
            val checksum =
                runCatching { environment.checksum(file) }.getOrElse { error ->
                    _state.update {
                        it.copy(busy = false, progress = null, message = error.message?.asUiText())
                    }
                    return@launch
                }
            val payload = buildProvisioningPayload(component, url, checksum)
            _state.update {
                it.copy(busy = false, progress = null, checksum = checksum, qrPayload = payload)
            }
        }
    }

    private fun buildProvisioningPayload(
        component: String,
        url: String,
        checksum: String,
    ): String =
        buildJsonObject {
            put("android.app.extra.PROVISIONING_DEVICE_ADMIN_COMPONENT_NAME", component)
            put("android.app.extra.PROVISIONING_DEVICE_ADMIN_PACKAGE_DOWNLOAD_LOCATION", url)
            put("android.app.extra.PROVISIONING_DEVICE_ADMIN_PACKAGE_CHECKSUM", checksum)
            put("android.app.extra.PROVISIONING_LEAVE_ALL_SYSTEM_APPS_ENABLED", true)
        }.toString()

    fun installFromUrl() {
        val url = _state.value.url.trim()
        if (url.isEmpty()) return report(uiText(R.string.dpc_error_url_install))
        viewModelScope.launch {
            _state.update { it.copy(busy = true, progress = uiText(R.string.dpc_progress_downloading), message = null) }
            val file =
                runCatching {
                    environment.download(url) { progress ->
                        _state.update { it.copy(progress = progress.asUiText()) }
                    }
                }.getOrElse { error ->
                    _state.update {
                        it.copy(busy = false, progress = null, message = error.message?.asUiText())
                    }
                    return@launch
                }
            preparedApk = file
            val result =
                runCatching {
                    environment.installFiles(listOf(file)) { progress ->
                        _state.update { it.copy(progress = progress.asUiText()) }
                    }
                }
            val outcomes = result.getOrDefault(emptyList())
            val ok = outcomes.isNotEmpty() && outcomes.all { it.success }
            _state.update {
                it.copy(
                    busy = false,
                    progress = null,
                    message =
                        when {
                            result.isFailure -> {
                                result.exceptionOrNull()?.message?.asUiText()
                            }

                            ok -> {
                                uiText(R.string.dpc_installed_url)
                            }

                            else -> {
                                outcomes
                                    .joinToString("\n") { "${it.packageName}: ${it.output}" }
                                    .asUiText()
                            }
                        },
                )
            }
            if (ok) detectReceivers()
        }
    }

    private fun runInstall(install: suspend ((String) -> Unit) -> List<dev.adboptimizer.core.adb.RemoteApkInstallOutcome>) {
        if (environment.activeSessionId() == null) return report(uiText(R.string.dpc_error_install_no_device))
        viewModelScope.launch {
            _state.update { it.copy(busy = true, progress = uiText(R.string.dpc_progress_preparing), message = null) }
            val result = runCatching { install { progress -> _state.update { it.copy(progress = progress.asUiText()) } } }
            val outcomes = result.getOrDefault(emptyList())
            val ok = outcomes.isNotEmpty() && outcomes.all { it.success }
            _state.update {
                it.copy(
                    busy = false,
                    progress = null,
                    message =
                        when {
                            result.isFailure -> {
                                result.exceptionOrNull()?.message?.asUiText()
                            }

                            ok -> {
                                uiText(R.string.dpc_installed)
                            }

                            else -> {
                                outcomes
                                    .joinToString("\n") { "${it.packageName}: ${it.output}" }
                                    .asUiText()
                            }
                        },
                )
            }
            if (ok) detectReceivers()
        }
    }

    fun setOwner() {
        val component = _state.value.component
        if (!component.contains('/')) return report(uiText(R.string.dpc_error_component))
        if (!environment.hasRemoteDevice()) return report(uiText(R.string.dpc_error_no_device))
        val mode = _state.value.mode
        viewModelScope.launch {
            _state.update { it.copy(busy = true, message = null) }
            val result = runCatching { environment.setOwner(component, mode) }
            val outcome = result.getOrNull()
            _state.update {
                it.copy(
                    busy = false,
                    message = result.exceptionOrNull()?.message?.asUiText() ?: outcome?.message?.asUiText(),
                )
            }
            refreshPreflight()
        }
    }

    fun removeOwner() {
        val component = _state.value.component
        if (!environment.hasRemoteDevice()) return report(uiText(R.string.dpc_error_no_device))
        viewModelScope.launch {
            _state.update { it.copy(busy = true, message = null) }
            val result = runCatching { environment.removeOwner(component) }
            _state.update {
                it.copy(
                    busy = false,
                    message =
                        result.exceptionOrNull()?.message?.asUiText()
                            ?: result.getOrNull()?.message?.asUiText(),
                )
            }
            refreshPreflight()
        }
    }

    fun dismissMessage() = _state.update { it.copy(message = null) }

    private fun componentPackage(): String =
        _state.value.component
            .substringBefore('/')
            .trim()

    private fun report(message: UiText) {
        _state.update { it.copy(message = message) }
    }

    companion object {
        fun factory(environment: DpcEnvironment): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T = DpcViewModel(environment) as T
            }
    }
}
