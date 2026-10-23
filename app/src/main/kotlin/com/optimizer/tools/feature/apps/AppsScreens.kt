@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.optimizer.tools.feature.apps

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.InstallMobile
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.optimizer.tools.R
import com.optimizer.tools.core.ui.AppColors
import com.optimizer.tools.core.ui.AppIcon
import com.optimizer.tools.core.ui.Translate
import com.optimizer.tools.core.ui.UiText
import com.optimizer.tools.core.ui.formatBytes
import com.optimizer.tools.core.ui.formatEpoch
import com.optimizer.tools.core.ui.resolve
import dev.adboptimizer.core.model.AppAction
import dev.adboptimizer.core.model.AppFilter
import dev.adboptimizer.core.model.AppSort
import dev.adboptimizer.core.model.AppTarget
import dev.adboptimizer.core.model.DeviceInfo
import dev.adboptimizer.core.model.ManagedApp
import dev.adboptimizer.core.model.ParamKind

internal fun visibleApps(state: AppsState): List<ManagedApp> = AppListFilter.apply(state.apps, state.query, state.filter, state.sort)

internal fun appsVisible(state: AppsState): List<String> = visibleApps(state).map { it.packageName }

@Composable
internal fun AppsScreen(
    state: AppsState,
    t: Translate,
    device: DeviceInfo?,
    actions: List<dev.adboptimizer.core.model.ActionSpec>,
    apkInstallRunning: Boolean,
    apkInstallProgress: UiText?,
    apkInstallMessage: UiText?,
    onInstallApks: (List<Uri>) -> Unit,
    onOpenHostAppPicker: () -> Unit,
    onQuery: (String) -> Unit,
    onFilter: (AppFilter) -> Unit,
    onSort: (AppSort) -> Unit,
    onReload: () -> Unit,
    onOpen: (ManagedApp, AppAction) -> Unit,
    onDetails: (ManagedApp) -> Unit,
    onDismissDetails: () -> Unit,
    onToggle: (String) -> Unit,
    onSelectAll: () -> Unit,
    onClear: () -> Unit,
    onBatch: (AppAction) -> Unit,
    onConfirmAction: () -> Unit,
    onDismissAction: () -> Unit,
    onConfirmBatch: () -> Unit,
    onDismissBatch: () -> Unit,
    onDismissMessage: () -> Unit,
    onAdvancedAction: (String, String) -> Unit,
    onConfirmAdvancedAction: () -> Unit,
    onDismissAdvancedAction: () -> Unit,
) {
    val visible = visibleApps(state)
    val busy = state.busy
    var searchOpen by remember { mutableStateOf(false) }
    var filtersOpen by remember { mutableStateOf(false) }
    val searchFocus = remember { FocusRequester() }
    LaunchedEffect(searchOpen) {
        if (searchOpen) searchFocus.requestFocus()
    }
    val filtersActive = state.sort != AppSort.NAME || state.filter != setOf(AppFilter.USER)

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().padding(horizontal = 18.dp)) {
            Spacer(Modifier.height(10.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (searchOpen) {
                    OutlinedTextField(
                        value = state.query,
                        onValueChange = onQuery,
                        placeholder = { Text(t("apps.search")) },
                        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                        trailingIcon = {
                            IconButton(onClick = {
                                onQuery("")
                                searchOpen = false
                            }) {
                                Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.apps_close_search))
                            }
                        },
                        singleLine = true,
                        modifier = Modifier.weight(1f).focusRequester(searchFocus),
                    )
                } else {
                    Column(Modifier.weight(1f)) {
                        Text(t("apps.title"), style = MaterialTheme.typography.titleMedium)
                        Text(
                            stringResource(R.string.apps_count, visible.size, state.apps.size),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    IconButton(onClick = { searchOpen = true }) {
                        Icon(Icons.Filled.Search, contentDescription = t("apps.search"))
                    }
                    IconButton(onClick = { filtersOpen = true }) {
                        Box {
                            Icon(Icons.Filled.FilterList, contentDescription = stringResource(R.string.apps_filters_sort))
                            if (filtersActive) {
                                Box(
                                    Modifier
                                        .align(Alignment.TopEnd)
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.primary),
                                )
                            }
                        }
                    }
                    IconButton(onClick = onReload, enabled = !state.loading) {
                        if (state.loading) {
                            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Filled.Refresh, contentDescription = t("apps.reload"))
                        }
                    }
                }
            }

            if (apkInstallProgress != null || apkInstallMessage != null) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        apkInstallProgress?.let { Text(it.resolve(), style = MaterialTheme.typography.bodySmall) }
                        apkInstallMessage?.let { Text(it.resolve(), style = MaterialTheme.typography.bodySmall) }
                    }
                }
            }

            if (state.message != null) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                ) {
                    Row(Modifier.padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(state.message.resolve(), style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                        TextButton(onClick = onDismissMessage) { Text(stringResource(R.string.apps_ok)) }
                    }
                }
            }

            if (state.selection.isNotEmpty()) {
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(stringResource(R.string.apps_selection_count, state.selection.size), style = MaterialTheme.typography.labelSmall)
                    TextButton(onClick = onSelectAll) { Text(stringResource(R.string.apps_select_all)) }
                    TextButton(onClick = onClear) { Text(stringResource(R.string.apps_select_none)) }
                    if (state.target == AppTarget.REMOTE) {
                        TextButton(onClick = { onBatch(AppAction.FORCE_STOP) }) { Text(stringResource(R.string.apps_force_stop)) }
                        TextButton(onClick = { onBatch(AppAction.UNINSTALL) }) { Text(stringResource(R.string.apps_uninstall)) }
                    }
                }
            }

            when {
                state.loading && state.apps.isEmpty() -> {
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    ) {
                        Column(
                            Modifier.fillMaxWidth().padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 2.dp)
                            Text(stringResource(R.string.apps_loading), style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }

                visible.isEmpty() -> {
                    val hasSearchOrFilters = state.query.isNotBlank() || filtersActive
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    ) {
                        Column(
                            Modifier.fillMaxWidth().padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Icon(
                                if (hasSearchOrFilters) Icons.Filled.Search else Icons.Filled.Apps,
                                contentDescription = null,
                                modifier = Modifier.size(32.dp),
                                tint = MaterialTheme.colorScheme.primary,
                            )
                            Text(
                                stringResource(
                                    if (hasSearchOrFilters) {
                                        R.string.apps_no_results
                                    } else {
                                        R.string.apps_empty
                                    },
                                ),
                                style = MaterialTheme.typography.titleSmall,
                            )
                            Text(
                                stringResource(
                                    if (hasSearchOrFilters) {
                                        R.string.apps_no_results_hint
                                    } else {
                                        R.string.apps_empty_hint
                                    },
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            TextButton(onClick = {
                                if (hasSearchOrFilters) {
                                    onQuery("")
                                    onSort(AppSort.NAME)
                                    if (AppFilter.USER !in state.filter) onFilter(AppFilter.USER)
                                    state.filter.filterNot { it == AppFilter.USER }.forEach(onFilter)
                                } else {
                                    onReload()
                                }
                            }) {
                                Text(
                                    stringResource(
                                        if (hasSearchOrFilters) {
                                            R.string.apps_clear_filters
                                        } else {
                                            R.string.apps_refresh_list
                                        },
                                    ),
                                )
                            }
                        }
                    }
                }

                else -> {
                    LazyColumn(
                        Modifier.weight(1f),
                        contentPadding =
                            androidx.compose.foundation.layout
                                .PaddingValues(top = 8.dp, bottom = 88.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(visible, key = { "${state.target}:${it.packageName}" }) { app ->
                            AppCard(
                                app = app,
                                selected = app.packageName in state.selection,
                                busy = busy?.endsWith(":${app.packageName}") == true || busy == "batch",
                                local = state.target == AppTarget.LOCAL,
                                actions = actions,
                                sdk = device?.sdk ?: 0,
                                t = t,
                                onToggle = onToggle,
                                onDetails = onDetails,
                                onAction = onOpen,
                                onAdvancedAction = onAdvancedAction,
                            )
                        }
                    }
                }
            }
        }
        if (state.target == AppTarget.REMOTE) {
            FloatingActionButton(
                onClick = { if (!apkInstallRunning) onOpenHostAppPicker() },
                modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ) {
                if (apkInstallRunning) {
                    CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                } else {
                    Icon(Icons.Filled.InstallMobile, contentDescription = stringResource(R.string.apps_install))
                }
            }
        }
    }

    if (filtersOpen) {
        AppFiltersSheet(
            sort = state.sort,
            filters = state.filter,
            onSort = onSort,
            onFilter = onFilter,
            onReset = {
                onSort(AppSort.NAME)
                if (AppFilter.USER !in state.filter) onFilter(AppFilter.USER)
                state.filter.filterNot { it == AppFilter.USER }.forEach(onFilter)
            },
            onDismiss = { filtersOpen = false },
        )
    }

    state.detail?.let { app ->
        AppDetailSheet(
            app = app,
            loading = state.detailsLoading,
            onDismiss = onDismissDetails,
            onAction = onOpen,
        )
    }
    state.pendingAction?.let { pending ->
        ConfirmAppActionDialog(
            pending = pending,
            onConfirm = onConfirmAction,
            onDismiss = onDismissAction,
        )
    }
    state.pendingAdvancedAction?.let { pending ->
        ConfirmAdvancedActionDialog(
            pending = pending,
            t = t,
            onConfirm = onConfirmAdvancedAction,
            onDismiss = onDismissAdvancedAction,
        )
    }
    state.pendingBatch?.let { action ->
        ConfirmBatchDialog(
            action = action,
            count = state.selection.size,
            onConfirm = onConfirmBatch,
            onDismiss = onDismissBatch,
        )
    }
}

@Composable
internal fun ConfirmAdvancedActionDialog(
    pending: PendingAdvancedAction,
    t: Translate,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.apps_advanced_title, t(pending.action.titleKey))) },
        text = {
            Text(
                stringResource(R.string.apps_advanced_body, pending.packageName),
                style = MaterialTheme.typography.bodySmall,
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text(stringResource(R.string.apps_apply)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

@Composable
internal fun AppFiltersSheet(
    sort: AppSort,
    filters: Set<AppFilter>,
    onSort: (AppSort) -> Unit,
    onFilter: (AppFilter) -> Unit,
    onReset: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(stringResource(R.string.apps_filters_title), style = MaterialTheme.typography.titleLarge)
                TextButton(onClick = onReset) { Text(stringResource(R.string.apps_reset)) }
            }

            Text(stringResource(R.string.apps_sort_by), style = MaterialTheme.typography.labelLarge)
            AppSort.entries.forEach { option ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { onSort(option) }
                        .padding(horizontal = 8.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(appSortLabel(option), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    if (option == sort) {
                        Icon(Icons.Filled.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    }
                }
            }

            HorizontalDivider(Modifier.padding(vertical = 4.dp))

            Text(stringResource(R.string.apps_type), style = MaterialTheme.typography.labelLarge)
            AppFilter.entries.forEach { filter ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { onFilter(filter) }
                        .padding(horizontal = 8.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(appFilterLabel(filter), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    Checkbox(checked = filter in filters, onCheckedChange = { onFilter(filter) })
                }
            }
        }
    }
}

@Composable
internal fun AppCard(
    app: ManagedApp,
    selected: Boolean,
    busy: Boolean,
    local: Boolean,
    actions: List<dev.adboptimizer.core.model.ActionSpec>,
    sdk: Int,
    t: Translate,
    onToggle: (String) -> Unit,
    onDetails: (ManagedApp) -> Unit,
    onAction: (ManagedApp, AppAction) -> Unit,
    onAdvancedAction: (String, String) -> Unit,
) {
    var open by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth().clickable { onDetails(app) },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Row(
            Modifier.padding(horizontal = 8.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(checked = selected, onCheckedChange = { onToggle(app.packageName) })
            AppIcon(app = app, local = local)
            Column(
                Modifier.weight(1f).padding(start = 10.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    app.displayName,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    app.packageName,
                    style = MaterialTheme.typography.bodySmall,
                    color = AppColors.muted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (app.isSystem) {
                        Text(stringResource(R.string.apps_system), style = MaterialTheme.typography.labelSmall, color = AppColors.muted)
                    }
                    if (!app.enabled) {
                        Text(stringResource(R.string.apps_disabled), style = MaterialTheme.typography.labelSmall, color = AppColors.warning)
                    }
                    if (app.versionCode > 0) {
                        Text(
                            "v${app.versionName.ifBlank { app.versionCode.toString() }}",
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                    if (app.sizeBytes > 0) {
                        Text(formatBytes(app.sizeBytes), style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
            if (busy) {
                CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(6.dp))
            }
            Box {
                IconButton(onClick = { open = true }) {
                    Icon(Icons.Filled.MoreVert, contentDescription = t("apps.actions"))
                }
                DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                    menuAction(stringResource(R.string.apps_open), !busy) { onAction(app, AppAction.OPEN) }
                    menuAction(stringResource(R.string.apps_details), !busy) { onDetails(app) }
                    menuAction(stringResource(R.string.apps_force_stop), !busy) { onAction(app, AppAction.FORCE_STOP) }
                    menuAction(
                        stringResource(if (app.enabled) R.string.apps_disable else R.string.apps_enable),
                        !busy,
                    ) {
                        onAction(app, if (app.enabled) AppAction.DISABLE else AppAction.ENABLE)
                    }
                    menuAction(stringResource(R.string.apps_clear_data), !busy) { onAction(app, AppAction.CLEAR_DATA) }
                    menuAction(stringResource(R.string.apps_clear_cache), !busy) { onAction(app, AppAction.CLEAR_CACHE) }
                    menuAction(stringResource(R.string.apps_extract_apk), !busy) { onAction(app, AppAction.EXTRACT_APK) }
                    HorizontalDivider()
                    actions.filter { it.paramKind == ParamKind.PACKAGE && it.id !in NATIVE_ACTION_IDS }.forEach { action ->
                        val minSdk = action.minSdk
                        val supported = minSdk == null || sdk >= minSdk
                        menuAction(t(action.titleKey), supported && !busy) {
                            onAdvancedAction(action.id, app.packageName)
                        }
                    }
                    HorizontalDivider()
                    menuAction(stringResource(R.string.apps_uninstall), !busy, color = AppColors.danger) {
                        onAction(app, AppAction.UNINSTALL)
                    }
                }
            }
        }
    }
}

@Composable
internal fun menuAction(
    label: String,
    enabled: Boolean,
    color: Color = Color.Unspecified,
    onClick: () -> Unit,
) {
    DropdownMenuItem(
        text = { Text(label, color = color) },
        enabled = enabled,
        onClick = onClick,
    )
}

@Composable
internal fun AppDetailSheet(
    app: ManagedApp,
    loading: Boolean,
    onDismiss: () -> Unit,
    onAction: (ManagedApp, AppAction) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AppIcon(app = app, local = true, size = 44)
                Column(Modifier.padding(start = 12.dp)) {
                    Text(app.displayName, style = MaterialTheme.typography.titleMedium)
                    Text(app.packageName, style = MaterialTheme.typography.bodySmall, color = AppColors.muted)
                }
            }
            if (loading) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.apps_read_detail), style = MaterialTheme.typography.bodySmall)
                }
            }
            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            detailLine(stringResource(R.string.apps_version), "${app.versionName.ifBlank { "—" }} (${app.versionCode})")
            detailLine(stringResource(R.string.apps_sdk), if (app.targetSdk > 0) "${app.minSdk} → ${app.targetSdk}" else "—")
            detailLine(stringResource(R.string.apps_size), formatBytes(app.sizeBytes))
            detailLine(stringResource(R.string.apps_installed), formatEpoch(app.installedAt))
            detailLine(stringResource(R.string.apps_updated), formatEpoch(app.updatedAt))
            detailLine(
                stringResource(R.string.apps_status),
                stringResource(if (app.enabled) R.string.apps_enabled else R.string.apps_disabled),
            )
            detailLine(
                stringResource(R.string.apps_type_label),
                stringResource(if (app.isSystem) R.string.apps_system else R.string.apps_user),
            )
            app.installerPackage?.let { detailLine(stringResource(R.string.apps_installer), it) }
            if (app.apkPaths.isNotEmpty()) {
                Text(stringResource(R.string.apps_apk), style = MaterialTheme.typography.labelSmall)
                app.apkPaths.forEach {
                    Text(it, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
            if (app.requestedPermissions.isNotEmpty()) {
                Text(stringResource(R.string.apps_permissions, app.requestedPermissions.size), style = MaterialTheme.typography.labelSmall)
                app.requestedPermissions.take(20).forEach {
                    Text(it, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            Text(stringResource(R.string.apps_actions), style = MaterialTheme.typography.labelSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                TextButton(onClick = { onAction(app, AppAction.OPEN) }) { Text(stringResource(R.string.apps_open)) }
                TextButton(onClick = { onAction(app, AppAction.FORCE_STOP) }) { Text(stringResource(R.string.apps_close)) }
                TextButton(onClick = { onAction(app, AppAction.CLEAR_CACHE) }) { Text(stringResource(R.string.apps_cache)) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                TextButton(onClick = { onAction(app, AppAction.CLEAR_DATA) }) { Text(stringResource(R.string.apps_clear_data)) }
                TextButton(onClick = { onAction(app, AppAction.EXTRACT_APK) }) { Text(stringResource(R.string.apps_extract_apk)) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                TextButton(onClick = {
                    onAction(app, if (app.enabled) AppAction.DISABLE else AppAction.ENABLE)
                }) {
                    Text(stringResource(if (app.enabled) R.string.apps_disable else R.string.apps_enable))
                }
                TextButton(
                    onClick = { onAction(app, AppAction.UNINSTALL) },
                    colors = ButtonDefaults.textButtonColors(contentColor = AppColors.danger),
                ) { Text(stringResource(R.string.apps_uninstall)) }
            }
        }
    }
}

@Composable
internal fun ConfirmAppActionDialog(
    pending: PendingAppAction,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val label = appActionLabel(pending.action)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.apps_confirm_title, label)) },
        text = {
            Text(
                stringResource(
                    R.string.apps_confirm_body,
                    pending.app.displayName,
                    pending.app.packageName,
                    appActionWarning(pending.action),
                ),
                style = MaterialTheme.typography.bodySmall,
            )
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                colors = ButtonDefaults.textButtonColors(contentColor = AppColors.danger),
            ) { Text(label) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

@Composable
internal fun ConfirmBatchDialog(
    action: AppAction,
    count: Int,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.apps_batch_title, appActionLabel(action))) },
        text = {
            Text(
                stringResource(
                    R.string.apps_batch_body,
                    count,
                    appActionWarning(action),
                ),
                style = MaterialTheme.typography.bodySmall,
            )
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                colors = ButtonDefaults.textButtonColors(contentColor = AppColors.danger),
            ) { Text(appActionLabel(action)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

@Composable
internal fun detailLine(
    label: String,
    value: String,
) {
    Row(Modifier.fillMaxWidth()) {
        Text(label, style = MaterialTheme.typography.labelSmall, modifier = Modifier.width(88.dp))
        Text(value, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
    }
}
