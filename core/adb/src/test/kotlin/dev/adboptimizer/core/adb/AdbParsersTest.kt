package dev.adboptimizer.core.adb

import dev.adboptimizer.core.model.BatteryChargeState
import dev.adboptimizer.core.model.BatteryHealth
import dev.adboptimizer.core.model.BatteryPowerSource
import dev.adboptimizer.core.shell.AdbShell
import dev.adboptimizer.core.shell.ShellResult
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class AdbParsersTest {
    @Test
    fun `acepta df montado en data user 0`() {
        val raw =
            """
            Filesystem       1K-blocks      Used Available Use% Mounted on
            /dev/block/dm-63 482768860 361064740 121573048  75% /data/user/0
            """.trimIndent()

        val parsed = AdbParsers.parseStorage(raw)

        assertEquals(482_768_860L, parsed.totalKb)
        assertEquals(361_064_740L, parsed.usedKb)
        assertEquals(121_573_048L, parsed.availableKb)
        assertEquals(75f, parsed.usedPercent, 0.001f)
    }

    @Test
    fun `usa AP como temperatura cuando no existe CPU`() {
        val thermal =
            """
            Thermal Status: 0
            Cached temperatures:
            ${'\t'}Temperature{mValue=0.0, mType=2, mName=SUBBAT, mStatus=0}
            ${'\t'}Temperature{mValue=37.0, mType=0, mName=AP, mStatus=0}
            ${'\t'}Temperature{mValue=32.6, mType=2, mName=BAT, mStatus=0}
            """.trimIndent()

        val metrics =
            AdbParsers.buildMetrics(
                meminfo = "",
                cpuinfo = "",
                storage = "",
                thermal = thermal,
                uptime = "",
                processes = "",
            )

        assertEquals(37.0f, metrics.temperatureC ?: -1f, 0.001f)
    }

    @Test
    fun `conserva cpu si el fabricante lo publica`() {
        val thermal =
            """
            Thermal Status: 3
            Cached temperatures:
            ${'\t'}Temperature{mValue=41.5, mType=0, mName=CPU, mStatus=0}
            ${'\t'}Temperature{mValue=50.0, mType=0, mName=AP, mStatus=0}
            """.trimIndent()

        val metrics = AdbParsers.buildMetrics("", "", "", thermal, "", "")

        assertEquals(41.5f, metrics.temperatureC ?: -1f, 0.001f)
        assertEquals(3, metrics.maxThermalStatus)
        assertTrue(metrics.healthScore() >= 0)
    }

    @Test
    fun `extrae hardware GLES Vulkan y USB sin inventar valores`() {
        val props =
            """
            [ro.product.brand]: [Samsung]
            [ro.product.name]: [dm3qxxx]
            [ro.product.board]: [kalama]
            [ro.hardware]: [qcom]
            [ro.build.version.security_patch]: [2026-09-01]
            [ro.build.display.id]: [BP4A.260901.001]
            [ro.product.cpu.abilist]: [arm64-v8a,armeabi-v7a]
            [ro.soc.model]: [SM8550]
            [ro.soc.manufacturer]: [QTI]
            [ro.opengles.version]: [196610]
            [gsm.version.baseband]: [G998BXXU]
            """.trimIndent()
        val features =
            """
            feature:android.hardware.usb.host
            feature:android.hardware.vulkan.version=4206592
            """.trimIndent()

        val info = AdbParsers.parseHardwareInfo(props, features, "6.1.0\n")

        assertEquals("Samsung", info.brand)
        assertEquals("dm3qxxx", info.product)
        assertEquals("3.2", info.openGlEsVersion)
        assertEquals("1.3.0", info.vulkanVersion)
        assertEquals(listOf("arm64-v8a", "armeabi-v7a"), info.supportedAbis)
        assertEquals("6.1.0", info.kernelVersion)
        assertEquals(true, info.supportsUsbHost)
    }

    @Test
    fun `prioriza el override de pantalla y conserva el tamano fisico`() {
        val raw =
            """
            Physical size: 1440x3088
            Override size: 1080x2316
            Physical density: 600
            Override density: 480
            DisplayDeviceInfo{"Pantalla", 1440 x 3088, renderFrameRate 60.000004}
            """.trimIndent()

        val display = AdbParsers.parseDisplayInfo(raw)

        assertEquals(1440, display?.physicalWidthPx)
        assertEquals(3088, display?.physicalHeightPx)
        assertEquals(600, display?.physicalDensityDpi)
        assertEquals(1080, display?.activeWidthPx)
        assertEquals(2316, display?.activeHeightPx)
        assertEquals(480, display?.activeDensityDpi)
        assertEquals(60.000004f, display?.refreshRateHz ?: -1f, 0.001f)
    }

    @Test
    fun `parsea el inventario de sensores sin muestrear`() {
        val raw =
            """
            Sensor List:
            0x0000000b) lsm6dso LSM6DSO Accelerometer Non-wakeup | STMicro         | ver: 15933 | type: android.sensor.accelerometer(1) | perm: n/a
            ${'\t'}continuous | minRate=1.00Hz
            0x000000ac) smd  Wakeup                | Samsung         | ver: 1 | type: android.sensor.significant_motion(17) | perm: n/a
            """.trimIndent()

        val sensors = AdbParsers.parseSensors(raw)

        assertEquals(2, sensors.size)
        assertEquals("lsm6dso LSM6DSO Accelerometer Non-wakeup", sensors[0].name)
        assertEquals("STMicro", sensors[0].vendor)
        assertEquals("android.sensor.accelerometer", sensors[0].typeName)
        assertEquals(1, sensors[0].typeCode)
        assertEquals("smd Wakeup", sensors[1].name)
    }

    @Test
    fun `parsea interfaces activas y descarta loopback y scope host`() {
        val raw =
            """
            1: lo    inet 127.0.0.1/8 scope host lo\       valid_lft forever
            45: wlan0    inet 192.168.200.8/23 brd 192.168.201.255 scope global dynamic wlan0\       valid_lft 63246sec
            45: wlan0    inet6 fe80::cc3a:52ff:fecc:e221/64 scope link \       valid_lft forever
            30: rmnet_data0    inet 10.93.196.18/30 brd 10.93.196.19 scope global rmnet_data0\       valid_lft forever
            """.trimIndent()

        val interfaces = AdbParsers.parseNetworkInterfaces(raw)

        assertEquals(listOf("wlan0", "rmnet_data0"), interfaces.map { it.name })
        assertEquals(listOf("192.168.200.8/23", "fe80::cc3a:52ff:fecc:e221/64"), interfaces[0].addresses)
        assertEquals(listOf("10.93.196.18/30"), interfaces[1].addresses)
    }

    @Test
    fun `parsea bateria remota y no confunde temperatura con la del sistema`() {
        val raw =
            """
            Current Battery Service state:
              AC powered: false
              USB powered: true
              Wireless powered: false
              Dock powered: false
              status: 2
              health: 2
              level: 32
              scale: 100
              voltage: 3825
              temperature: 300
              technology: Li-ion
              Charge counter: 1487640
              current now: 10
            """.trimIndent()

        val battery = requireNotNull(AdbParsers.parseBattery(raw))

        assertEquals(32, battery.percent)
        assertEquals(BatteryChargeState.CHARGING, battery.diagnostics.chargeState)
        assertEquals(BatteryHealth.GOOD, battery.diagnostics.health)
        assertEquals(BatteryPowerSource.USB, battery.diagnostics.powerSource)
        assertEquals(3.825f, battery.diagnostics.voltageVolts ?: -1f, 0.001f)
        assertEquals(30.0f, battery.diagnostics.temperatureC ?: -1f, 0.001f)
        assertEquals(1487.64f, battery.diagnostics.remainingChargeMah ?: -1f, 0.01f)
        assertNull(battery.diagnostics.currentMilliamps)
    }

    @Test
    fun `parsea frecuencias por nucleo y descarta lecturas vacias`() {
        val raw =
            """
            0|1800000|300000|2802000
            1|||
            2|2400000|300000|2802000
            """.trimIndent()

        val frequencies = AdbParsers.parseCpuFrequencies(raw)

        assertEquals(3, frequencies.size)
        assertEquals(1800f, frequencies[0].currentMhz ?: -1f, 0.001f)
        assertNull(frequencies[1].currentMhz)
        assertEquals(2400f, frequencies[2].currentMhz ?: -1f, 0.001f)
    }

    @Test
    fun `lecturas opcionales fallidas no impiden devolver el dispositivo`() =
        runTest {
            val shell =
                object : AdbShell {
                    override suspend fun exec(
                        command: String,
                        timeoutMs: Long,
                    ): ShellResult =
                        when (command) {
                            "getprop" -> ShellResult(0, "[ro.product.model]: [Remote]\n[ro.build.version.sdk]: [35]\n", "", 1)
                            "which su || echo no-su" -> ShellResult(0, "no-su", "", 1)
                            "pm list features" -> ShellResult(0, "feature:android.hardware.usb.host\n", "", 1)
                            "uname -r" -> ShellResult(0, "6.1.0\n", "", 1)
                            else -> throw IOException("permission denied")
                        }
                }

            val info = DeviceRepository(shell).readInfo("192.0.2.10:5555")

            assertEquals("Remote", info.model)
            assertEquals("6.1.0", info.hardwareInfo?.kernelVersion)
            assertNull(info.displayInfo)
            assertTrue(info.sensors.isEmpty())
            assertTrue(info.networkInterfaces.isEmpty())
        }
}
