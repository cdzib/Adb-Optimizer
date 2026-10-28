package com.optimizer.tools.feature.profiles

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.optimizer.tools.R
import com.optimizer.tools.core.ui.UiText
import com.optimizer.tools.core.ui.asUiText
import com.optimizer.tools.core.ui.uiText
import com.optimizer.tools.domain.profile.AppProfile
import com.optimizer.tools.domain.profile.ProfilePlan
import com.optimizer.tools.domain.profile.ProfileProgress
import com.optimizer.tools.domain.profile.ProfileRun
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

/** Un perfil con el plan que lo llevaria al estado actual del dispositivo. */
data class ProfilePreview(
    val profile: AppProfile,
    val plan: ProfilePlan,
)

data class ProfilesState(
    val loading: Boolean = false,
    val profiles: List<AppProfile> = emptyList(),
    val drift: ProfilePreview? = null,
    val preview: ProfilePreview? = null,
    val previewLoading: Boolean = false,
    val creating: Boolean = false,
    val nameDraft: String = "",
    val pendingDelete: AppProfile? = null,
    val running: Boolean = false,
    val done: Int = 0,
    val total: Int = 0,
    val currentPackage: String? = null,
    val lastRun: ProfileRun? = null,
    val deviceAvailable: Boolean = true,
    val message: UiText? = null,
    val error: UiText? = null,
)

class ProfilesViewModel(
    private val environment: ProfilesEnvironment,
) : ViewModel() {
    private val _state = MutableStateFlow(ProfilesState())
    val state: StateFlow<ProfilesState> = _state.asStateFlow()

    private var loadJob: Job? = null

    fun onEnter() {
        val available = environment.hasDevice()
        _state.update { it.copy(deviceAvailable = available) }
        if (available) refresh() else showNoDevice()
    }

    fun refresh() {
        if (loadJob?.isActive == true) return
        loadJob =
            viewModelScope.launch {
                _state.update { it.copy(loading = true, error = null) }
                try {
                    val profiles = environment.profiles()
                    val watched = profiles.firstOrNull { it.watched }
                    val drift = watched?.let { environment.plan(it).toPreview(it) }?.takeIf { !it.plan.isEmpty }
                    _state.update {
                        it.copy(
                            loading = false,
                            profiles = profiles,
                            drift = drift,
                            lastRun = environment.lastRun(),
                        )
                    }
                } catch (error: Exception) {
                    _state.update {
                        it.copy(
                            loading = false,
                            error = error.message?.asUiText() ?: uiText(R.string.profiles_error),
                        )
                    }
                }
            }
    }

    fun startCreate() {
        _state.update { it.copy(creating = true, nameDraft = "", error = null) }
    }

    fun cancelCreate() {
        _state.update { it.copy(creating = false, nameDraft = "") }
    }

    fun setNameDraft(value: String) {
        _state.update { it.copy(nameDraft = value.take(MAX_NAME_LENGTH)) }
    }

    fun createProfile() {
        val name = _state.value.nameDraft.trim()
        if (name.isEmpty()) {
            _state.update { it.copy(error = uiText(R.string.profiles_error_name_required)) }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(creating = false, error = null, previewLoading = true) }
            try {
                val apps = environment.snapshot()
                if (apps.isEmpty()) {
                    _state.update { it.copy(previewLoading = false, error = uiText(R.string.profiles_error_empty_snapshot)) }
                    return@launch
                }
                val profile =
                    AppProfile(
                        id = UUID.randomUUID().toString(),
                        name = name,
                        createdAt = System.currentTimeMillis(),
                        apps = apps,
                    )
                environment.save(profile)
                _state.update {
                    it.copy(
                        previewLoading = false,
                        message = uiText(R.string.profiles_message_created, profile.name, profile.appCount),
                    )
                }
                refresh()
            } catch (error: Exception) {
                _state.update {
                    it.copy(
                        previewLoading = false,
                        error = error.message?.asUiText() ?: uiText(R.string.profiles_error),
                    )
                }
            }
        }
    }

    fun preview(profile: AppProfile) {
        viewModelScope.launch {
            _state.update { it.copy(previewLoading = true, error = null) }
            try {
                val plan = environment.plan(profile)
                _state.update { it.copy(previewLoading = false, preview = plan.toPreview(profile)) }
            } catch (error: Exception) {
                _state.update {
                    it.copy(
                        previewLoading = false,
                        error = error.message?.asUiText() ?: uiText(R.string.profiles_error),
                    )
                }
            }
        }
    }

    fun dismissPreview() {
        _state.update { it.copy(preview = null) }
    }

    fun apply(profile: AppProfile) {
        if (_state.value.running) return
        viewModelScope.launch {
            _state.update {
                it.copy(
                    running = true,
                    done = 0,
                    total = 0,
                    currentPackage = null,
                    preview = null,
                    error = null,
                    message = null,
                )
            }
            try {
                val run =
                    environment.apply(profile) { progress ->
                        onProgress(progress)
                    }
                _state.update {
                    it.copy(
                        running = false,
                        currentPackage = null,
                        lastRun = run,
                        message =
                            if (run.failures.isEmpty()) {
                                uiText(R.string.profiles_message_applied, run.count)
                            } else {
                                uiText(R.string.profiles_message_applied_partial, run.count, run.failures.size)
                            },
                    )
                }
                refresh()
            } catch (error: Exception) {
                _state.update {
                    it.copy(
                        running = false,
                        currentPackage = null,
                        error = error.message?.asUiText() ?: uiText(R.string.profiles_error),
                    )
                }
            }
        }
    }

    private fun onProgress(progress: ProfileProgress) {
        _state.update {
            it.copy(
                done = progress.done,
                total = progress.total,
                currentPackage = progress.packageName,
            )
        }
    }

    fun dismissDrift() {
        _state.update { it.copy(drift = null) }
    }

    fun requestDelete(profile: AppProfile) {
        _state.update { it.copy(pendingDelete = profile) }
    }

    fun cancelDelete() {
        _state.update { it.copy(pendingDelete = null) }
    }

    fun confirmDelete() {
        val profile = _state.value.pendingDelete ?: return
        viewModelScope.launch {
            try {
                environment.delete(profile.id)
                _state.update {
                    it.copy(
                        pendingDelete = null,
                        message = uiText(R.string.profiles_message_deleted, profile.name),
                    )
                }
                refresh()
            } catch (error: Exception) {
                _state.update {
                    it.copy(
                        pendingDelete = null,
                        error = error.message?.asUiText() ?: uiText(R.string.profiles_error),
                    )
                }
            }
        }
    }

    fun toggleWatch(profile: AppProfile) {
        viewModelScope.launch {
            try {
                environment.setWatched(profile.id, !profile.watched)
                _state.update {
                    it.copy(
                        message =
                            if (profile.watched) {
                                uiText(R.string.profiles_message_watch_off)
                            } else {
                                uiText(R.string.profiles_message_watch_on, profile.name)
                            },
                    )
                }
                refresh()
            } catch (error: Exception) {
                _state.update { it.copy(error = error.message?.asUiText() ?: uiText(R.string.profiles_error)) }
            }
        }
    }

    fun undoLastRun() {
        val run = _state.value.lastRun ?: return
        if (_state.value.running || run.changes.isEmpty()) return
        viewModelScope.launch {
            _state.update { it.copy(running = true, error = null, message = null) }
            try {
                val restored = environment.undo(run)
                _state.update {
                    it.copy(
                        running = false,
                        message = uiText(R.string.profiles_message_undone, restored),
                    )
                }
                refresh()
            } catch (error: Exception) {
                _state.update {
                    it.copy(
                        running = false,
                        error = error.message?.asUiText() ?: uiText(R.string.profiles_error),
                    )
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

    private fun showNoDevice() {
        _state.update {
            it.copy(
                loading = false,
                deviceAvailable = false,
                error = uiText(R.string.profiles_error_no_device),
            )
        }
    }

    companion object {
        const val MAX_NAME_LENGTH = 60

        fun factory(environment: ProfilesEnvironment): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T = ProfilesViewModel(environment) as T
            }
    }
}

private fun ProfilePlan.toPreview(profile: AppProfile): ProfilePreview = ProfilePreview(profile, this)
