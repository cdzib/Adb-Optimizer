package com.optimizer.tools.feature.files

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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.optimizer.tools.R
import com.optimizer.tools.core.ui.DesignDivider
import com.optimizer.tools.core.ui.DesignEmptyState
import com.optimizer.tools.core.ui.DesignIconBadge
import com.optimizer.tools.core.ui.DesignSearchField
import com.optimizer.tools.core.ui.DesignTone
import com.optimizer.tools.core.ui.ToolEmptyState
import com.optimizer.tools.core.ui.ToolTopBar
import com.optimizer.tools.core.ui.formatBytes
import com.optimizer.tools.core.ui.resolve
import com.optimizer.tools.data.files.FileEntry

@Composable
internal fun FilesScreen(
    state: FilesState,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onOpen: (String) -> Unit,
    onUp: () -> Unit,
    onEntry: (FileEntry) -> Unit,
    onQuery: (String) -> Unit,
    onPreview: (FileEntry) -> Unit,
    onDismissPreview: () -> Unit,
    onRename: (FileEntry) -> Unit,
    onRenameName: (String) -> Unit,
    onConfirmRename: () -> Unit,
    onCancelRename: () -> Unit,
    onRequestDelete: (FileEntry) -> Unit,
    onConfirmDelete: () -> Unit,
    onCancelDelete: () -> Unit,
    onStartCreateFolder: () -> Unit,
    onFolderName: (String) -> Unit,
    onConfirmCreateFolder: () -> Unit,
    onCancelCreateFolder: () -> Unit,
    onInstall: (FileEntry) -> Unit,
    onDismissSelection: () -> Unit,
    onDismissMessage: () -> Unit,
    onDismissError: () -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        ToolTopBar(
            title = stringResource(R.string.files_title),
            subtitle = state.path,
            onBack = onBack,
            actions = {
                IconButton(onClick = onStartCreateFolder) {
                    Icon(Icons.Filled.CreateNewFolder, contentDescription = stringResource(R.string.files_new_folder))
                }
                IconButton(onClick = onUp, enabled = state.canGoUp) {
                    Icon(Icons.Filled.ArrowUpward, contentDescription = stringResource(R.string.files_up))
                }
                IconButton(onClick = onRefresh) {
                    Icon(Icons.Filled.Refresh, contentDescription = stringResource(R.string.files_refresh))
                }
            },
        )
        when {
            !state.deviceAvailable -> {
                ToolEmptyState(
                    icon = Icons.Filled.PhoneAndroid,
                    title = stringResource(R.string.files_no_device_title),
                    description = stringResource(R.string.files_no_device),
                )
            }

            else -> {
                FilesBody(
                    state = state,
                    onOpen = onOpen,
                    onEntry = onEntry,
                    onQuery = onQuery,
                    onStartCreateFolder = onStartCreateFolder,
                    onDismissMessage = onDismissMessage,
                    onDismissError = onDismissError,
                )
            }
        }
        state.selected?.let { entry ->
            EntryActionsDialog(
                entry = entry,
                onDismiss = onDismissSelection,
                onOpenDirectory = { onOpen(entry.path) },
                onPreview = { onPreview(entry) },
                onRename = { onRename(entry) },
                onDelete = { onRequestDelete(entry) },
                onInstall = { onInstall(entry) },
            )
        }
        state.preview?.let { preview ->
            PreviewDialog(preview, onDismissPreview)
        }
        state.renameTarget?.let {
            InputDialog(
                title = stringResource(R.string.files_rename),
                value = state.renameName,
                label = stringResource(R.string.files_name),
                onValue = onRenameName,
                onConfirm = onConfirmRename,
                onDismiss = onCancelRename,
            )
        }
        if (state.creatingFolder) {
            InputDialog(
                title = stringResource(R.string.files_new_folder),
                value = state.folderName,
                label = stringResource(R.string.files_name),
                onValue = onFolderName,
                onConfirm = onConfirmCreateFolder,
                onDismiss = onCancelCreateFolder,
            )
        }
        state.pendingDelete?.let { entry ->
            DeleteDialog(entry, onConfirmDelete, onCancelDelete)
        }
    }
}

@Composable
private fun FilesBody(
    state: FilesState,
    onOpen: (String) -> Unit,
    onEntry: (FileEntry) -> Unit,
    onQuery: (String) -> Unit,
    onStartCreateFolder: () -> Unit,
    onDismissMessage: () -> Unit,
    onDismissError: () -> Unit,
) {
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = { onOpen("/") }) { Text("/") }
            state.segments.forEach { (name, path) ->
                TextButton(
                    onClick = { onOpen(path) },
                    contentPadding =
                        androidx.compose.foundation.layout
                            .PaddingValues(horizontal = 6.dp),
                ) {
                    Text(name, maxLines = 1)
                }
                Text("›", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        state.error?.let { error ->
            Notice(error.resolve(), MaterialTheme.colorScheme.error, onDismissError)
        }
        state.message?.let { message ->
            Notice(message.resolve(), MaterialTheme.colorScheme.primary, onDismissMessage)
        }
        if (state.busy) {
            LinearProgressIndicator(Modifier.fillMaxWidth())
            Spacer(Modifier.height(4.dp))
        }
        DesignSearchField(
            value = state.query,
            onValueChange = onQuery,
            placeholder = stringResource(R.string.files_search),
        )
        Spacer(Modifier.height(10.dp))
        when {
            state.loading -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            }

            state.visible.isEmpty() -> {
                DesignEmptyState(
                    icon = Icons.Filled.FolderOpen,
                    title = stringResource(R.string.files_empty),
                    body = stringResource(R.string.files_empty_body),
                    modifier = Modifier.padding(top = 8.dp),
                )
            }

            else -> {
                LazyColumn(
                    Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.surface),
                ) {
                    itemsIndexed(state.visible, key = { _, entry -> entry.path }) { index, entry ->
                        if (index > 0) DesignDivider()
                        EntryRow(entry = entry, onClick = { onEntry(entry) })
                    }
                }
            }
        }
        Column(
            Modifier
                .fillMaxWidth()
                .padding(top = 10.dp, bottom = 12.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(MaterialTheme.colorScheme.surface)
                .padding(horizontal = 14.dp, vertical = 10.dp),
        ) {
            Text(
                stringResource(R.string.files_protected_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun EntryRow(
    entry: FileEntry,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    Row(
        Modifier
            .fillMaxWidth()
            .onFocusChanged { focused = it.isFocused }
            .background(if (focused) MaterialTheme.colorScheme.surfaceContainerHigh else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        DesignIconBadge(
            icon = if (entry.isDirectory) Icons.Filled.Folder else Icons.Filled.Description,
            tone =
                when {
                    entry.isDirectory -> DesignTone.Amber
                    entry.isApk -> DesignTone.Red
                    else -> DesignTone.Cyan
                },
        )
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                entry.name,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                if (entry.isDirectory) {
                    stringResource(R.string.files_folder)
                } else {
                    "${formatBytes(entry.sizeBytes)} · ${entry.mode}"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
            modifier = Modifier.size(18.dp),
        )
    }
}

@Composable
private fun EntryActionsDialog(
    entry: FileEntry,
    onDismiss: () -> Unit,
    onOpenDirectory: () -> Unit,
    onPreview: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    onInstall: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(entry.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        text = { Text(entry.path, style = MaterialTheme.typography.bodySmall) },
        confirmButton = {
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (entry.isDirectory) {
                    TextButton(onClick = onOpenDirectory) { Text(stringResource(R.string.files_open)) }
                } else {
                    TextButton(onClick = onPreview) { Text(stringResource(R.string.files_preview)) }
                    if (entry.isApk) {
                        TextButton(onClick = onInstall) { Text(stringResource(R.string.files_install_apk)) }
                    }
                }
                TextButton(onClick = onRename) { Text(stringResource(R.string.files_rename)) }
                TextButton(onClick = onDelete) { Text(stringResource(R.string.files_delete)) }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.profiles_cancel)) } },
    )
}

@Composable
private fun PreviewDialog(
    preview: TextPreview,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(preview.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        text = {
            Box(Modifier.heightIn(max = 360.dp)) {
                androidx.compose.foundation.text.selection.SelectionContainer {
                    Text(
                        preview.content.ifBlank { stringResource(R.string.files_empty_file) },
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_close)) } },
    )
}

@Composable
private fun InputDialog(
    title: String,
    value: String,
    label: String,
    onValue: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(value = value, onValueChange = onValue, label = { Text(label) }, singleLine = true)
        },
        confirmButton = {
            Button(onClick = onConfirm, enabled = value.isNotBlank()) { Text(stringResource(R.string.files_confirm)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.profiles_cancel)) } },
    )
}

@Composable
private fun DeleteDialog(
    entry: FileEntry,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.files_delete_title)) },
        text = { Text(stringResource(R.string.files_delete_body, entry.name)) },
        confirmButton = { Button(onClick = onConfirm) { Text(stringResource(R.string.files_delete)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.profiles_cancel)) } },
    )
}

@Composable
private fun Notice(
    text: String,
    tint: androidx.compose.ui.graphics.Color,
    onDismiss: () -> Unit,
) {
    Card(
        Modifier.fillMaxWidth().padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = tint.copy(alpha = 0.12f)),
        shape = RoundedCornerShape(10.dp),
    ) {
        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(text, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_close)) }
        }
    }
}
