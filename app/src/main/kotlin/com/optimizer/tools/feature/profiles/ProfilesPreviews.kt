package com.optimizer.tools.feature.profiles

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.optimizer.tools.core.ui.AdbOptimizerTheme
import com.optimizer.tools.domain.model.ThemeMode
import com.optimizer.tools.domain.profile.AppProfile
import com.optimizer.tools.domain.profile.ProfileApp
import com.optimizer.tools.domain.profile.ProfileAppState
import com.optimizer.tools.domain.profile.ProfilePlan
import com.optimizer.tools.domain.profile.ProfileStep
import com.optimizer.tools.domain.profile.ProfileStepKind

private val previewProfile =
    AppProfile(
        id = "preview",
        name = "Teléfono limpio",
        createdAt = 1_760_000_000_000,
        apps =
            listOf(
                ProfileApp("com.example.player", "Player", ProfileAppState.DISABLED),
                ProfileApp("com.example.social", "Social", ProfileAppState.UNINSTALLED),
                ProfileApp("com.example.notes", "Notes", ProfileAppState.ENABLED),
            ),
        watched = true,
    )

private val previewPlan =
    ProfilePlan(
        steps =
            listOf(
                ProfileStep(ProfileStepKind.REINSTALL, listOf("com.example.social")),
                ProfileStep(ProfileStepKind.DISABLE, listOf("com.example.social", "com.example.player")),
            ),
        same = 1,
        missing = 0,
        blocked = 0,
    )

private val previewState =
    ProfilesState(
        profiles = listOf(previewProfile),
        drift = ProfilePreview(previewProfile, previewPlan),
        lastRun = null,
        deviceAvailable = true,
        message = null,
    )

@Composable
private fun PreviewShell(content: @Composable () -> Unit) {
    AdbOptimizerTheme(themeMode = ThemeMode.DARK) {
        androidx.compose.material3.Surface(modifier = Modifier.padding(0.dp)) { content() }
    }
}

@Preview(showBackground = true, name = "Perfiles con drift")
@Composable
private fun ProfilesWithDriftPreview() {
    PreviewShell {
        ProfilesScreen(
            state = previewState,
            onBack = {},
            onRefresh = {},
            onStartCreate = {},
            onCancelCreate = {},
            onNameDraft = {},
            onCreate = {},
            onPreview = {},
            onDismissPreview = {},
            onApply = {},
            onToggleWatch = {},
            onRequestDelete = {},
            onCancelDelete = {},
            onConfirmDelete = {},
            onUndo = {},
            onDismissMessage = {},
            onDismissError = {},
            onDismissDrift = {},
        )
    }
}

@Preview(showBackground = true, name = "Perfiles vacío")
@Composable
private fun ProfilesEmptyPreview() {
    PreviewShell {
        ProfilesScreen(
            state = ProfilesState(deviceAvailable = true),
            onBack = {},
            onRefresh = {},
            onStartCreate = {},
            onCancelCreate = {},
            onNameDraft = {},
            onCreate = {},
            onPreview = {},
            onDismissPreview = {},
            onApply = {},
            onToggleWatch = {},
            onRequestDelete = {},
            onCancelDelete = {},
            onConfirmDelete = {},
            onUndo = {},
            onDismissMessage = {},
            onDismissError = {},
            onDismissDrift = {},
        )
    }
}
