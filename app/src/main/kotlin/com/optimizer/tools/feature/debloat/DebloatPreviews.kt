package com.optimizer.tools.feature.debloat

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.optimizer.tools.core.ui.AdbOptimizerTheme
import com.optimizer.tools.domain.debloat.DebloatEntry
import com.optimizer.tools.domain.debloat.DebloatRisk
import com.optimizer.tools.domain.debloat.DebloatSuggestion
import com.optimizer.tools.domain.model.ThemeMode
import com.optimizer.tools.domain.profile.ProfileAppState

private val previewSuggestions =
    listOf(
        DebloatSuggestion(
            entry =
                DebloatEntry(
                    "com.facebook.katana",
                    "Oem",
                    DebloatRisk.RECOMMENDED,
                    "Facebook app; safe to remove if you do not use it.",
                ),
            label = "Facebook",
            app = null,
            state = ProfileAppState.ENABLED,
        ),
        DebloatSuggestion(
            entry = DebloatEntry("com.samsung.android.bixby.agent", "Oem", DebloatRisk.ADVANCED, "Bixby voice assistant."),
            label = "Bixby Voice",
            app = null,
            state = ProfileAppState.DISABLED,
        ),
    )

@Composable
private fun Shell(content: @Composable () -> Unit) {
    AdbOptimizerTheme(themeMode = ThemeMode.DARK) { content() }
}

@Preview(showBackground = true, name = "Debloater con catálogo")
@Composable
private fun DebloatReadyPreview() {
    Shell {
        DebloatScreen(
            state =
                DebloatState(
                    catalogReady = true,
                    catalogSize = 3119,
                    suggestions = previewSuggestions,
                    visible = previewSuggestions,
                    selection = setOf("com.facebook.katana"),
                ),
            onBack = {},
            onRefresh = {},
            onDownload = {},
            onQuery = {},
            onToggleRisk = {},
            onToggle = {},
            onSelectAll = {},
            onClearSelection = {},
            onRequestAction = {},
            onCancelAction = {},
            onConfirmAction = {},
            onUndo = {},
            onDismissMessage = {},
            onDismissError = {},
        )
    }
}

@Preview(showBackground = true, name = "Debloater sin lista")
@Composable
private fun DebloatMissingCatalogPreview() {
    Shell {
        DebloatScreen(
            state = DebloatState(catalogReady = false),
            onBack = {},
            onRefresh = {},
            onDownload = {},
            onQuery = {},
            onToggleRisk = {},
            onToggle = {},
            onSelectAll = {},
            onClearSelection = {},
            onRequestAction = {},
            onCancelAction = {},
            onConfirmAction = {},
            onUndo = {},
            onDismissMessage = {},
            onDismissError = {},
        )
    }
}
