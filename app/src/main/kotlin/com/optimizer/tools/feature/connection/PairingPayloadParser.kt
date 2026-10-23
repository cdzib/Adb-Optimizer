package com.optimizer.tools.feature.connection

/**
 * Acepta `adb://host:port?code=123456`, `adb://host:port#123456` o `host:port`.
 * El QR oficial de Android (`WIFI:T:ADB;S:...;P:...;;`) no incluye host/puerto:
 * ese formato solo sirve para el flujo de generacion (ver ADR-004).
 */
internal fun parsePairingPayload(raw: String): Triple<String, Int?, String?>? {
    val text = raw.trim()
    if (text.isEmpty()) return null

    val uri = runCatching { java.net.URI(text) }.getOrNull()
    if (uri != null && uri.scheme == "adb") {
        val host = uri.host ?: return null
        val port = uri.port.takeIf { it in 1..65535 }
        val code =
            uri.query
                ?.split("&")
                ?.firstOrNull { it.startsWith("code=") }
                ?.substringAfter("code=")
                ?: uri.fragment
        return Triple(host, port, code?.takeIf { it.isNotBlank() })
    }

    if (text.matches(Regex("""^\d{1,3}(\.\d{1,3}){3}(:\d+)?$"""))) {
        val parts = text.split(":")
        return Triple(parts[0], parts.getOrNull(1)?.toIntOrNull(), null)
    }
    return null
}
