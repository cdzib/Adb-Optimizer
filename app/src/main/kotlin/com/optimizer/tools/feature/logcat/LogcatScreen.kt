package com.optimizer.tools.feature.logcat

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.optimizer.tools.R
import com.optimizer.tools.core.ui.SkeletonRow
import com.optimizer.tools.core.ui.ToolEmptyState
import com.optimizer.tools.core.ui.ToolSearchField
import com.optimizer.tools.core.ui.ToolTopBar
import com.optimizer.tools.core.ui.resolve

@Composable
internal fun LogcatScreen(
    state: LogcatState,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onQuery: (String) -> Unit,
    onLevel: (Char) -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        ToolTopBar(
            title = stringResource(R.string.logcat_title),
            subtitle = stringResource(R.string.logcat_subtitle, state.entries.size, state.visible.size),
            onBack = onBack,
        ) {
            IconButton(onClick = onRefresh, enabled = !state.loading, modifier = Modifier.size(48.dp)) {
                if (state.loading) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                } else {
                    Icon(Icons.Filled.Refresh, contentDescription = stringResource(R.string.logcat_refresh))
                }
            }
        }
        ToolSearchField(
            value = state.query,
            onValueChange = onQuery,
            placeholder = stringResource(R.string.logcat_search),
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        LevelFilters(state, onLevel)
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Box(Modifier.weight(1f)) {
            LogcatContent(state, onRefresh = onRefresh)
        }
    }
}

@Composable
private fun LevelFilters(
    state: LogcatState,
    onLevel: (Char) -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        LevelFilter.entries.forEach { filter ->
            val count =
                if (filter.level == 'V') {
                    state.entries.size
                } else {
                    state.entries.count { it.level == filter.level }
                }
            FilterChip(
                selected = state.level == filter.level,
                onClick = { onLevel(filter.level) },
                label = { Text(stringResource(filter.label), style = MaterialTheme.typography.labelLarge) },
                leadingIcon = {
                    Box(Modifier.size(8.dp).clip(CircleShape).background(levelTint(filter.level)))
                },
                trailingIcon = {
                    if (count > 0) {
                        Text(
                            count.toString(),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                colors =
                    FilterChipDefaults.filterChipColors(
                        selectedContainerColor = levelTint(filter.level).copy(alpha = 0.18f),
                    ),
            )
        }
    }
}

@Composable
private fun LogcatContent(
    state: LogcatState,
    onRefresh: () -> Unit,
) {
    val error = state.error
    when {
        !state.connected -> {
            ToolEmptyState(
                icon = Icons.Filled.PhoneAndroid,
                title = stringResource(R.string.logcat_no_device_title),
                description = stringResource(R.string.logcat_no_device),
            )
        }

        error != null -> {
            ToolEmptyState(
                icon = Icons.Filled.WarningAmber,
                title = stringResource(R.string.logcat_error_title),
                description = error.resolve(),
                action = {
                    OutlinedButton(onClick = onRefresh) { Text(stringResource(R.string.action_retry)) }
                },
            )
        }

        state.loading && state.entries.isEmpty() -> {
            Column(Modifier.padding(vertical = 8.dp)) {
                repeat(10) { SkeletonRow() }
            }
        }

        state.visible.isEmpty() -> {
            ToolEmptyState(
                icon = Icons.Filled.Search,
                title = stringResource(R.string.logcat_empty_title),
                description = stringResource(R.string.logcat_empty_description),
            )
        }

        else -> {
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(state.visible) { entry -> LogcatRow(entry) }
            }
        }
    }
}

@Composable
private fun LogcatRow(entry: LogcatEntry) {
    val tint = levelTint(entry.level)
    Row(
        Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surface),
    ) {
        Box(Modifier.width(3.dp).fillMaxHeight().background(tint))
        Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    entry.time.substringAfter(' ').take(12),
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.width(8.dp))
                Box(
                    Modifier
                        .size(20.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(tint.copy(alpha = 0.18f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        entry.level.toString(),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = tint,
                    )
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    entry.tag,
                    style = MaterialTheme.typography.labelMedium,
                    color = tint,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.height(2.dp))
            Text(
                entry.message,
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun levelTint(level: Char): Color =
    when (level) {
        'E', 'F' -> MaterialTheme.colorScheme.error
        'W' -> MaterialTheme.colorScheme.tertiary
        'I' -> MaterialTheme.colorScheme.primary
        'D' -> MaterialTheme.colorScheme.secondary
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

internal enum class LevelFilter(
    val level: Char,
    val label: Int,
) {
    ALL('V', R.string.logcat_level_all),
    DEBUG('D', R.string.logcat_level_debug),
    INFO('I', R.string.logcat_level_info),
    WARN('W', R.string.logcat_level_warn),
    ERROR('E', R.string.logcat_level_error),
}

private val sample =
    listOf(
        LogcatEntry("01-02 03:04:05.678", "1042", "1042", 'I', "ActivityManager", "Start proc com.example.player"),
        LogcatEntry("01-02 03:04:06.001", "1042", "1100", 'W', "PackageManager", "Package com.example.player is not debuggable"),
        LogcatEntry("01-02 03:04:07.250", "2210", "2210", 'E', "AndroidRuntime", "FATAL EXCEPTION: main"),
    )

@Preview(name = "Registro · contenido", heightDp = 720)
@Composable
private fun LogcatContentPreview() {
    LogcatScreen(
        state = LogcatState(entries = sample, visible = sample, connected = true),
        onBack = {},
        onRefresh = {},
        onQuery = {},
        onLevel = {},
    )
}

@Preview(name = "Registro · cargando", heightDp = 720)
@Composable
private fun LogcatLoadingPreview() {
    LogcatScreen(
        state = LogcatState(connected = true, loading = true),
        onBack = {},
        onRefresh = {},
        onQuery = {},
        onLevel = {},
    )
}

@Preview(name = "Registro · vacio", heightDp = 720)
@Composable
private fun LogcatEmptyPreview() {
    LogcatScreen(
        state = LogcatState(connected = true, query = "zzz"),
        onBack = {},
        onRefresh = {},
        onQuery = {},
        onLevel = {},
    )
}

@Preview(name = "Registro · sin dispositivo", heightDp = 720)
@Composable
private fun LogcatDisconnectedPreview() {
    LogcatScreen(
        state = LogcatState(connected = false),
        onBack = {},
        onRefresh = {},
        onQuery = {},
        onLevel = {},
    )
}
