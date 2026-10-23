package com.optimizer.tools.feature.mirror

import android.view.KeyEvent
import android.view.Surface
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.optimizer.tools.R
import com.optimizer.tools.core.ui.resolve
import com.optimizer.tools.core.ui.video.RemoteVideo

@Composable
internal fun MirrorScreen(
    state: MirrorState,
    onBack: () -> Unit,
    onSurface: (Surface?) -> Unit,
    onTouch: (Int, Float, Float) -> Unit,
    onKey: (Int) -> Unit,
) {
    Column(
        Modifier.fillMaxSize().padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(top = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack, modifier = Modifier.offset(x = (-12).dp)) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
            }
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.mirror_title), style = MaterialTheme.typography.titleLarge)
                Text(
                    state.status.resolve(),
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
                Text(message.resolve(), Modifier.padding(16.dp), style = MaterialTheme.typography.bodySmall)
            }
        }

        RemoteVideo(
            state = state,
            onSurface = onSurface,
            onTouch = onTouch,
            modifier = Modifier.fillMaxWidth().weight(1f),
            controlsEnabled = state.touchAllowed,
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            TextButton(onClick = { onKey(KeyEvent.KEYCODE_BACK) }, enabled = state.live) { Text(stringResource(R.string.remote_back)) }
            TextButton(onClick = { onKey(KeyEvent.KEYCODE_HOME) }, enabled = state.live) { Text(stringResource(R.string.remote_home)) }
            TextButton(
                onClick = { onKey(KeyEvent.KEYCODE_APP_SWITCH) },
                enabled = state.live,
            ) { Text(stringResource(R.string.mirror_recent)) }
        }
    }
}
