package com.optimizer.tools.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DeviceDiagnosticsParserTest {
    @Test
    fun `battery conversions preserve signed current and fractional temperature`() {
        assertEquals(-125.5f, DeviceDiagnosticsParser.parseBatteryCurrent(-125500))
        assertEquals(32.7f, DeviceDiagnosticsParser.parseBatteryTemperature(327))
        assertEquals(4.125f, DeviceDiagnosticsParser.parseBatteryVoltage(4125))
        assertEquals(75, DeviceDiagnosticsParser.parseBatteryPercent(150, 200))
        assertEquals(50, DeviceDiagnosticsParser.parseBatteryPercent(Int.MAX_VALUE / 2 + 1, Int.MAX_VALUE))
        assertNull(DeviceDiagnosticsParser.parseBatteryCurrent(Int.MIN_VALUE))
        assertNull(DeviceDiagnosticsParser.parseBatteryVoltage(-1))
        assertNull(DeviceDiagnosticsParser.parseBatteryTemperature(Int.MIN_VALUE))
        assertEquals(-1, DeviceDiagnosticsParser.parseBatteryPercent(50, 0))
        assertEquals(-1, DeviceDiagnosticsParser.parseBatteryPercent(101, 100))
        assertEquals(-1, DeviceDiagnosticsParser.parseBatteryPercent(-1, 100))
    }

    @Test
    fun `vulkan patch retains all twelve bits`() {
        assertEquals("1.3.274", DeviceDiagnosticsParser.parseVulkanVersion((1 shl 22) or (3 shl 12) or 274))
        assertNull(DeviceDiagnosticsParser.parseVulkanVersion(0))
    }
}
