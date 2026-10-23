package com.optimizer.tools.core.ui.video

import android.view.MotionEvent
import android.view.Surface
import android.view.SurfaceHolder
import android.view.SurfaceView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.viewinterop.AndroidView
import com.optimizer.tools.R
import com.optimizer.tools.core.ui.AdbOptimizerTheme
import com.optimizer.tools.core.ui.resolve
import com.optimizer.tools.core.ui.uiText

/** Ajusta el video sin deformarlo y evita mapear toques del letterbox al destino. */
@Composable
internal fun RemoteVideo(
    state: RemoteVideoState,
    onSurface: (Surface?) -> Unit,
    onTouch: (Int, Float, Float) -> Unit,
    modifier: Modifier = Modifier,
    controlsEnabled: Boolean = true,
) {
    val currentOnSurface by rememberUpdatedState(onSurface)
    val currentOnTouch by rememberUpdatedState(onTouch)
    val canControl by rememberUpdatedState(controlsEnabled && state.live)
    val inspection = LocalInspectionMode.current
    val videoDescription = stringResource(R.string.video_device_description)
    BoxWithConstraints(modifier.background(Color.Black), contentAlignment = Alignment.Center) {
        val ratio = if (state.width > 0 && state.height > 0) state.width.toFloat() / state.height else 16f / 9f
        val videoWidth = minOf(maxWidth, maxHeight * ratio)
        val videoHeight = videoWidth / ratio
        if (inspection) {
            Text(stringResource(R.string.video_device_placeholder), color = Color.White)
        } else {
            AndroidView(
                modifier = Modifier.width(videoWidth).height(videoHeight),
                factory = { context ->
                    SurfaceView(context).apply {
                        contentDescription = videoDescription
                        setOnTouchListener { view, event ->
                            if (!canControl) return@setOnTouchListener true
                            if (event.pointerCount == 1 && view.width > 0 && view.height > 0) {
                                when (event.actionMasked) {
                                    MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE,
                                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL,
                                    -> {
                                        view.parent.requestDisallowInterceptTouchEvent(true)
                                        currentOnTouch(event.actionMasked, event.x / view.width, event.y / view.height)
                                        if (event.actionMasked == MotionEvent.ACTION_UP) view.performClick()
                                    }
                                }
                            } else if (event.actionMasked == MotionEvent.ACTION_POINTER_DOWN) {
                                currentOnTouch(MotionEvent.ACTION_CANCEL, 0f, 0f)
                            }
                            true
                        }
                        holder.addCallback(
                            object : SurfaceHolder.Callback {
                                override fun surfaceCreated(holder: SurfaceHolder) = currentOnSurface(holder.surface)

                                override fun surfaceChanged(
                                    holder: SurfaceHolder,
                                    format: Int,
                                    width: Int,
                                    height: Int,
                                ) = Unit

                                override fun surfaceDestroyed(holder: SurfaceHolder) = currentOnSurface(null)
                            },
                        )
                    }
                },
                onRelease = { currentOnSurface(null) },
            )
        }
        if (!state.live || state.error != null) {
            Box(Modifier.background(MaterialTheme.colorScheme.surface.copy(alpha = 0.9f))) {
                Text(state.error?.resolve() ?: state.status.resolve(), color = MaterialTheme.colorScheme.onSurface)
            }
        }
    }
}

@androidx.compose.ui.tooling.preview.Preview(name = "Video integrado", widthDp = 360, heightDp = 203)
@androidx.compose.ui.tooling.preview.Preview(name = "Video horizontal", widthDp = 800, heightDp = 360)
@Composable
private fun RemoteVideoPreview() {
    AdbOptimizerTheme {
        RemoteVideo(
            state = RemoteVideoState(status = uiText(R.string.video_status_live), width = 1920, height = 1080, live = true),
            onSurface = {},
            onTouch = { _, _, _ -> },
            modifier = Modifier.fillMaxSize(),
            controlsEnabled = false,
        )
    }
}
