package com.optimizer.tools.feature.connection

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class QrPairingRetryTrackerTest {
    @Test
    fun `failed service is skipped during cooldown but another service remains eligible`() {
        val tracker = QrPairingRetryTracker(cooldownMs = 8_000)
        tracker.markFailed("192.168.1.10:40001", nowMs = 1_000)

        assertFalse(tracker.canAttempt("192.168.1.10:40001", nowMs = 2_000))
        assertTrue(tracker.canAttempt("192.168.1.11:40002", nowMs = 2_000))
    }

    @Test
    fun `failed service becomes eligible again when cooldown expires`() {
        val tracker = QrPairingRetryTracker(cooldownMs = 8_000)
        tracker.markFailed("192.168.1.10:40001", nowMs = 1_000)

        assertTrue(tracker.canAttempt("192.168.1.10:40001", nowMs = 9_000))
    }
}
