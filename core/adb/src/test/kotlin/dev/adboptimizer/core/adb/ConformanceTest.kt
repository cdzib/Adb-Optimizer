package dev.adboptimizer.core.adb

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/**
 * Misma suite que desktop/tests/parsers.test.ts.
 * Si los dos parsers divergen, uno de los dos CI falla.
 */
class ConformanceTest {
    private val casesDir: File? =
        System.getProperty("adb.shared.dir")?.let { File(it, "conformance/cases") }
            ?: generateSequence(File(".").absoluteFile) { it.parentFile }
                .map { File(it, "shared/conformance/cases") }
                .firstOrNull { it.isDirectory }
            ?: listOf(
                File("../../../AdbOptimizer-Desktop/shared/conformance/cases"),
                File("../../../../AdbOptimizer-Desktop/shared/conformance/cases"),
            ).firstOrNull { it.isDirectory }

    @Test
    fun `hay casos de conformidad compartidos`() {
        assumeTrue("fixtures de conformidad no disponibles: usa -PsharedDir=...", casesDir != null)
        val files = requireNotNull(casesDir).listFiles { file -> file.extension == "json" }.orEmpty()
        assertTrue("sin fixtures en $casesDir", files.size >= 4)
    }

    @Test
    fun `todos los casos parsean igual que lo esperado`() {
        assumeTrue("fixtures de conformidad no disponibles: usa -PsharedDir=...", casesDir != null)
        val files = requireNotNull(casesDir).listFiles { file -> file.extension == "json" }.orEmpty()
        files.forEach { file ->
            val root = Json.parseToJsonElement(file.readText()).jsonObject
            val command = root["command"]!!.jsonPrimitive.content
            val stdout = root["stdout"]!!.jsonPrimitive.content
            val expected = root["expected"]!!.jsonObject

            when {
                command.startsWith("dumpsys meminfo") -> {
                    val parsed = AdbParsers.parseMeminfo(stdout)
                    assertEquals(file.name, expected.long("totalKb"), parsed.totalKb)
                    assertEquals(file.name, expected.long("freeKb"), parsed.freeKb)
                    assertEquals(file.name, expected.long("usedKb"), parsed.usedKb)
                    assertEquals(file.name, expected.long("cachedKb"), parsed.cachedKb)
                }

                command.startsWith("dumpsys cpuinfo") -> {
                    val parsed = AdbParsers.parseCpuinfo(stdout)
                    assertEquals(file.name, expected.double("load1"), parsed.load1.toDouble(), 0.001)
                    assertEquals(file.name, expected.double("load5"), parsed.load5.toDouble(), 0.001)
                    assertEquals(file.name, expected.double("load15"), parsed.load15.toDouble(), 0.001)
                    assertEquals(file.name, expected.double("busyPercent"), parsed.busyPercent.toDouble(), 0.001)
                }

                command.startsWith("df ") -> {
                    val parsed = AdbParsers.parseStorage(stdout)
                    assertEquals(file.name, expected.long("totalKb"), parsed.totalKb)
                    assertEquals(file.name, expected.long("usedKb"), parsed.usedKb)
                    assertEquals(file.name, expected.long("availableKb"), parsed.availableKb)
                    assertEquals(file.name, expected.double("usedPercent"), parsed.usedPercent.toDouble(), 0.001)
                }

                command.startsWith("dumpsys thermalservice") -> {
                    val parsed = AdbParsers.parseThermal(stdout)
                    assertEquals(file.name, expected.long("maxStatus").toInt(), parsed.maxStatus)
                    val temperatures = expected["temperatures"]!!.jsonArray
                    assertTrue(file.name, parsed.temperatures.isNotEmpty())
                    assertEquals(file.name, temperatures.size, parsed.temperatures.size)
                }

                else -> {
                    error("Caso sin parser: $command")
                }
            }
        }
    }

    private fun JsonObject.long(key: String): Long = this[key]!!.jsonPrimitive.content.toLong()

    private fun JsonObject.double(key: String): Double = this[key]!!.jsonPrimitive.content.toDouble()
}
