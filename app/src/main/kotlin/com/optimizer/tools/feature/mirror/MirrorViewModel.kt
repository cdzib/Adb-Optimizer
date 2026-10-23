package com.optimizer.tools.feature.mirror

import android.view.Surface
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.optimizer.tools.R
import com.optimizer.tools.core.ui.asUiText
import com.optimizer.tools.core.ui.uiText
import com.optimizer.tools.core.ui.video.RemoteVideoState
import dev.adboptimizer.core.adb.scrcpy.ScrcpyControlProtocol
import dev.adboptimizer.core.adb.scrcpy.ScrcpyUnit
import dev.adboptimizer.core.adb.scrcpy.ScrcpyVideoStream
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

typealias MirrorState = RemoteVideoState

class MirrorViewModel(
    private val environment: MirrorEnvironment,
) : ViewModel() {
    private val _state = MutableStateFlow(MirrorState())
    val state: StateFlow<MirrorState> = _state.asStateFlow()

    private val surface = MutableStateFlow<Surface?>(null)
    private var job: Job? = null
    private val controls = Channel<Pair<ScrcpyVideoStream, ByteArray>>(64)

    @Volatile private var controlsEnabled = true

    fun setControlsEnabled(enabled: Boolean) {
        controlsEnabled = enabled
    }

    init {
        viewModelScope.launch(Dispatchers.IO) {
            for ((session, message) in controls) {
                if (!controlsEnabled || session !== stream) continue
                try {
                    session.sendControl(message)
                } catch (error: Exception) {
                    _state.update {
                        it.copy(
                            error = error.message?.asUiText() ?: uiText(R.string.video_error_send_control),
                        )
                    }
                }
            }
        }
    }

    fun pressKey(keycode: Int) {
        if (!controlsEnabled) return
        val session = stream ?: return
        controls.trySend(session to ScrcpyControlProtocol.pressKey(keycode))
    }

    fun sendTouch(
        action: Int,
        x: Float,
        y: Float,
    ) {
        if (!controlsEnabled) return
        val session = stream ?: return
        val current = _state.value
        if (!current.touchAllowed || !current.live || current.width <= 0 || current.height <= 0) return
        val result = controls.trySend(session to ScrcpyControlProtocol.touch(action, x, y, current.width, current.height))
        if (result.isFailure) {
            // Never leave a remote finger down when congestion drops an UP/CANCEL.
            stop()
            _state.update { it.copy(live = false, error = uiText(R.string.video_error_saturated)) }
        }
    }

    @Volatile
    private var stream: ScrcpyVideoStream? = null

    fun start() {
        if (job?.isActive == true) return
        if (surface.value == null) return
        val previous = job
        _state.value = MirrorState()
        job =
            viewModelScope.launch(Dispatchers.IO) {
                previous?.join()
                var opened: ScrcpyVideoStream? = null
                var decoder: MirrorDecoder? = null
                try {
                    if (!environment.hasDevice()) error("No hay dispositivo conectado.")
                    val isTv = environment.isTv()
                    val capabilities = environment.readInputCapabilities()
                    val hint =
                        when {
                            capabilities.inputPermissionGranted == false -> {
                                uiText(R.string.video_control_denied)
                            }

                            capabilities.inputPermissionGranted == null -> {
                                uiText(R.string.video_control_unknown)
                            }

                            isTv -> {
                                uiText(R.string.video_control_tv)
                            }

                            else -> {
                                uiText(R.string.video_control_touch)
                            }
                        }
                    _state.update { it.copy(touchAllowed = capabilities.inputPermissionGranted != false, controlHint = hint) }
                    val session = environment.openVideo { }
                    opened = session
                    stream = session
                    ensureActive()
                    var width = session.info.width
                    var height = session.info.height
                    _state.update { it.copy(status = uiText(R.string.video_status_preparing), width = width, height = height) }

                    val target = surface.filterNotNull().first()
                    decoder = environment.createDecoder(target, width, height)
                    _state.update { it.copy(status = uiText(R.string.video_status_live), live = true) }
                    while (isActive && surface.value === target) {
                        when (val unit = session.readUnit()) {
                            is ScrcpyUnit.Packet -> {
                                decoder?.feed(unit)
                            }

                            is ScrcpyUnit.Resize -> {
                                require(unit.width > 0 && unit.height > 0)
                                width = unit.width
                                height = unit.height
                                decoder?.close()
                                decoder = environment.createDecoder(target, width, height)
                                _state.update { it.copy(width = width, height = height) }
                            }
                        }
                    }
                    _state.update { it.copy(status = uiText(R.string.video_status_closed), live = false) }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    if (isActive) {
                        _state.update {
                            it.copy(
                                status = uiText(R.string.video_status_disconnected),
                                live = false,
                                error = error.message?.asUiText() ?: uiText(R.string.video_error_show),
                            )
                        }
                    }
                } finally {
                    runCatching { decoder?.close() }
                    opened?.close()
                    if (stream === opened) stream = null
                }
            }
    }

    fun onSurfaceChanged(value: Surface?) {
        surface.value = value
        if (value == null) stop() else start()
    }

    /** Cerrar el stream desbloquea la lectura pendiente y el servidor en el dispositivo se detiene. */
    fun stop() {
        job?.cancel()
        stream?.let { runCatching { it.close() } }
        _state.update { it.copy(live = false) }
    }

    override fun onCleared() {
        stop()
    }

    companion object {
        fun factory(environment: MirrorEnvironment): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T = MirrorViewModel(environment) as T
            }
    }
}
