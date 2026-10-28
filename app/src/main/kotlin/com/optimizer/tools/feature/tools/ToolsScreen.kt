@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.optimizer.tools.feature.tools

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.BatterySaver
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Cable
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.optimizer.tools.R
import com.optimizer.tools.app.tv.LocalTelevisionUi
import com.optimizer.tools.app.tv.TvScreenHeader
import com.optimizer.tools.app.tv.TvSectionLabel
import com.optimizer.tools.app.tv.TvTile
import com.optimizer.tools.core.ui.AdbOptimizerTheme
import com.optimizer.tools.core.ui.DesignGroup
import com.optimizer.tools.core.ui.DesignRow
import com.optimizer.tools.core.ui.DesignSearchField
import com.optimizer.tools.core.ui.DesignSectionLabel
import com.optimizer.tools.core.ui.DesignTone
import com.optimizer.tools.core.ui.ScreenHeader
import com.optimizer.tools.core.ui.ScreenIconButton
import com.optimizer.tools.core.ui.color

private data class ToolEntry(
    val group: ToolsGroupId,
    val title: Int,
    val description: Int,
    val icon: ImageVector,
    val tone: DesignTone,
    val onClick: () -> Unit,
)

private enum class ToolsGroupId { Diagnostics, System, Apps }

/** Stateless entry point shared by mobile and TV hosts; actions stay with the host. */
@Composable
fun ToolsScreen(
    onBack: () -> Unit,
    onOpenLogcat: () -> Unit,
    onOpenConsole: () -> Unit,
    onOpenSettingsDb: () -> Unit,
    onOpenDoze: () -> Unit,
    onOpenProfiles: () -> Unit,
    onOpenDebloat: () -> Unit,
    onOpenFiles: () -> Unit,
    onOpenLocalAdb: () -> Unit,
    onOpenDpc: (() -> Unit)? = null,
    targetLabel: String? = null,
) {
    val entries =
        buildList {
            add(
                ToolEntry(
                    ToolsGroupId.Diagnostics,
                    R.string.settings_open_logcat,
                    R.string.tools_logcat_description,
                    Icons.Default.BugReport,
                    DesignTone.Green,
                    onOpenLogcat,
                ),
            )
            add(
                ToolEntry(
                    ToolsGroupId.Diagnostics,
                    R.string.settings_open_console,
                    R.string.tools_console_description,
                    Icons.Default.Terminal,
                    DesignTone.Cyan,
                    onOpenConsole,
                ),
            )
            add(
                ToolEntry(
                    ToolsGroupId.Diagnostics,
                    R.string.settings_open_doze,
                    R.string.tools_doze_description,
                    Icons.Default.BatterySaver,
                    DesignTone.Amber,
                    onOpenDoze,
                ),
            )
            add(
                ToolEntry(
                    ToolsGroupId.System,
                    R.string.localadb_title,
                    R.string.localadb_tools_description,
                    Icons.Filled.Cable,
                    DesignTone.Green,
                    onOpenLocalAdb,
                ),
            )
            add(
                ToolEntry(
                    ToolsGroupId.System,
                    R.string.settings_open_settings_db,
                    R.string.tools_settings_description,
                    Icons.Default.Tune,
                    DesignTone.Green,
                    onOpenSettingsDb,
                ),
            )
            add(
                ToolEntry(
                    ToolsGroupId.System,
                    R.string.settings_open_files,
                    R.string.tools_files_description,
                    Icons.Default.Folder,
                    DesignTone.Cyan,
                    onOpenFiles,
                ),
            )
            add(
                ToolEntry(
                    ToolsGroupId.System,
                    R.string.settings_open_debloat,
                    R.string.tools_debloat_description,
                    Icons.Default.Apps,
                    DesignTone.Red,
                    onOpenDebloat,
                ),
            )
            add(
                ToolEntry(
                    ToolsGroupId.Apps,
                    R.string.settings_open_profiles,
                    R.string.tools_profiles_description,
                    Icons.Default.Tune,
                    DesignTone.Amber,
                    onOpenProfiles,
                ),
            )
            if (onOpenDpc != null) {
                add(
                    ToolEntry(
                        ToolsGroupId.System,
                        R.string.settings_open_dpc,
                        R.string.settings_dpc_description,
                        Icons.Default.Security,
                        DesignTone.Neutral,
                        onOpenDpc,
                    ),
                )
            }
        }

    if (LocalTelevisionUi.current) {
        TelevisionToolsContent(onBack = onBack, entries = entries, targetLabel = targetLabel)
        return
    }

    var query by remember { mutableStateOf("") }
    // map es inline: permite resolver recursos por entrada antes de filtrar.
    val labelled = entries.map { entry -> entry to (stringResource(entry.title) to stringResource(entry.description)) }
    val visible = labelled.filter { (_, text) -> text.matchesQuery(query) }
    Column(Modifier.fillMaxSize()) {
        Column(Modifier.padding(horizontal = 16.dp)) {
            ScreenHeader(
                title = stringResource(R.string.tools_title),
                subtitle = stringResource(R.string.tools_subtitle, entries.size, targetLabel ?: stringResource(R.string.tools_this_device)),
                leading = {
                    ScreenIconButton(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        stringResource(R.string.tools_back),
                        onBack,
                    )
                },
            )
            DesignSearchField(
                value = query,
                onValueChange = { query = it },
                placeholder = stringResource(R.string.tools_search),
            )
            Spacer(Modifier.height(4.dp))
        }
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp),
        ) {
            ToolsGroupId.entries.forEach { group ->
                val groupEntries = visible.filter { (entry, _) -> entry.group == group }
                if (groupEntries.isEmpty()) return@forEach
                DesignSectionLabel(stringResource(group.label()))
                DesignGroup {
                    groupEntries.forEachIndexed { index, (entry, text) ->
                        DesignRow(
                            icon = entry.icon,
                            title = text.first,
                            subtitle = text.second,
                            tone = entry.tone,
                            isFirst = index == 0,
                            onClick = entry.onClick,
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
            }
            if (visible.isEmpty()) {
                DesignSectionLabel(stringResource(R.string.tools_title))
                Text(
                    stringResource(R.string.tools_search_empty),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
            }
        }
    }
}

private fun ToolsGroupId.label(): Int =
    when (this) {
        ToolsGroupId.Diagnostics -> R.string.tools_diagnostics
        ToolsGroupId.System -> R.string.tools_system
        ToolsGroupId.Apps -> R.string.tools_apps
    }

private fun Pair<String, String>.matchesQuery(query: String): Boolean {
    if (query.isBlank()) return true
    val needle = query.trim().lowercase()
    return first.lowercase().contains(needle) || second.lowercase().contains(needle)
}

@Composable
private fun TelevisionToolsContent(
    onBack: () -> Unit,
    entries: List<ToolEntry>,
    targetLabel: String?,
) {
    Column(Modifier.fillMaxSize()) {
        TvScreenHeader(
            title = stringResource(R.string.tools_title),
            subtitle = stringResource(R.string.tools_subtitle, entries.size, targetLabel ?: stringResource(R.string.tools_this_device)),
        )
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(bottom = 12.dp),
            modifier = Modifier.weight(1f),
        ) {
            ToolsGroupId.entries.forEach { group ->
                val groupEntries = entries.filter { it.group == group }
                if (groupEntries.isEmpty()) return@forEach
                item(key = group.name, span = { GridItemSpan(3) }) {
                    TvSectionLabel(stringResource(group.label()))
                }
                items(groupEntries, key = { it.title }) { tool ->
                    TvTile(
                        icon = tool.icon,
                        title = stringResource(tool.title),
                        subtitle = stringResource(tool.description),
                        onClick = tool.onClick,
                    )
                }
            }
        }
    }
}

@Preview(showBackground = true, heightDp = 800)
@Composable
private fun ToolsScreenPreview() {
    AdbOptimizerTheme { ToolsScreen({}, {}, {}, {}, {}, {}, {}, {}, {}) }
}

@Preview(widthDp = 608, heightDp = 400, showBackground = true)
@Composable
private fun ToolsScreenTelevisionPreview() {
    AdbOptimizerTheme {
        CompositionLocalProvider(LocalTelevisionUi provides true) {
            ToolsScreen({}, {}, {}, {}, {}, {}, {}, {}, {})
        }
    }
}
