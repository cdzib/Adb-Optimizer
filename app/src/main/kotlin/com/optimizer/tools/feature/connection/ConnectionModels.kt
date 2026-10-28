package com.optimizer.tools.feature.connection

enum class ConnectMode { NETWORK, CODE, QR, MANUAL }

enum class QrStatus { WAITING, PAIRING, CONNECTING, CONNECTED, TIMEOUT, ERROR }

internal data class ConnectAttempt(
    val host: String,
    val port: Int,
    val secure: Boolean,
)
