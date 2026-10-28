package com.optimizer.tools.feature.files

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.optimizer.tools.core.ui.AdbOptimizerTheme
import com.optimizer.tools.data.files.FileEntry
import com.optimizer.tools.domain.model.ThemeMode

private val previewEntries =
    listOf(
        FileEntry("Download", "/sdcard/Download", isDirectory = true, isLink = false, sizeBytes = 4096, mode = "drwxrws---"),
        FileEntry("backup.apk", "/sdcard/backup.apk", isDirectory = false, isLink = false, sizeBytes = 5_242_880, mode = "-rw-rw----"),
        FileEntry("notes.txt", "/sdcard/notes.txt", isDirectory = false, isLink = false, sizeBytes = 812, mode = "-rw-rw----"),
    )

@Preview(showBackground = true, name = "Archivos")
@Composable
private fun FilesPreview() {
    AdbOptimizerTheme(themeMode = ThemeMode.DARK) {
        FilesScreen(
            state = FilesState(path = "/sdcard", entries = previewEntries, visible = previewEntries),
            onBack = {},
            onRefresh = {},
            onOpen = {},
            onUp = {},
            onEntry = {},
            onQuery = {},
            onPreview = {},
            onDismissPreview = {},
            onRename = {},
            onRenameName = {},
            onConfirmRename = {},
            onCancelRename = {},
            onRequestDelete = {},
            onConfirmDelete = {},
            onCancelDelete = {},
            onStartCreateFolder = {},
            onFolderName = {},
            onConfirmCreateFolder = {},
            onCancelCreateFolder = {},
            onInstall = {},
            onDismissSelection = {},
            onDismissMessage = {},
            onDismissError = {},
        )
    }
}
