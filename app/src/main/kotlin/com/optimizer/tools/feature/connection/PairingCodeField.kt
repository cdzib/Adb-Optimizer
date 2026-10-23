package com.optimizer.tools.feature.connection

import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.optimizer.tools.R
import com.optimizer.tools.core.ui.AdbOptimizerTheme
import com.optimizer.tools.domain.model.ThemeMode

/** A single editable field, decorated as six slots; paste and backspace stay native. */
@Composable
internal fun PairingCodeField(
    value: String,
    onValueChange: (String) -> Unit,
    enabled: Boolean = true,
) {
    val interactions = remember { MutableInteractionSource() }
    val focused by interactions.collectIsFocusedAsState()
    val label = stringResource(R.string.connection_pair_code)
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(label, style = MaterialTheme.typography.titleSmall)
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            BasicTextField(
                value = TextFieldValue(value, selection = TextRange(value.length)),
                onValueChange = { onValueChange(normalizePairingCode(it.text)) },
                enabled = enabled,
                singleLine = true,
                interactionSource = interactions,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword, imeAction = ImeAction.Done),
                modifier = Modifier.fillMaxWidth().semantics { contentDescription = label },
                decorationBox = { innerTextField ->
                    Box {
                        Row(Modifier.fillMaxWidth().clearAndSetSemantics {}, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            repeat(PAIRING_CODE_LENGTH) { index ->
                                val active = focused && index == value.length.coerceAtMost(PAIRING_CODE_LENGTH - 1)
                                Box(
                                    Modifier.weight(1f).heightIn(min = 56.dp).border(
                                        if (active) 2.dp else 1.dp,
                                        if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                                        MaterialTheme.shapes.medium,
                                    ),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        value.getOrNull(index)?.toString() ?: "–",
                                        style = MaterialTheme.typography.titleLarge,
                                        color =
                                            if (value.getOrNull(index) !=
                                                null
                                            ) {
                                                MaterialTheme.colorScheme.onSurface
                                            } else {
                                                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                            },
                                    )
                                }
                            }
                        }
                        // Keep the real editor in composition for native input and accessibility.
                        Box(Modifier.size(1.dp).alpha(0f)) { innerTextField() }
                    }
                },
            )
        }
    }
}

@Preview(name = "OTP oscuro", showBackground = true, widthDp = 360)
@Composable
private fun PairingCodeFieldDarkPreview() {
    AdbOptimizerTheme(ThemeMode.DARK) {
        Surface { Column(Modifier.padding(24.dp)) { PairingCodeField("012", {}) } }
    }
}

@Preview(name = "OTP completo texto grande", showBackground = true, widthDp = 320, fontScale = 1.3f)
@Composable
private fun PairingCodeFieldCompletePreview() {
    AdbOptimizerTheme(ThemeMode.LIGHT) {
        Surface { Column(Modifier.padding(24.dp)) { PairingCodeField("012345", {}) } }
    }
}
