package com.optimizer.tools.feature.connection

internal const val PAIRING_CODE_LENGTH = 6

internal fun normalizePairingCode(value: String): String = value.filter { it in '0'..'9' }.take(PAIRING_CODE_LENGTH)
