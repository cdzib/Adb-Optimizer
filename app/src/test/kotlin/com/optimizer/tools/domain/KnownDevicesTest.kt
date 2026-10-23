package com.optimizer.tools.domain

import com.optimizer.tools.domain.model.KnownDevice
import com.optimizer.tools.domain.model.deduplicateKnownDevices
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class KnownDevicesTest {
    @Test fun `same physical device keeps newest address and details`() {
        val latest = KnownDevice("192.0.2.11", 41000, true, "Phone", identityKey = "device-a", isTv = false)
        val older = latest.copy(host = "192.0.2.10", port = 42000)
        assertEquals(listOf(latest), deduplicateKnownDevices(listOf(latest, older)))
    }

    @Test fun `two devices with same model remain separate`() {
        val first = KnownDevice("192.0.2.10", 41000, true, "Same model", identityKey = "a")
        val second = first.copy(host = "192.0.2.11", identityKey = "b")
        assertEquals(listOf(first, second), deduplicateKnownDevices(listOf(first, second)))
    }

    @Test fun `legacy duplicate ports are merged without merging different hosts`() {
        val first = KnownDevice("192.0.2.10", 41000, true, "Phone")
        val second = first.copy(port = 42000)
        val different = first.copy(host = "192.0.2.11")
        assertEquals(listOf(first, different), deduplicateKnownDevices(listOf(first, second, different)))
    }

    @Test fun `old stored records remain readable without invented metadata`() {
        val device = Json.decodeFromString<KnownDevice>("""{"host":"192.0.2.10","port":5555,"secure":false,"model":"Phone"}""")
        assertNull(device.isTv)
        assertNull(device.identityKey)
        assertEquals("Phone", device.displayName)
    }
}
