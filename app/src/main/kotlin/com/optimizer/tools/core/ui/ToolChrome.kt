package com.optimizer.tools.core.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.optimizer.tools.R
import com.optimizer.tools.app.tv.LocalTelevisionUi
import com.optimizer.tools.app.tv.TvScreenHeader

/**
 * Cabecera comun de las pantallas de herramientas. Delega en el lenguaje visual de la app
 * para que todas compartan titulo, subtitulo y acciones.
 */
@Composable
fun ToolTopBar(
    title: String,
    onBack: () -> Unit,
    subtitle: String? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    if (LocalTelevisionUi.current) {
        // En TV se vuelve con el mando (Atrás) o cambiando de sección en la barra lateral.
        TvScreenHeader(title = title, subtitle = subtitle, actions = actions)
        return
    }
    ScreenHeader(
        title = title,
        subtitle = subtitle,
        modifier = Modifier.padding(horizontal = 8.dp),
        leading = {
            ScreenIconButton(
                icon = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = stringResource(R.string.action_back),
                onClick = onBack,
            )
        },
        actions = actions,
    )
}

/** Campo de busqueda compartido por las herramientas. */
@Composable
fun ToolSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
) {
    DesignSearchField(
        value = value,
        onValueChange = onValueChange,
        placeholder = placeholder,
        modifier = modifier,
    )
}

/** Estado vacio o de error: icono en circulo tonal, titulo y explicacion. */
@Composable
fun ToolEmptyState(
    icon: ImageVector,
    title: String,
    description: String? = null,
    action: (@Composable () -> Unit)? = null,
) {
    Column(
        Modifier.fillMaxSize().padding(horizontal = 32.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
    ) {
        Box(
            Modifier
                .size(64.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
        }
        Text(title, style = MaterialTheme.typography.titleSmall, textAlign = TextAlign.Center)
        if (description != null) {
            Text(
                description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
        if (action != null) {
            Spacer(Modifier.height(4.dp))
            action()
        }
    }
}

/** Barrido de brillo sobre una superficie neutra, para esqueletos de carga. */
@Composable
fun Modifier.shimmer(shape: Shape = RoundedCornerShape(8.dp)): Modifier {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val translate by
        transition.animateFloat(
            initialValue = -400f,
            targetValue = 1200f,
            animationSpec = infiniteRepeatable(animation = tween(1300, easing = LinearEasing), repeatMode = RepeatMode.Restart),
            label = "shimmerTranslate",
        )
    val base = MaterialTheme.colorScheme.surfaceVariant
    val highlight = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.16f)
    val brush =
        Brush.linearGradient(
            colors = listOf(base, base.copy(alpha = 0.6f), highlight, base.copy(alpha = 0.6f), base),
            start = Offset(translate - 320f, 0f),
            end = Offset(translate, 0f),
        )
    return clip(shape).background(brush)
}

/** Linea de esqueleto con la forma del contenido real. */
@Composable
fun SkeletonLine(
    widthFraction: Float,
    modifier: Modifier = Modifier,
) {
    Spacer(
        modifier
            .fillMaxWidth(widthFraction)
            .height(14.dp)
            .shimmer(RoundedCornerShape(4.dp)),
    )
}

@Composable
fun SkeletonRow() {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Spacer(Modifier.width(56.dp).height(12.dp).shimmer(RoundedCornerShape(4.dp)))
        Spacer(Modifier.width(20.dp).height(20.dp).shimmer(RoundedCornerShape(6.dp)))
        SkeletonLine(0.7f, Modifier.weight(1f))
    }
}
