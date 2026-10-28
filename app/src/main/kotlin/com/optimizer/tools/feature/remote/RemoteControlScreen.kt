@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.optimizer.tools.feature.remote

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.VolumeDown
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.optimizer.tools.R
import com.optimizer.tools.core.ui.resolve
import com.optimizer.tools.core.ui.video.FullscreenEffect
import com.optimizer.tools.core.ui.video.RemoteVideo
import com.optimizer.tools.core.ui.video.RemoteVideoState
import com.optimizer.tools.domain.model.RemoteKey
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val HOLD_DELAY_MS = 400L
private const val REPEAT_INTERVAL_MS = 120L

@Composable
internal fun RemoteControlScreen(
    state: RemoteControlState,
    onBack: () -> Unit,
    onPress: (RemoteKey) -> Unit,
    onDismissError: () -> Unit,
    mirrorState: RemoteVideoState,
    onSurface: (android.view.Surface?) -> Unit,
    onTouch: (Int, Float, Float) -> Unit,
    onControlsEnabled: (Boolean) -> Unit,
    showRemoteButtons: Boolean,
    isPhone: Boolean,
) {
    val isLandscape =
        androidx.compose.ui.platform.LocalConfiguration.current.orientation ==
            android.content.res.Configuration.ORIENTATION_LANDSCAPE
    FullscreenEffect(isLandscape || isPhone)
    androidx.compose.runtime.DisposableEffect(isLandscape) {
        onControlsEnabled(!isLandscape)
        onDispose { onControlsEnabled(false) }
    }
    if (isLandscape) {
        FullscreenRemote(
            state = mirrorState,
            onBack = onBack,
            onSurface = onSurface,
            onTouch = onTouch,
            controlsEnabled = false,
        )
        return
    }
    if (isPhone) {
        FullscreenRemote(
            state = mirrorState,
            onBack = onBack,
            onSurface = onSurface,
            onTouch = onTouch,
            controlsEnabled = mirrorState.touchAllowed,
        )
        return
    }
    Column(
        Modifier.fillMaxSize().padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(top = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack, modifier = Modifier.offset(x = (-12).dp)) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
            }
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.remote_title), style = MaterialTheme.typography.titleLarge)
                Text(
                    state.deviceName ?: stringResource(R.string.remote_no_device),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        state.error?.let { message ->
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    Modifier.padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(message.resolve(), Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                    TextButton(onClick = onDismissError) { Text(stringResource(R.string.action_close)) }
                }
            }
        }

        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier.fillMaxWidth().weight(1f),
        ) {
            Column(Modifier.fillMaxSize()) {
                RemoteVideo(
                    mirrorState,
                    onSurface,
                    onTouch,
                    Modifier.fillMaxWidth().weight(1f),
                    controlsEnabled = mirrorState.touchAllowed,
                )
                Text(
                    mirrorState.controlHint.resolve(),
                    modifier = Modifier.padding(12.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        if (showRemoteButtons) {
            Column(
                Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                RemoteButton(stringResource(R.string.remote_up), repeatable = true, onKey = { onPress(RemoteKey.UP) }) {
                    RemoteIcon(Icons.Filled.KeyboardArrowUp)
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RemoteButton(stringResource(R.string.remote_left), repeatable = true, onKey = { onPress(RemoteKey.LEFT) }) {
                        RemoteIcon(Icons.AutoMirrored.Filled.KeyboardArrowLeft)
                    }
                    RemoteButton(
                        label = stringResource(R.string.remote_ok),
                        onKey = { onPress(RemoteKey.OK) },
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                    ) {
                        Text(stringResource(R.string.remote_ok), style = MaterialTheme.typography.titleSmall)
                    }
                    RemoteButton(stringResource(R.string.remote_right), repeatable = true, onKey = { onPress(RemoteKey.RIGHT) }) {
                        RemoteIcon(Icons.AutoMirrored.Filled.KeyboardArrowRight)
                    }
                }
                RemoteButton(stringResource(R.string.remote_down), repeatable = true, onKey = { onPress(RemoteKey.DOWN) }) {
                    RemoteIcon(Icons.Filled.KeyboardArrowDown)
                }
            }

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                RemoteButton(stringResource(R.string.remote_back), onKey = { onPress(RemoteKey.BACK) }) {
                    RemoteIcon(Icons.AutoMirrored.Filled.ArrowBack)
                }
                RemoteButton(stringResource(R.string.remote_home), onKey = { onPress(RemoteKey.HOME) }) {
                    RemoteIcon(Icons.Filled.Home)
                }
                RemoteButton(stringResource(R.string.remote_menu), onKey = { onPress(RemoteKey.MENU) }) {
                    RemoteIcon(Icons.Filled.Menu)
                }
            }

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                RemoteButton(stringResource(R.string.remote_volume_down), repeatable = true, onKey = { onPress(RemoteKey.VOLUME_DOWN) }) {
                    RemoteIcon(Icons.AutoMirrored.Filled.VolumeDown)
                }
                RemoteButton(stringResource(R.string.remote_play_pause), onKey = { onPress(RemoteKey.PLAY_PAUSE) }) {
                    RemoteIcon(Icons.Filled.PlayArrow)
                }
                RemoteButton(stringResource(R.string.remote_volume_up), repeatable = true, onKey = { onPress(RemoteKey.VOLUME_UP) }) {
                    RemoteIcon(Icons.AutoMirrored.Filled.VolumeUp)
                }
            }
        }
    }
}

@Composable
private fun FullscreenRemote(
    state: RemoteVideoState,
    onBack: () -> Unit,
    onSurface: (android.view.Surface?) -> Unit,
    onTouch: (Int, Float, Float) -> Unit,
    controlsEnabled: Boolean,
) {
    Box(Modifier.fillMaxSize()) {
        RemoteVideo(
            state = state,
            onSurface = onSurface,
            onTouch = onTouch,
            modifier = Modifier.fillMaxSize(),
            controlsEnabled = controlsEnabled,
        )
        FilledIconButton(
            onClick = onBack,
            modifier =
                Modifier
                    .align(Alignment.TopStart)
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .padding(12.dp),
            shape = CircleShape,
            colors =
                IconButtonDefaults.filledIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.72f),
                    contentColor = MaterialTheme.colorScheme.onSurface,
                ),
        ) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = stringResource(R.string.action_back),
            )
        }
    }
}

@Composable
private fun RemoteIcon(icon: ImageVector) {
    Icon(icon, contentDescription = null)
}

/**
 * Botón circular de 64dp con respuesta visual y háptica al pulsar.
 * Si [repeatable] es true, mantenerlo pulsado repite la tecla tras una pausa inicial.
 */
@Composable
private fun RemoteButton(
    label: String,
    repeatable: Boolean = false,
    onKey: () -> Unit,
    containerColor: Color = MaterialTheme.colorScheme.secondaryContainer,
    contentColor: Color = MaterialTheme.colorScheme.onSecondaryContainer,
    content: @Composable () -> Unit,
) {
    val haptic = LocalHapticFeedback.current
    val currentOnKey by rememberUpdatedState(onKey)
    var pressed by remember { mutableStateOf(false) }

    Box(
        Modifier
            .size(64.dp)
            .scale(if (pressed) 0.92f else 1f)
            .clip(CircleShape)
            .background(containerColor)
            .pointerInput(repeatable) {
                detectTapGestures(
                    onPress = {
                        pressed = true
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        currentOnKey()
                        coroutineScope {
                            val repeatJob =
                                if (repeatable) {
                                    launch {
                                        delay(HOLD_DELAY_MS)
                                        while (true) {
                                            currentOnKey()
                                            delay(REPEAT_INTERVAL_MS)
                                        }
                                    }
                                } else {
                                    null
                                }
                            tryAwaitRelease()
                            repeatJob?.cancel()
                        }
                        pressed = false
                    },
                )
            }.semantics {
                contentDescription = label
                role = Role.Button
            },
        contentAlignment = Alignment.Center,
    ) {
        androidx.compose.runtime.CompositionLocalProvider(
            androidx.compose.material3.LocalContentColor provides contentColor,
        ) {
            content()
        }
    }
}
