package com.optimizer.tools.feature.files

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.optimizer.tools.R
import com.optimizer.tools.core.ui.UiText
import com.optimizer.tools.core.ui.asUiText
import com.optimizer.tools.core.ui.uiText
import com.optimizer.tools.data.files.FileEntry
import com.optimizer.tools.data.files.RemoteFileRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class TextPreview(
    val name: String,
    val content: String,
)

data class FilesState(
    val path: String = "/sdcard",
    val entries: List<FileEntry> = emptyList(),
    val visible: List<FileEntry> = emptyList(),
    val loading: Boolean = false,
    val deviceAvailable: Boolean = true,
    val query: String = "",
    val busy: Boolean = false,
    val selected: FileEntry? = null,
    val preview: TextPreview? = null,
    val pendingDelete: FileEntry? = null,
    val renameTarget: FileEntry? = null,
    val renameName: String = "",
    val creatingFolder: Boolean = false,
    val folderName: String = "",
    val message: UiText? = null,
    val error: UiText? = null,
) {
    val segments: List<Pair<String, String>> get() = RemoteFileRepository.segments(path)
    val canGoUp: Boolean get() = path != "/"
}

class FilesViewModel(
    private val environment: FilesEnvironment,
) : ViewModel() {
    private val _state = MutableStateFlow(FilesState())
    val state: StateFlow<FilesState> = _state.asStateFlow()

    fun onEnter() {
        if (!environment.hasDevice()) {
            _state.update { it.copy(deviceAvailable = false, error = uiText(R.string.files_error_no_device)) }
            return
        }
        _state.update { it.copy(deviceAvailable = true) }
        load(_state.value.path)
    }

    fun refresh() {
        load(_state.value.path)
    }

    fun open(path: String) {
        load(RemoteFileRepository.normalize(path))
    }

    fun up() {
        if (!_state.value.canGoUp) return
        load(RemoteFileRepository.parent(_state.value.path))
    }

    fun openEntry(entry: FileEntry) {
        if (entry.isDirectory) load(entry.path) else _state.update { it.copy(selected = entry) }
    }

    fun select(entry: FileEntry) {
        _state.update { it.copy(selected = entry) }
    }

    fun dismissSelection() {
        _state.update { it.copy(selected = null) }
    }

    fun setQuery(query: String) {
        _state.update { current -> current.copy(query = query, visible = filter(current.entries, query)) }
    }

    fun previewText(entry: FileEntry) {
        viewModelScope.launch {
            _state.update { it.copy(selected = null, busy = true, error = null) }
            try {
                val content = environment.readText(entry.path)
                _state.update { it.copy(busy = false, preview = TextPreview(entry.name, content)) }
            } catch (error: Exception) {
                _state.update {
                    it.copy(busy = false, error = error.message?.asUiText() ?: uiText(R.string.files_error_read))
                }
            }
        }
    }

    fun dismissPreview() {
        _state.update { it.copy(preview = null) }
    }

    fun startCreateFolder() {
        _state.update { it.copy(creatingFolder = true, folderName = "", selected = null) }
    }

    fun setFolderName(name: String) {
        _state.update { it.copy(folderName = name) }
    }

    fun cancelCreateFolder() {
        _state.update { it.copy(creatingFolder = false, folderName = "") }
    }

    fun confirmCreateFolder() {
        val name = _state.value.folderName.trim()
        if (name.isEmpty()) {
            _state.update { it.copy(error = uiText(R.string.files_error_name)) }
            return
        }
        runOperation(uiText(R.string.files_message_folder_created)) {
            environment.createDirectory(_state.value.path, name)
        }
    }

    fun startRename(entry: FileEntry) {
        _state.update { it.copy(renameTarget = entry, renameName = entry.name, selected = null) }
    }

    fun setRenameName(name: String) {
        _state.update { it.copy(renameName = name) }
    }

    fun cancelRename() {
        _state.update { it.copy(renameTarget = null, renameName = "") }
    }

    fun confirmRename() {
        val target = _state.value.renameTarget ?: return
        val name = _state.value.renameName.trim()
        if (name.isEmpty()) {
            _state.update { it.copy(error = uiText(R.string.files_error_name)) }
            return
        }
        _state.update { it.copy(renameTarget = null) }
        runOperation(uiText(R.string.files_message_renamed)) {
            environment.rename(target.path, name)
        }
    }

    fun requestDelete(entry: FileEntry) {
        _state.update { it.copy(pendingDelete = entry, selected = null) }
    }

    fun cancelDelete() {
        _state.update { it.copy(pendingDelete = null) }
    }

    fun confirmDelete() {
        val target = _state.value.pendingDelete ?: return
        _state.update { it.copy(pendingDelete = null) }
        runOperation(uiText(R.string.files_message_deleted, target.name)) {
            environment.delete(target.path)
        }
    }

    fun installApk(entry: FileEntry) {
        _state.update { it.copy(selected = null) }
        viewModelScope.launch {
            _state.update { it.copy(busy = true, error = null, message = null) }
            try {
                environment.installApk(entry.path)
                _state.update { it.copy(busy = false, message = uiText(R.string.files_message_installed, entry.name)) }
            } catch (error: Exception) {
                _state.update {
                    it.copy(busy = false, error = error.message?.asUiText() ?: uiText(R.string.files_error_install))
                }
            }
        }
    }

    fun dismissMessage() {
        _state.update { it.copy(message = null) }
    }

    fun dismissError() {
        _state.update { it.copy(error = null) }
    }

    private fun runOperation(
        success: UiText,
        block: suspend () -> Unit,
    ) {
        viewModelScope.launch {
            _state.update { it.copy(busy = true, error = null, message = null, creatingFolder = false, folderName = "") }
            try {
                block()
                _state.update { it.copy(busy = false, message = success) }
                load(_state.value.path)
            } catch (error: Exception) {
                _state.update {
                    it.copy(busy = false, error = error.message?.asUiText() ?: uiText(R.string.files_error))
                }
            }
        }
    }

    private fun load(path: String) {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null, path = path, query = "") }
            try {
                val entries = environment.list(path)
                _state.update {
                    it.copy(loading = false, entries = entries, visible = filter(entries, ""))
                }
            } catch (error: Exception) {
                _state.update {
                    it.copy(
                        loading = false,
                        entries = emptyList(),
                        visible = emptyList(),
                        error = error.message?.asUiText() ?: uiText(R.string.files_error_list),
                    )
                }
            }
        }
    }

    private fun filter(
        entries: List<FileEntry>,
        query: String,
    ): List<FileEntry> {
        val text = query.trim().lowercase()
        if (text.isEmpty()) return entries
        return entries.filter { it.name.lowercase().contains(text) }
    }

    companion object {
        fun factory(environment: FilesEnvironment): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T = FilesViewModel(environment) as T
            }
    }
}
