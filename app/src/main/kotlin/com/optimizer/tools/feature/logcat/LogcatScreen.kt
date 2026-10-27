package com.optimizer.tools.feature.logcat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import com.optimizer.tools.core.ui.uiText

@Composable
internal fun LogcatScreen(
    state: LogcatState,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onQuery: (String) -> Unit,
    onLevel: (Char) -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.logcat_back))
            }
            Text(
                stringResource(R.string.logcat_title),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onRefresh, enabled = !state.loading) {
                Icon(Icons.Filled.Refresh, contentDescription = stringResource(R.string.logcat_refresh))
            }
        }
        HorizontalDivider()
        OutlinedTextField(
            value = state.query,
            onValueChange = onQuery,
            label = { Text(stringResource(R.string.logcat_search)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        )
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            LevelFilter.entries.forEach { filter ->
                FilterChip(
                    selected = state.level == filter.level,
                    onClick = { onLevel(filter.level) },
                    label = { Text(stringResource(filter.label)) },
                )
            }
        }
        Spacer(Modifier.padding(4.dp))
        if (state.loading) {
            LinearProgressIndicator(Modifier.fillMaxWidth())
        }
        LogcatBody(state)
    }
}

@Composable
private fun LogcatBody(state: LogcatState) {
    val error = state.error
    when {
        !state.connected -> {
            LogcatMessage(stringResource(R.string.logcat_no_device))
        }

        error != null -> {
            LogcatMessage(error.resolve(androidx.compose.ui.platform.LocalContext.current))
        }

        state.visible.isEmpty() && !state.loading -> {
            LogcatMessage(stringResource(R.string.logcat_empty))
        }

        else -> {
            val listState = rememberLazyListState()
            LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                items(state.visible) { entry ->
                    Text(
                        text = entry.line,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        color = levelColor(entry.level),
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 2.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun levelColor(level: Char) =
    when (level) {
        'E', 'F' -> MaterialTheme.colorScheme.error
        'W' -> MaterialTheme.colorScheme.tertiary
        'I' -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

@Composable
private fun LogcatMessage(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(24.dp),
    )
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
        LogcatEntry('I', "ActivityManager", "1042", "Start proc com.example.player"),
        LogcatEntry('W', "PackageManager", "1042", "Package com.example.player is not debuggable"),
        LogcatEntry('E', "AndroidRuntime", "2210", "FATAL EXCEPTION: main"),
    )

@Preview(name = "Logcat con entradas")
@Composable
private fun LogcatScreenPreview() {
    LogcatScreen(
        state = LogcatState(entries = sample, visible = sample, connected = true),
        onBack = {},
        onRefresh = {},
        onQuery = {},
        onLevel = {},
    )
}

@Preview(name = "Logcat vacio")
@Composable
private fun LogcatEmptyPreview() {
    LogcatScreen(
        state = LogcatState(connected = true, error = uiText(R.string.logcat_empty)),
        onBack = {},
        onRefresh = {},
        onQuery = {},
        onLevel = {},
    )
}

@Preview(name = "Logcat sin dispositivo")
@Composable
private fun LogcatDisconnectedPreview() {
    LogcatScreen(
        state = LogcatState(connected = false, error = UiText.Resource(R.string.logcat_no_device)),
        onBack = {},
        onRefresh = {},
        onQuery = {},
        onLevel = {},
    )
}
