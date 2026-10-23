package com.optimizer.tools.feature.connection

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ConnectionStepTest {
    @Test
    fun `selection goes directly to connection in two steps`() {
        assertEquals(2, ConnectionStep.entries.size)
        assertEquals(ConnectionStep.CONNECT, ConnectionStep.SELECT.next())
        assertEquals(ConnectionStep.CONNECT, ConnectionStep.CONNECT.next())
    }

    @Test
    fun `back returns to previous step and exits only from selection`() {
        assertEquals(ConnectionStep.SELECT, ConnectionStep.CONNECT.previous())
        assertNull(ConnectionStep.SELECT.previous())
    }
}
