package com.optimizer.tools.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import dev.adboptimizer.core.model.ManagedApp

/** Icono real en destino local; inicial sobre fondo en remoto (shell no expone iconos). */
@Composable
internal fun AppIcon(
    app: ManagedApp,
    local: Boolean,
    size: Int = 40,
) {
    val context = LocalContext.current
    val packageManager = remember(context) { context.packageManager }
    val drawable =
        remember(app.packageName, local) {
            if (!local) null else runCatching { packageManager.getApplicationIcon(app.packageName) }.getOrNull()
        }
    if (drawable != null) {
        val bitmap = remember(drawable, size) { drawable.toBitmap(size, size) }
        androidx.compose.foundation.Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = null,
            modifier = Modifier.size(size.dp).clip(RoundedCornerShape(10.dp)),
        )
    } else {
        Box(
            Modifier
                .size(size.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                app.displayName.take(1).uppercase(),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}
