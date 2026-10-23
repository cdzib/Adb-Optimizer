package com.optimizer.tools.core.ui

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource

/**
 * Texto de presentación que puede originarse en un ViewModel sin acoplarlo a `Context`.
 *
 * Los textos estáticos se guardan como referencia a un recurso Android; los mensajes
 * dinámicos (por ejemplo, el detalle de una excepción) se conservan como [Plain].
 */
sealed interface UiText {
    data class Resource(
        @param:StringRes val id: Int,
        val args: List<Any> = emptyList(),
    ) : UiText

    data class Plain(
        val value: String,
    ) : UiText
}

fun uiText(
    @StringRes id: Int,
    vararg args: Any,
): UiText = UiText.Resource(id, args.toList())

fun String.asUiText(): UiText = UiText.Plain(this)

@Composable
fun UiText.resolve(): String =
    when (this) {
        is UiText.Resource -> stringResource(id, *args.toTypedArray())
        is UiText.Plain -> value
    }

fun UiText.resolve(context: Context): String =
    when (this) {
        is UiText.Resource -> context.getString(id, *args.toTypedArray())
        is UiText.Plain -> value
    }
