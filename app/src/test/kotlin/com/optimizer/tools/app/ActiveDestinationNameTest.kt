package com.optimizer.tools.app

import dev.adboptimizer.core.model.AppTarget
import dev.adboptimizer.core.model.DeviceInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ActiveDestinationNameTest {
    private val phone = DeviceInfo("local", "Galaxy S23", "Samsung", 36, "16", false, false, "arm64-v8a")
    private val remote = phone.copy(id = "192.0.2.10:41000", model = "Galaxy A35")

    @Test fun `local header uses actual device name instead of generic phone label`() {
        assertEquals(phone.displayName, resolveActiveDestinationName(AppTarget.LOCAL, AppTarget.LOCAL, phone, remote, "Fallback"))
    }

    @Test fun `remote header follows selected connection not stale dashboard`() {
        assertEquals(remote.displayName, resolveActiveDestinationName(AppTarget.REMOTE, AppTarget.LOCAL, phone, remote, "Fallback"))
    }

    @Test fun `changing to local never displays cached remote name`() {
        assertEquals("Fallback", resolveActiveDestinationName(AppTarget.LOCAL, AppTarget.REMOTE, remote, remote, "Fallback"))
        assertNull(resolveActiveDestinationName(AppTarget.REMOTE, AppTarget.LOCAL, phone, null, "Fallback"))
    }
}
