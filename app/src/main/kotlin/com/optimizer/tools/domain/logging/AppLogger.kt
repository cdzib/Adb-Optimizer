package com.optimizer.tools.domain.logging

/**
 * Contrato mínimo de logging de la app.
 *
 * Las implementaciones de producción deben ser no-op; solo la variante debug conserva
 * los mensajes. El logger nunca debe lanzar excepciones ni alterar el flujo funcional.
 */
interface AppLogger {
    fun debug(
        tag: String,
        message: String,
        error: Throwable? = null,
    )

    fun error(
        tag: String,
        message: String,
        error: Throwable? = null,
    )
}
