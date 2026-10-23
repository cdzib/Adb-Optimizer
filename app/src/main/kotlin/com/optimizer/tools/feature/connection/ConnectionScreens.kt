@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.optimizer.tools.feature.connection

import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AddLink
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import com.optimizer.tools.R
import com.optimizer.tools.core.ui.AppColors
import com.optimizer.tools.core.ui.QrImage
import com.optimizer.tools.core.ui.Translate
import com.optimizer.tools.domain.model.KnownDevice
import dev.adboptimizer.core.adb.DiscoveredService
import dev.adboptimizer.core.model.AppTarget

@Composable
internal fun ConnectScreen(
    state: ConnectionState,
    t: Translate,
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
    onForgetKnown: (KnownDevice) -> Unit,
    onCancelConnect: () -> Unit,
    onRetryConnect: () -> Unit,
    onRefreshWifi: () -> Unit,
    onEnableWifi: () -> Boolean,
) {
    val context = LocalContext.current

    LaunchedEffect(Unit) { onRefreshWifi() }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { onRefreshWifi() }

    val openWifiSettings: () -> Unit = {
        val intent =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                Intent(Settings.Panel.ACTION_WIFI)
            } else {
                Intent(Settings.ACTION_WIFI_SETTINGS)
            }
        runCatching { context.startActivity(intent) }
            .onFailure { context.startActivity(Intent(Settings.ACTION_WIFI_SETTINGS)) }
        Unit
    }
    var ip by rememberSaveable { mutableStateOf("") }
    var pairPort by rememberSaveable { mutableStateOf("") }
    var code by rememberSaveable { mutableStateOf("") }
    var connectPort by rememberSaveable { mutableStateOf("") }
    var manualIp by rememberSaveable { mutableStateOf("") }
    var manualPort by rememberSaveable { mutableStateOf("5555") }
    var step by rememberSaveable { mutableStateOf(ConnectionStep.SELECT) }
    var showUsbHelp by remember { mutableStateOf(false) }
    val selectMode: (ConnectMode) -> Unit = {
        onMode(it)
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
    BackHandler(enabled = !showUsbHelp, onBack = goBack)
    val helpSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val scanLauncher =
        rememberLauncherForActivityResult(ScanContract()) { result ->
            val payload = result.contents
            if (payload != null) {
                val parsed = parsePairingPayload(payload)
                if (parsed != null) {
                    if (parsed.third != null) {
                        ip = parsed.first
                        pairPort = parsed.second?.toString().orEmpty()
                        code = parsed.third.orEmpty()
                        onMode(ConnectMode.CODE)
                    } else {
                        manualIp = parsed.first
                        manualPort = (parsed.second ?: 5555).toString()
                    }
                }
            }
        }
    val scanPrompt = stringResource(R.string.connection_scan_prompt)
    val startScan: () -> Unit = {
        scanLauncher.launch(
            ScanOptions().apply {
                setDesiredBarcodeFormats(ScanOptions.QR_CODE)
                setPrompt(scanPrompt)
                setBeepEnabled(false)
                setOrientationLocked(false)
            },
        )
    }

    Column(Modifier.fillMaxSize().imePadding()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = goBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.connection_back))
            }
            Text(
                stringResource(
                    if (step == ConnectionStep.CONNECT &&
                        state.connectMode == ConnectMode.QR
                    ) {
                        R.string.connection_qr_header
                    } else {
                        R.string.connection_new
                    },
                ),
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleLarge,
            )
            IconButton(onClick = { showUsbHelp = true }) {
                Icon(Icons.Filled.Info, contentDescription = stringResource(R.string.connection_help))
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        key(step) {
            Column(
                modifier =
                    Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp),
            ) {
                when (step) {
                    ConnectionStep.SELECT -> {
                        ConnectionSelectionContent(
                            onMode = selectMode,
                            onExisting = selectMode,
                        )
                    }

                    ConnectionStep.CONNECT -> {
                        Unit
                    }
                }

                if (state.wifiEnabled == false && step != ConnectionStep.SELECT) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(stringResource(R.string.connection_wifi_off), style = MaterialTheme.typography.titleSmall)
                            Text(
                                stringResource(R.string.connection_wifi_off_body),
                                style = MaterialTheme.typography.bodySmall,
                            )
                            Button(
                                onClick = { if (!onEnableWifi()) openWifiSettings() },
                                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                            ) { Text(stringResource(R.string.connection_enable_wifi)) }
                        }
                    }
                }

                if (step == ConnectionStep.CONNECT) {
                    if (state.connecting && state.connectMode != ConnectMode.QR) {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                                    Spacer(Modifier.width(10.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(stringResource(R.string.connection_connecting), style = MaterialTheme.typography.titleSmall)
                                        Text(
                                            state.connectTarget.orEmpty(),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                                OutlinedButton(
                                    onClick = onCancelConnect,
                                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                                ) { Text(stringResource(R.string.connection_cancel_attempt)) }
                            }
                        }
                    } else if (state.connectTimedOut && state.connectMode != ConnectMode.QR) {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Text(stringResource(R.string.connection_timeout_title), style = MaterialTheme.typography.titleSmall)
                                Text(
                                    stringResource(R.string.connection_timeout_body, state.connectTarget.orEmpty()),
                                    style = MaterialTheme.typography.bodySmall,
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Button(
                                        onClick = onRetryConnect,
                                        modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                                    ) { Text(stringResource(R.string.action_retry)) }
                                    OutlinedButton(
                                        onClick = onCancelConnect,
                                        modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                                    ) { Text(stringResource(R.string.action_close)) }
                                }
                            }
                        }
                    }

                    when (state.connectMode) {
                        ConnectMode.NETWORK -> {
                            NetworkPanel(
                                state = state,
                                onDiscover = onDiscover,
                                onConnect = onConnectDiscovered,
                                onConnectKnown = onConnectKnown,
                                onForgetKnown = onForgetKnown,
                            )
                        }

                        ConnectMode.CODE -> {
                            CodePanel(
                                state = state,
                                ip = ip,
                                pairPort = pairPort,
                                code = code,
                                connectPort = connectPort,
                                onIp = { ip = it },
                                onPairPort = { pairPort = it },
                                onCode = { code = it },
                                onConnectPort = { connectPort = it },
                                onPair = {
                                    onPairWithCode(
                                        ip.trim(),
                                        pairPort.toIntOrNull() ?: 0,
                                        code.trim(),
                                        connectPort.toIntOrNull(),
                                    )
                                },
                            )
                        }

                        ConnectMode.QR -> {
                            QrPanel(
                                state = state,
                                onStartQr = {
                                    if (state.connectTimedOut) onCancelConnect()
                                    onStartQr()
                                },
                                onCancelQr = {
                                    onCancelQr()
                                    if (state.connecting) onCancelConnect()
                                    step = ConnectionStep.SELECT
                                },
                                onUseCode = {
                                    onCancelQr()
                                    selectMode(ConnectMode.CODE)
                                },
                            )
                        }

                        ConnectMode.MANUAL -> {
                            ManualPanel(
                                state = state,
                                ip = manualIp,
                                port = manualPort,
                                onIp = { manualIp = it },
                                onPort = { manualPort = it },
                                onConnect = {
                                    onConnectManual(
                                        manualIp.trim() + ":" + (manualPort.toIntOrNull() ?: 5555),
                                    )
                                },
                                onConnectSecure = { onConnectSecure("${manualIp.trim()}:${manualPort.trim()}") },
                                onScan = startScan,
                            )
                        }
                    }
                }
            }
        }
    }

    if (showUsbHelp) {
        ModalBottomSheet(
            onDismissRequest = { showUsbHelp = false },
            sheetState = helpSheetState,
        ) {
            UsbDebuggingHelpContent()
        }
    }
}

@Composable
internal fun DevicesScreen(
    state: ConnectionState,
    activeTarget: AppTarget,
    onAddDevice: () -> Unit,
    onSelectTarget: (AppTarget) -> Unit,
    onSelectDevice: (String) -> Unit,
    onDisconnectDevice: (String) -> Unit,
    onConnectKnown: (KnownDevice) -> Unit,
    onForgetKnown: (KnownDevice) -> Unit,
    onCancelConnect: (() -> Unit)? = null,
) {
    Box(Modifier.fillMaxSize()) {
        MyDevicesContent(
            state = state,
            activeTarget = activeTarget,
            onSelectTarget = onSelectTarget,
            onSelectDevice = onSelectDevice,
            onDisconnectDevice = onDisconnectDevice,
            onConnectKnown = onConnectKnown,
            onForgetKnown = onForgetKnown,
            onCancelConnect = onCancelConnect,
            modifier = Modifier.fillMaxSize(),
        )
        FloatingActionButton(
            onClick = onAddDevice,
            modifier = Modifier.align(Alignment.BottomEnd).padding(24.dp),
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
        ) {
            Icon(Icons.Filled.AddLink, contentDescription = stringResource(R.string.devices_add))
        }
    }
}

@Composable
private fun MyDevicesContent(
    state: ConnectionState,
    activeTarget: AppTarget,
    onSelectTarget: (AppTarget) -> Unit,
    onSelectDevice: (String) -> Unit,
    onDisconnectDevice: (String) -> Unit,
    onConnectKnown: (KnownDevice) -> Unit,
    onForgetKnown: (KnownDevice) -> Unit,
    onCancelConnect: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val device = state.connected
    Column(
        modifier =
            modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(start = 24.dp, top = 24.dp, end = 24.dp, bottom = 104.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                stringResource(R.string.devices_eyebrow),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(stringResource(R.string.devices_title), style = MaterialTheme.typography.headlineSmall)
            Text(
                stringResource(R.string.devices_description),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        DeviceSelectionRow(
            title = stringResource(R.string.devices_local),
            subtitle =
                stringResource(
                    if (activeTarget ==
                        AppTarget.LOCAL
                    ) {
                        R.string.devices_local_active
                    } else {
                        R.string.devices_local_available
                    },
                ),
            selected = activeTarget == AppTarget.LOCAL,
            onSelect = { onSelectTarget(AppTarget.LOCAL) },
            icon = Icons.Filled.Smartphone,
        )
        if (state.connecting) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    stringResource(R.string.devices_remote_heading),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                val targetAddress = state.connectTarget?.removeSuffix(" (TLS)")
                val knownTarget = state.known.firstOrNull { it.address == targetAddress }
                DevicesConnectionStatus(
                    target = knownTarget?.displayName ?: state.connectTarget,
                    reconnecting = knownTarget != null,
                    onCancel = onCancelConnect,
                )
            }
        }
        if (state.connectedDevices.isNotEmpty()) {
            ConnectedDevicesSection(
                devices = state.connectedDevices,
                activeTarget = activeTarget,
                activeRemoteId = device?.id,
                onSelect = { id ->
                    onSelectDevice(id)
                    onSelectTarget(AppTarget.REMOTE)
                },
                onDisconnect = onDisconnectDevice,
            )
        } else if (!state.connecting) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            ) {
                Column(
                    Modifier.fillMaxWidth().padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Icon(
                        Icons.Filled.Devices,
                        contentDescription = null,
                        modifier = Modifier.size(40.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Text(stringResource(R.string.devices_empty_title), style = MaterialTheme.typography.titleMedium)
                    Text(
                        stringResource(R.string.devices_empty_body),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        KnownDevicesSection(
            devices = state.known,
            connectedDevices = state.connectedDevices,
            connecting = state.connecting,
            onConnect = onConnectKnown,
            onForget = onForgetKnown,
            onSelect = {
                onSelectDevice(it)
                onSelectTarget(AppTarget.REMOTE)
            },
            onDisconnect = onDisconnectDevice,
            connectTarget = state.connectTarget,
        )
    }
}

@Composable
internal fun DeviceSelectionRow(
    title: String,
    subtitle: String,
    selected: Boolean,
    onSelect: () -> Unit,
    onStop: (() -> Unit)? = null,
    icon: ImageVector = Icons.Filled.Devices,
) {
    Card(
        colors =
            CardDefaults.cardColors(
                containerColor =
                    if (selected) {
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
                    } else {
                        MaterialTheme.colorScheme.surface
                    },
            ),
        modifier = Modifier.fillMaxWidth().clickable(onClick = onSelect),
        border = BorderStroke(1.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant),
        shape = MaterialTheme.shapes.large,
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (onStop != null) {
                TextButton(
                    onClick = onStop,
                    colors =
                        androidx.compose.material3.ButtonDefaults.textButtonColors(
                            contentColor = MaterialTheme.colorScheme.error,
                        ),
                ) { Text(stringResource(R.string.connection_stop)) }
            }
        }
    }
}

@Composable
internal fun NetworkPanel(
    state: ConnectionState,
    onDiscover: () -> Unit,
    onConnect: (DiscoveredService) -> Unit,
    onConnectKnown: (KnownDevice) -> Unit,
    onForgetKnown: (KnownDevice) -> Unit,
    knownDevices: List<KnownDevice> = state.known,
) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.connection_network_title), style = MaterialTheme.typography.titleMedium)
            Text(
                stringResource(R.string.connection_network_body),
                style = MaterialTheme.typography.bodySmall,
            )
            Button(onClick = onDiscover, enabled = !state.discoveryRunning) {
                if (state.discoveryRunning) {
                    CircularProgressIndicator(Modifier.size(15.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                }
                Text(
                    stringResource(
                        if (state.discoveryRunning) {
                            R.string.connection_searching
                        } else {
                            R.string.connection_search_devices
                        },
                    ),
                )
            }

            if (state.discovered.isEmpty()) {
                Text(
                    stringResource(
                        if (state.discoveryRunning) {
                            R.string.connection_listening
                        } else {
                            R.string.connection_no_results
                        },
                    ),
                    style = MaterialTheme.typography.bodySmall,
                )
            } else {
                state.discovered.forEach { service ->
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(service.host, style = MaterialTheme.typography.titleSmall)
                            Text(
                                stringResource(R.string.connection_tls_port, service.port),
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        Button(
                            onClick = { onConnect(service) },
                            enabled = !state.connecting,
                            contentPadding =
                                androidx.compose.foundation.layout
                                    .PaddingValues(horizontal = 14.dp),
                        ) { Text(stringResource(R.string.connection_connect)) }
                    }
                    HorizontalDivider(color = AppColors.border)
                }
            }

            Text(
                stringResource(R.string.connection_network_hint),
                style = MaterialTheme.typography.labelSmall,
            )

            if (knownDevices.isNotEmpty()) {
                HorizontalDivider(color = AppColors.border)
                Text(stringResource(R.string.connection_recent_short), style = MaterialTheme.typography.labelSmall)
                knownDevices.forEach { device ->
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                device.model.ifBlank { device.host },
                                style = MaterialTheme.typography.titleSmall,
                            )
                            Text(
                                device.address + " · " +
                                    if (device.secure) {
                                        "TLS"
                                    } else {
                                        stringResource(R.string.connection_transport_classic)
                                    },
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        TextButton(onClick = { onForgetKnown(device) }) { Text(stringResource(R.string.connection_forget)) }
                        Button(
                            onClick = { onConnectKnown(device) },
                            enabled = !state.connecting,
                            contentPadding =
                                androidx.compose.foundation.layout
                                    .PaddingValues(horizontal = 14.dp),
                        ) { Text(stringResource(R.string.connection_connect)) }
                    }
                }
            }
        }
    }
}

@Composable
internal fun CodePanel(
    state: ConnectionState,
    ip: String,
    pairPort: String,
    code: String,
    connectPort: String,
    onIp: (String) -> Unit,
    onPairPort: (String) -> Unit,
    onCode: (String) -> Unit,
    onConnectPort: (String) -> Unit,
    onPair: () -> Unit,
) {
    var showConnectPort by rememberSaveable { mutableStateOf(false) }
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.connection_code_title), style = MaterialTheme.typography.titleMedium)
            Text(
                stringResource(R.string.connection_code_inline),
                style = MaterialTheme.typography.bodySmall,
            )

            OutlinedTextField(
                value = ip,
                onValueChange = onIp,
                label = { Text(stringResource(R.string.connection_ip_label)) },
                placeholder = { Text(stringResource(R.string.connection_ip_placeholder)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                OutlinedTextField(
                    value = pairPort,
                    onValueChange = onPairPort,
                    label = { Text(stringResource(R.string.connection_pair_port)) },
                    placeholder = { Text(stringResource(R.string.connection_pair_port_placeholder)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                PairingCodeField(
                    value = code,
                    onValueChange = onCode,
                    enabled = !state.pairing && !state.connecting,
                )
            }

            TextButton(onClick = { showConnectPort = !showConnectPort }) {
                Text(stringResource(R.string.connection_optional_port))
            }
            if (showConnectPort) {
                OutlinedTextField(
                    value = connectPort,
                    onValueChange = onConnectPort,
                    label = { Text(stringResource(R.string.connection_connect_port)) },
                    placeholder = { Text(stringResource(R.string.connection_connect_port_placeholder)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    stringResource(R.string.connection_port_help),
                    style = MaterialTheme.typography.labelSmall,
                )
            }

            Button(
                onClick = onPair,
                enabled = !state.pairing && !state.connecting,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (state.pairing) {
                    CircularProgressIndicator(Modifier.size(15.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                }
                Text(
                    stringResource(
                        if (state.pairing) {
                            R.string.connection_pairing
                        } else {
                            R.string.connection_pair_action
                        },
                    ),
                )
            }
        }
    }
}

@Composable
internal fun QrPanel(
    state: ConnectionState,
    onStartQr: () -> Unit,
    onCancelQr: () -> Unit,
    onUseCode: () -> Unit,
) {
    val failed = state.connectTimedOut || state.qrStatus == QrStatus.ERROR || state.qrStatus == QrStatus.TIMEOUT
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(24.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.connection_qr_title), style = MaterialTheme.typography.headlineSmall)
            Text(
                stringResource(R.string.connection_qr_description),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Column(
            Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (!failed && state.qrText != null) {
                QrImage(state.qrText, Modifier.widthIn(max = 248.dp).fillMaxWidth().aspectRatio(1f))
            }
            if (failed || state.qrText == null) {
                Text(
                    stringResource(
                        if (state.qrStatus ==
                            QrStatus.TIMEOUT
                        ) {
                            R.string.connection_qr_expired
                        } else {
                            R.string.connection_qr_unavailable
                        },
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Button(
                    onClick = onStartQr,
                    enabled = state.wifiEnabled != false && !state.connecting,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                ) {
                    Text(stringResource(R.string.connection_qr_retry))
                }
            } else {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (state.qrStatus == QrStatus.PAIRING || state.qrStatus == QrStatus.CONNECTING) {
                        CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                    }
                    Text(qrStatusText(state.qrStatus), style = MaterialTheme.typography.bodyMedium)
                }
                Text(
                    stringResource(R.string.connection_qr_keep_open),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        TextButton(
            onClick = onUseCode,
            enabled = !state.connecting && (failed || state.qrStatus != QrStatus.CONNECTING),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.connection_code_alternative))
        }
        OutlinedButton(onClick = onCancelQr, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp), shape = MaterialTheme.shapes.large) {
            Text(stringResource(R.string.connection_qr_cancel))
        }
    }
}

@Composable
internal fun ManualPanel(
    state: ConnectionState,
    ip: String,
    port: String,
    onIp: (String) -> Unit,
    onPort: (String) -> Unit,
    onConnect: () -> Unit,
    onConnectSecure: () -> Unit,
    onScan: () -> Unit,
) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.connection_manual_title), style = MaterialTheme.typography.titleMedium)
            Text(
                stringResource(R.string.connection_manual_body),
                style = MaterialTheme.typography.bodySmall,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = ip,
                    onValueChange = onIp,
                    label = { Text(stringResource(R.string.connection_ip_short)) },
                    placeholder = { Text(stringResource(R.string.connection_ip_placeholder)) },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = port,
                    onValueChange = onPort,
                    label = { Text(stringResource(R.string.connection_port_label)) },
                    singleLine = true,
                    modifier = Modifier.weight(0.6f),
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(
                    onClick = onConnect,
                    enabled = !state.connecting,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(
                        stringResource(
                            if (state.connecting) {
                                R.string.connection_connecting
                            } else {
                                R.string.connection_classic
                            },
                        ),
                    )
                }
                Button(
                    onClick = onConnectSecure,
                    enabled = !state.connecting,
                    modifier = Modifier.weight(1f),
                ) {
                    if (state.connecting) {
                        CircularProgressIndicator(Modifier.size(15.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                    }
                    Text(
                        stringResource(
                            if (state.connecting) {
                                R.string.connection_connecting
                            } else {
                                R.string.connection_tls
                            },
                        ),
                    )
                }
            }
            OutlinedButton(onClick = onScan, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.connection_scan_qr))
            }
        }
    }
}

@Composable
internal fun UsbDebuggingHelpContent() {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(stringResource(R.string.connection_usb_help_title), style = MaterialTheme.typography.titleLarge)
        Text(
            stringResource(R.string.connection_usb_help_desc),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        HelpStep("1", stringResource(R.string.connection_usb_help_step1))
        HelpStep("2", stringResource(R.string.connection_usb_help_step2))
        HelpStep("3", stringResource(R.string.connection_usb_help_step3))
        HelpStep("4", stringResource(R.string.connection_usb_help_step4))
        Text(
            stringResource(R.string.connection_usb_help_note),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
internal fun qrStatusText(status: QrStatus?): String =
    when (status) {
        QrStatus.WAITING -> stringResource(R.string.connection_qr_waiting)
        QrStatus.PAIRING -> stringResource(R.string.connection_qr_pairing)
        QrStatus.CONNECTING -> stringResource(R.string.connection_qr_connecting)
        QrStatus.TIMEOUT -> stringResource(R.string.connection_qr_timeout)
        QrStatus.ERROR -> stringResource(R.string.connection_qr_error)
        null -> ""
    }

@Composable
internal fun HelpStep(
    number: String,
    text: String,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(22.dp)
                .clip(RoundedCornerShape(50))
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center,
        ) {
            Text(number, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
        }
        Text(text, style = MaterialTheme.typography.bodySmall)
    }
}

// ---------------------------------------------------------------- Dashboard
