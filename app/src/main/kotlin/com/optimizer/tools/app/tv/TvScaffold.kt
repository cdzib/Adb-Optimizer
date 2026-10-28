@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)

package com.optimizer.tools.app.tv

import android.app.UiModeManager
import android.content.Context
import android.content.res.Configuration
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.optimizer.tools.R
import com.optimizer.tools.core.ui.AdbOptimizerTheme
import kotlinx.coroutines.delay
import java.time.LocalTime
import java.time.format.DateTimeFormatter

val LocalTelevisionUi = staticCompositionLocalOf { false }

/** Host form factor, unrelated to the connected ADB target. */
fun isTelevisionHost(context: Context): Boolean =
    (context.getSystemService(Context.UI_MODE_SERVICE) as? UiModeManager)?.currentModeType ==
        Configuration.UI_MODE_TYPE_TELEVISION

data class TvNavigationItem(
    val key: String,
    val label: String,
    val icon: ImageVector,
    val onClick: () -> Unit,
)

/** The caller supplies the real graph: back stack and capability gates remain unchanged. */
@Composable
fun TvScaffold(
    items: List<TvNavigationItem>,
    selectedKey: String?,
    statusText: String?,
    snackbarHost: @Composable () -> Unit,
    content: @Composable (PaddingValues) -> Unit,
) {
    TvTheme {
        Scaffold(snackbarHost = snackbarHost, containerColor = MaterialTheme.colorScheme.background) { insets ->
            Column(Modifier.fillMaxSize().padding(insets).padding(horizontal = 34.dp, vertical = 22.dp)) {
                TvTopBar(statusText)
                Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                    TvNavigationRail(items, selectedKey, Modifier.width(180.dp))
                    Box(Modifier.weight(1f)) { content(PaddingValues()) }
                }
                TvHintBar()
            }
        }
    }
}

@Composable
private fun TvTopBar(statusText: String?) {
    Row(
        Modifier.fillMaxWidth().padding(bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        TvBrandMark()
        Text(
            stringResource(R.string.app_name),
            style = MaterialTheme.typography.labelLarge.copy(fontSize = 14.sp, fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurface,
        )
        if (statusText != null) {
            Box(
                Modifier
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f))
                    .padding(horizontal = 10.dp, vertical = 4.dp),
            ) {
                Text(
                    statusText,
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp, fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                )
            }
        }
        Box(Modifier.weight(1f))
        TvClock()
    }
}

@Composable
private fun TvClock() {
    val time by
        produceState(initialValue = LocalTime.now()) {
            while (true) {
                value = LocalTime.now()
                delay(30_000)
            }
        }
    Text(
        time.format(DateTimeFormatter.ofPattern("HH:mm")),
        style = MaterialTheme.typography.labelLarge.copy(fontSize = 13.sp),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** Native dp/sp sizing: Android TV already applies density to the 10-foot viewport. */
@Composable
fun TvTheme(content: @Composable () -> Unit) {
    val typography = MaterialTheme.typography
    MaterialTheme(
        colorScheme = MaterialTheme.colorScheme,
        shapes = MaterialTheme.shapes,
        typography =
            typography.copy(
                titleLarge = typography.titleLarge.copy(fontSize = 24.sp, lineHeight = 32.sp),
                titleMedium = typography.titleMedium.copy(fontSize = 18.sp, lineHeight = 24.sp),
                bodyLarge = typography.bodyLarge.copy(fontSize = 18.sp, lineHeight = 26.sp),
                bodyMedium = typography.bodyMedium.copy(fontSize = 16.sp, lineHeight = 24.sp),
                bodySmall = typography.bodySmall.copy(fontSize = 14.sp, lineHeight = 20.sp),
                labelLarge = typography.labelLarge.copy(fontSize = 16.sp, lineHeight = 22.sp),
            ),
        content = content,
    )
}

@Composable
private fun TvNavigationRail(
    items: List<TvNavigationItem>,
    selectedKey: String?,
    modifier: Modifier = Modifier,
) {
    val keys = items.map { it.key }
    val requesters = remember(keys) { keys.associateWith { FocusRequester() } }
    var lastFocusedKey by rememberSaveable { mutableStateOf<String?>(null) }
    val entryKey = lastFocusedKey?.takeIf { it in keys } ?: selectedKey?.takeIf { it in keys } ?: keys.firstOrNull()
    Column(
        modifier
            .focusProperties { enter = { requesters[entryKey] ?: FocusRequester.Default } }
            .focusGroup()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        items.forEach { item ->
            var focused by remember(item.key) { mutableStateOf(false) }
            Surface(
                onClick = item.onClick,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .focusRequester(requesters.getValue(item.key))
                        .onFocusChanged {
                            focused = it.isFocused
                            if (it.isFocused) lastFocusedKey = item.key
                        }.semantics { selected = item.key == selectedKey },
                shape = RoundedCornerShape(12.dp),
                color =
                    when {
                        focused -> MaterialTheme.colorScheme.surfaceContainerHigh
                        item.key == selectedKey -> MaterialTheme.colorScheme.surfaceContainer
                        else -> MaterialTheme.colorScheme.background
                    },
                border = if (focused) BorderStroke(2.dp, MaterialTheme.colorScheme.onSurface) else null,
            ) {
                Row(
                    Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Icon(
                        item.icon,
                        contentDescription = null,
                        tint =
                            if (item.key == selectedKey || focused) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        modifier = Modifier.size(18.dp),
                    )
                    Text(
                        item.label,
                        style = MaterialTheme.typography.titleMedium.copy(fontSize = 15.sp),
                        color =
                            if (item.key == selectedKey) {
                                MaterialTheme.colorScheme.onSurface
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                    )
                }
            }
        }
    }
    // Re-entry restores the last rail item; selection changes must not steal content focus.
    LaunchedEffect(Unit) { requesters[entryKey]?.requestFocus() }
}

@Preview(widthDp = 960, heightDp = 540, showBackground = true)
@Composable
private fun TvScaffoldPreview() {
    AdbOptimizerTheme {
        TvScaffold(
            items = listOf(TvNavigationItem("home", stringResource(R.string.nav_home), Icons.Filled.Home) {}),
            selectedKey = "home",
            statusText = "Conectado · local",
            snackbarHost = {},
        ) {
            Text(stringResource(R.string.tv_navigation_hint))
        }
    }
}
