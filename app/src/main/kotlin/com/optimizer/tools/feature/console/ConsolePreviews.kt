package com.optimizer.tools.feature.console

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.optimizer.tools.core.ui.AdbOptimizerTheme
import com.optimizer.tools.domain.model.ThemeMode

@Preview(showBackground = true, name = "Consola")
@Composable
private fun ConsolePreview() {
    AdbOptimizerTheme(themeMode = ThemeMode.DARK) {
        ConsoleScreen(
            state =
                ConsoleState(
                    command = "getprop ro.product.model",
                    history = listOf("dumpsys battery", "pm list packages -3"),
                    entries =
                        listOf(
                            ConsoleEntry(
                                command = "getprop ro.product.model",
                                stdout = "Pixel 8 Pro\n",
                                stderr = "",
                                exitCode = 0,
                                durationMs = 42,
                            ),
                        ),
                ),
            onBack = {},
            onCommand = {},
            onRun = {},
            onUseHistory = {},
            onClear = {},
            onDismissMessage = {},
            onDismissError = {},
        )
    }
}
