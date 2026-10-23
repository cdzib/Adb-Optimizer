package com.optimizer.tools.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DeviceDiagnosticsReaderTest {
    @Test
    fun `invalid and inaccessible frequency readings remain unknown`() {
        listOf("0", "-1000", "bad", "", "9223372036854775808").forEach { value ->
            assertNull(DeviceDiagnosticsReader { value }.readCpuFrequencies(1).single().currentMhz)
        }
        assertNull(DeviceDiagnosticsReader { throw SecurityException() }.readCpuFrequencies(1).single().currentMhz)
        assertEquals(emptyList<Any>(), DeviceDiagnosticsReader { "1000" }.readCpuFrequencies(0))
    }

    @Test
    fun `unreadable core does not discard other frequencies`() {
        val reader =
            DeviceDiagnosticsReader { path ->
                when {
                    path.contains("cpu0/") && path.endsWith("scaling_cur_freq") -> "1800500\n"
                    path.contains("cpu0/") && path.endsWith("cpuinfo_max_freq") -> "2400000"
                    else -> throw java.io.IOException("not readable")
                }
            }
        val cores = reader.readCpuFrequencies(2)
        assertEquals(1800.5f, cores[0].currentMhz)
        assertEquals(2400f, cores[0].maximumMhz)
        assertNull(cores[0].minimumMhz)
        assertNull(cores[1].currentMhz)
        assertEquals(1, cores[1].index)
    }
}
