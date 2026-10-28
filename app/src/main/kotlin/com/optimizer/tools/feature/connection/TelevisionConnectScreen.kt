package com.optimizer.tools.feature.connection

import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Cable
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.optimizer.tools.R
import com.optimizer.tools.app.tv.LocalTelevisionUi
import com.optimizer.tools.app.tv.TvActionRow
import com.optimizer.tools.app.tv.TvFocusCard
import com.optimizer.tools.app.tv.TvScreenHeader
import com.optimizer.tools.app.tv.TvSectionLabel
import com.optimizer.tools.app.tv.TvTile
import com.optimizer.tools.core.ui.AdbOptimizerTheme
import com.optimizer.tools.domain.model.KnownDevice
import com.optimizer.tools.domain.model.ThemeMode
import dev.adboptimizer.core.adb.DiscoveredService

/**
 * Asistente "Nuevo equipo" para televisor. En TV se evita el FAB y la cabecera tactil:
 * se vuelve con el mando (Atras) y cada modo es una tarjeta enfocable.
 */
@Composable
internal fun TelevisionConnectScreen(
    state: ConnectionState,
    onBack: () -> Unit,
    onMode: (ConnectMode) -> Unit,
    onDiscover: () -> Unit,
    onConnectDiscovered: (DiscoveredService) -> Unit,
    onConnectManual: (String) -> Unit,
    onConnectSecure: (String) -> Unit,
    onPairWithCode: (String, Int, String, Int?) -> Unit,
    onStartQr: () -> Unit,
    onCancelQr: () -> Unit,
    onConnectKnown: (KnownDevice) -> Unit,
    onCancelConnect: () -> Unit,
    onRetryConnect: () -> Unit,
    onEnableWifi: () -> Boolean,
) {
    var step by rememberSaveable { mutableStateOf(ConnectionStep.SELECT) }
    var ip by rememberSaveable { mutableStateOf("") }
    var pairPort by rememberSaveable { mutableStateOf("") }
    var code by rememberSaveable { mutableStateOf("") }
    var connectPort by rememberSaveable { mutableStateOf("") }
    var manualIp by rememberSaveable { mutableStateOf("") }
    var manualPort by rememberSaveable { mutableStateOf(DEFAULT_ADB_PORT) }

    val chooseMode: (ConnectMode) -> Unit = { mode ->
        onMode(mode)
        step = ConnectionStep.CONNECT
    }
    LaunchedEffect(step, state.connectMode) {
        if (step == ConnectionStep.CONNECT && state.connectMode == ConnectMode.QR && state.qrText == null) {
            onStartQr()
        }
    }
    DisposableEffect(Unit) {
        onDispose { onCancelQr() }
    }
    val goBack: () -> Unit = {
        onCancelQr()
        if (state.connecting || state.connectTimedOut) onCancelConnect()
        val previous = step.previous()
        if (previous == null) onBack() else step = previous
    }
    BackHandler(onBack = goBack)

    when (step) {
        ConnectionStep.SELECT -> {
            ModeSelection(chooseMode, onBack)
        }

        ConnectionStep.CONNECT -> {
            ConnectStepContent(
                state = state,
                goBack = goBack,
                ip = ip,
                pairPort = pairPort,
                code = code,
                connectPort = connectPort,
                manualIp = manualIp,
                manualPort = manualPort,
                onIp = { ip = it },
                onPairPort = { pairPort = it },
                onCode = { code = it },
                onConnectPort = { connectPort = it },
                onManualIp = { manualIp = it },
                onManualPort = { manualPort = it },
                onDiscover = onDiscover,
                onConnectDiscovered = onConnectDiscovered,
                onConnectKnown = onConnectKnown,
                onConnectManual = {
                    onConnectManual("${manualIp.trim()}:${manualPort.toIntOrNull() ?: DEFAULT_ADB_PORT_NUMBER}")
                },
                onConnectSecure = { onConnectSecure("${manualIp.trim()}:${manualPort.trim()}") },
                onPair = {
                    onPairWithCode(ip.trim(), pairPort.toIntOrNull() ?: 0, code.trim(), connectPort.toIntOrNull())
                },
                onStartQr = onStartQr,
                onCancelQr = onCancelQr,
                onUseCode = {
                    onCancelQr()
                    chooseMode(ConnectMode.CODE)
                },
                onCancelConnect = onCancelConnect,
                onRetryConnect = onRetryConnect,
                onEnableWifi = onEnableWifi,
            )
        }
    }
}

@Composable
private fun ModeSelection(
    onMode: (ConnectMode) -> Unit,
    onBack: () -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        TvScreenHeader(
            title = stringResource(R.string.tv_connect_title),
            subtitle = stringResource(R.string.connection_step, 1),
        )
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(bottom = 12.dp),
            modifier = Modifier.weight(1f),
        ) {
            item(key = "modes-label", span = { GridItemSpan(2) }) {
                TvSectionLabel(stringResource(R.string.connection_select_title))
            }
            item(key = "mode-tv") {
                TvTile(
                    icon = Icons.Filled.Devices,
                    title = stringResource(R.string.connection_tv),
                    subtitle = stringResource(R.string.connection_tv_detail),
                    onClick = { onMode(ConnectMode.CODE) },
                )
            }
            item(key = "mode-phone") {
                TvTile(
                    icon = Icons.Filled.Smartphone,
                    title = stringResource(R.string.connection_phone),
                    subtitle = stringResource(R.string.connection_phone_detail),
                    onClick = { onMode(ConnectMode.QR) },
                )
            }
            item(key = "mode-network") {
                TvTile(
                    icon = Icons.Filled.Search,
                    title = stringResource(R.string.connection_search),
                    subtitle = stringResource(R.string.tv_connect_network_detail),
                    onClick = { onMode(ConnectMode.NETWORK) },
                )
            }
            item(key = "mode-manual") {
                TvTile(
                    icon = Icons.Filled.Cable,
                    title = stringResource(R.string.connection_manual),
                    subtitle = stringResource(R.string.tv_connect_manual_detail),
                    onClick = { onMode(ConnectMode.MANUAL) },
                )
            }
            item(key = "modes-body", span = { GridItemSpan(2) }) {
                Text(
                    stringResource(R.string.connection_select_body),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            item(key = "mode-back", span = { GridItemSpan(2) }) {
                TvTile(
                    icon = Icons.AutoMirrored.Filled.ArrowBack,
                    title = stringResource(R.string.connection_back),
                    subtitle = stringResource(R.string.nav_destination),
                    onClick = onBack,
                )
            }
        }
    }
}

@Composable
private fun ConnectStepContent(
    state: ConnectionState,
    goBack: () -> Unit,
    ip: String,
    pairPort: String,
    code: String,
    connectPort: String,
    manualIp: String,
    manualPort: String,
    onIp: (String) -> Unit,
    onPairPort: (String) -> Unit,
    onCode: (String) -> Unit,
    onConnectPort: (String) -> Unit,
    onManualIp: (String) -> Unit,
    onManualPort: (String) -> Unit,
    onDiscover: () -> Unit,
    onConnectDiscovered: (DiscoveredService) -> Unit,
    onConnectKnown: (KnownDevice) -> Unit,
    onConnectManual: () -> Unit,
    onConnectSecure: () -> Unit,
    onPair: () -> Unit,
    onStartQr: () -> Unit,
    onCancelQr: () -> Unit,
    onUseCode: () -> Unit,
    onCancelConnect: () -> Unit,
    onRetryConnect: () -> Unit,
    onEnableWifi: () -> Boolean,
) {
    Column(Modifier.fillMaxSize()) {
        TvScreenHeader(
            title =
                stringResource(
                    if (state.connectMode == ConnectMode.QR) {
                        R.string.connection_qr_header
                    } else {
                        R.string.connection_new
                    },
                ),
            subtitle = stringResource(R.string.connection_step, 2),
        )
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (state.wifiEnabled == false) {
                WifiOffCard(onEnableWifi)
            }
            ConnectStatusCard(state, onCancelConnect, onRetryConnect)
            TvConnectModeContent(
                state = state,
                ip = ip,
                pairPort = pairPort,
                code = code,
                connectPort = connectPort,
                manualIp = manualIp,
                manualPort = manualPort,
                onIp = onIp,
                onPairPort = onPairPort,
                onCode = onCode,
                onConnectPort = onConnectPort,
                onManualIp = onManualIp,
                onManualPort = onManualPort,
                onDiscover = onDiscover,
                onConnectDiscovered = onConnectDiscovered,
                onConnectKnown = onConnectKnown,
                onPair = onPair,
                onConnectManual = onConnectManual,
                onConnectSecure = onConnectSecure,
                onStartQr = onStartQr,
                onCancelQr = onCancelQr,
                onUseCode = onUseCode,
            )
            TvTile(
                icon = Icons.AutoMirrored.Filled.ArrowBack,
                title = stringResource(R.string.tv_connect_back),
                subtitle = stringResource(R.string.connection_select_title),
                onClick = goBack,
            )
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun WifiOffCard(onEnableWifi: () -> Boolean) {
    val context = LocalContext.current
    TvFocusCard(onClick = null) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                stringResource(R.string.connection_wifi_off),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.error,
            )
            Text(
                stringResource(R.string.connection_wifi_off_body),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            TvActionRow(
                primaryLabel = stringResource(R.string.connection_enable_wifi),
                primaryEnabled = true,
                onPrimary = { if (!onEnableWifi()) openWifiSettings(context) },
            )
        }
    }
}

@Composable
private fun ConnectStatusCard(
    state: ConnectionState,
    onCancelConnect: () -> Unit,
    onRetryConnect: () -> Unit,
) {
    when {
        state.connecting && state.connectMode != ConnectMode.QR -> {
            TvFocusCard(onClick = null) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        stringResource(R.string.connection_connecting),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        state.connectTarget.orEmpty(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    TvActionRow(
                        primaryLabel = stringResource(R.string.connection_cancel_attempt),
                        primaryEnabled = true,
                        onPrimary = onCancelConnect,
                    )
                }
            }
        }

        state.connectTimedOut && state.connectMode != ConnectMode.QR -> {
            TvFocusCard(onClick = null) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        stringResource(R.string.connection_timeout_title),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                    Text(
                        stringResource(R.string.connection_timeout_body, state.connectTarget.orEmpty()),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    TvActionRow(
                        primaryLabel = stringResource(R.string.action_retry),
                        primaryEnabled = true,
                        onPrimary = onRetryConnect,
                        secondaryLabel = stringResource(R.string.action_close),
                        onSecondary = onCancelConnect,
                    )
                }
            }
        }
    }
}

private fun openWifiSettings(context: Context) {
    val opened = runCatching { context.startActivity(Intent(Settings.ACTION_WIFI_SETTINGS)) }.isSuccess
    if (!opened) runCatching { context.startActivity(Intent(Settings.ACTION_SETTINGS)) }
}

private const val DEFAULT_ADB_PORT = "5555"
private const val DEFAULT_ADB_PORT_NUMBER = 5555

@Preview(widthDp = 960, heightDp = 540, showBackground = true)
@Composable
private fun TelevisionConnectScreenPreview() {
    AdbOptimizerTheme(ThemeMode.DARK) {
        androidx.compose.runtime.CompositionLocalProvider(LocalTelevisionUi provides true) {
            TelevisionConnectScreen(
                state = ConnectionState(),
                onBack = {},
                onMode = {},
                onDiscover = {},
                onConnectDiscovered = {},
                onConnectManual = {},
                onConnectSecure = {},
                onPairWithCode = { _, _, _, _ -> },
                onStartQr = {},
                onCancelQr = {},
                onConnectKnown = {},
                onCancelConnect = {},
                onRetryConnect = {},
                onEnableWifi = { true },
            )
        }
    }
}
