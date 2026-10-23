package com.optimizer.tools.feature.connection

internal class QrPairingRetryTracker(
    private val cooldownMs: Long,
) {
    private val retryAfterByAddress = mutableMapOf<String, Long>()

    fun canAttempt(
        address: String,
        nowMs: Long,
    ): Boolean = retryAfterByAddress[address]?.let { nowMs >= it } ?: true

    fun markFailed(
        address: String,
        nowMs: Long,
    ) {
        retryAfterByAddress[address] = nowMs + cooldownMs
    }
}
