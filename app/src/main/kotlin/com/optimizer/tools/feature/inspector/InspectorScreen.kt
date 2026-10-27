package com.optimizer.tools.feature.inspector

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.optimizer.tools.R
import com.optimizer.tools.core.ui.UiText
import com.optimizer.tools.core.ui.resolve

@Composable
internal fun InspectorScreen(
    state: InspectorState,
    onBack: () -> Unit,
    onTab: (InspectorTab) -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.inspector_back))
            }
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.inspector_title), style = MaterialTheme.typography.titleLarge)
                Text(
                    state.packageName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        HorizontalDivider()
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)) {
            InspectorTab.entries.forEach { tab ->
                FilterChip(
                    selected = state.tab == tab,
                    onClick = { onTab(tab) },
                    label = { Text(stringResource(tab.label())) },
                    modifier = Modifier.padding(end = 6.dp),
                )
            }
        }
        if (state.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
        InspectorBody(state)
    }
}

@Composable
private fun InspectorBody(state: InspectorState) {
    val error = state.error
    when {
        !state.connected -> InspectorMessage(stringResource(R.string.inspector_no_device))
        error != null -> InspectorMessage(error.resolve())
        state.loading -> Unit
        else -> InspectorContent(state)
    }
}

@Composable
private fun InspectorContent(state: InspectorState) {
    val inspection = state.inspection
    when (state.tab) {
        InspectorTab.PERMISSIONS -> {
            if (inspection.permissions.isEmpty()) {
                InspectorMessage(stringResource(R.string.inspector_empty))
            } else {
                InspectorList(inspection.permissions) { permission ->
                    PermissionRow(permission)
                }
            }
        }

        InspectorTab.APP_OPS -> {
            if (inspection.appOps.isEmpty()) {
                InspectorMessage(stringResource(R.string.inspector_empty))
            } else {
                InspectorList(inspection.appOps) { op ->
                    ItemRow(op.name, op.mode)
                }
            }
        }

        InspectorTab.COMPONENTS -> {
            if (inspection.components.isEmpty()) {
                InspectorMessage(stringResource(R.string.inspector_empty))
            } else {
                InspectorList(inspection.components) { component ->
                    ItemRow(component.className, componentKindLabel(component.kind))
                }
            }
        }

        InspectorTab.SIGNATURE -> {
            val signature = inspection.signature
            if (signature == null) {
                InspectorMessage(stringResource(R.string.inspector_empty))
            } else {
                InspectorMessage(stringResource(R.string.inspector_signature_note, signature))
            }
        }
    }
}

@Composable
private fun <T> InspectorList(
    items: List<T>,
    row: @Composable (T) -> Unit,
) {
    LazyColumn(Modifier.fillMaxSize()) {
        items(items) { item ->
            row(item)
            HorizontalDivider()
        }
    }
}

@Composable
private fun PermissionRow(permission: AppPermission) {
    val status =
        when (permission.granted) {
            true -> stringResource(R.string.inspector_granted)
            false -> stringResource(R.string.inspector_denied)
            null -> stringResource(R.string.inspector_declared)
        }
    ItemRow(permission.name, status)
}

@Composable
private fun ItemRow(
    title: String,
    detail: String,
) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) {
        Text(title, style = MaterialTheme.typography.bodyMedium, fontFamily = FontFamily.Monospace)
        Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun InspectorMessage(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(24.dp),
    )
}

@Composable
private fun componentKindLabel(kind: ComponentKind): String =
    when (kind) {
        ComponentKind.ACTIVITY -> stringResource(R.string.inspector_activity)
        ComponentKind.SERVICE -> stringResource(R.string.inspector_service)
        ComponentKind.RECEIVER -> stringResource(R.string.inspector_receiver)
        ComponentKind.PROVIDER -> stringResource(R.string.inspector_provider)
    }

private fun InspectorTab.label(): Int =
    when (this) {
        InspectorTab.PERMISSIONS -> R.string.inspector_tab_permissions
        InspectorTab.APP_OPS -> R.string.inspector_tab_appops
        InspectorTab.COMPONENTS -> R.string.inspector_tab_components
        InspectorTab.SIGNATURE -> R.string.inspector_tab_signature
    }

private val sample =
    AppInspection(
        permissions = listOf(AppPermission("android.permission.INTERNET", true), AppPermission("android.permission.CAMERA", false)),
        appOps = listOf(AppOp("RUN_IN_BACKGROUND", "ignore")),
        components = listOf(AppComponent(ComponentKind.ACTIVITY, "com.example.player.MainActivity")),
        signature = "v3 · 71c782a3",
    )

@Preview(name = "Inspector permisos")
@Composable
private fun InspectorPermissionsPreview() {
    InspectorScreen(
        state = InspectorState(packageName = "com.example.player", connected = true, inspection = sample),
        onBack = {},
        onTab = {},
    )
}

@Preview(name = "Inspector appops")
@Composable
private fun InspectorOpsPreview() {
    InspectorScreen(
        state = InspectorState(packageName = "com.example.player", tab = InspectorTab.APP_OPS, connected = true, inspection = sample),
        onBack = {},
        onTab = {},
    )
}

@Preview(name = "Inspector firma")
@Composable
private fun InspectorSignaturePreview() {
    InspectorScreen(
        state = InspectorState(packageName = "com.example.player", tab = InspectorTab.SIGNATURE, connected = true, inspection = sample),
        onBack = {},
        onTab = {},
    )
}

@Preview(name = "Inspector sin dispositivo")
@Composable
private fun InspectorDisconnectedPreview() {
    InspectorScreen(
        state = InspectorState(connected = false, error = UiText.Resource(R.string.inspector_no_device)),
        onBack = {},
        onTab = {},
    )
}
