@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.optimizer.tools.feature.install

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.optimizer.tools.R
import com.optimizer.tools.core.ui.AppIcon
import com.optimizer.tools.core.ui.formatBytes
import com.optimizer.tools.core.ui.resolve
import com.optimizer.tools.domain.model.ShizukuLocalStatus
import dev.adboptimizer.core.model.ManagedApp

@Composable
internal fun HostAppsInstallScreen(
    state: InstallState,
    shizukuStatus: ShizukuLocalStatus,
    onBack: () -> Unit,
    onInstall: (List<String>) -> Unit,
    onInstallApks: (List<Uri>) -> Unit,
) {
    var selectedPackages by remember { mutableStateOf(emptySet<String>()) }
    val apkPicker =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
            if (uris.isNotEmpty()) onInstallApks(uris)
        }
    Column(
        Modifier.fillMaxSize().padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(top = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack, modifier = Modifier.offset(x = (-12).dp)) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.install_back_to_apps))
            }
            Text(stringResource(R.string.install_title), style = MaterialTheme.typography.titleLarge)
        }
        OutlinedButton(
            onClick = { apkPicker.launch(arrayOf("application/vnd.android.package-archive")) },
            enabled = !state.running,
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
        ) {
            Icon(Icons.Filled.FolderOpen, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.install_choose_apk))
        }
        if (state.progress != null || state.message != null) {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (state.running) {
                        LinearProgressIndicator(Modifier.fillMaxWidth())
                    }
                    state.progress?.let { progress ->
                        Text(progress.resolve(), style = MaterialTheme.typography.bodySmall)
                    }
                    state.message?.let { message ->
                        Text(message.resolve(), style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
        Text(
            stringResource(R.string.install_intro),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        when {
            shizukuStatus != ShizukuLocalStatus.READY -> {
                Box(
                    Modifier.weight(1f).fillMaxWidth(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(stringResource(R.string.install_shizuku_hint))
                }
            }

            state.hostAppsLoading -> {
                Box(
                    Modifier.weight(1f).fillMaxWidth(),
                    contentAlignment = Alignment.Center,
                ) { CircularProgressIndicator() }
            }

            state.hostApps.isEmpty() -> {
                Box(
                    Modifier.weight(1f).fillMaxWidth(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(state.hostAppsError?.resolve() ?: stringResource(R.string.install_no_user_apps))
                }
            }

            else -> {
                LazyColumn(
                    Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding =
                        androidx.compose.foundation.layout
                            .PaddingValues(vertical = 8.dp),
                ) {
                    items(state.hostApps, key = ManagedApp::packageName) { app ->
                        val selected = app.packageName in selectedPackages
                        Card(Modifier.fillMaxWidth()) {
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        selectedPackages =
                                            if (selected) selectedPackages - app.packageName else selectedPackages + app.packageName
                                    }.padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                AppIcon(app, local = true)
                                Column(Modifier.weight(1f).padding(start = 12.dp)) {
                                    Text(app.displayName, style = MaterialTheme.typography.titleSmall)
                                    Text(
                                        stringResource(
                                            R.string.install_app_detail,
                                            app.packageName,
                                            formatBytes(app.sizeBytes),
                                            if (app.hasSplits) stringResource(R.string.install_app_split) else "",
                                        ),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                                Checkbox(
                                    checked = selected,
                                    onCheckedChange = { checked ->
                                        selectedPackages =
                                            if (checked) selectedPackages + app.packageName else selectedPackages - app.packageName
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
        Button(
            onClick = { onInstall(selectedPackages.toList()) },
            enabled = selectedPackages.isNotEmpty() && !state.running && !state.hostAppsLoading,
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
        ) {
            if (state.running) {
                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
            } else {
                Text(stringResource(R.string.install_selected, selectedPackages.size))
            }
        }
    }
}
