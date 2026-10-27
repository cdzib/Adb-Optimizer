package dev.adboptimizer.core.optimization

import dev.adboptimizer.core.model.AppliesTo
import dev.adboptimizer.core.model.Category
import dev.adboptimizer.core.model.DeviceInfo
import dev.adboptimizer.core.model.Impact
import dev.adboptimizer.core.model.JournalEntry
import dev.adboptimizer.core.model.JournalStep
import dev.adboptimizer.core.model.Recipe
import dev.adboptimizer.core.model.RecipeStep
import dev.adboptimizer.core.model.Risk
import dev.adboptimizer.core.model.StepStatus
import dev.adboptimizer.core.model.VerifySpec
import dev.adboptimizer.core.shell.AdbShell
import dev.adboptimizer.core.shell.ShellResult
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RecipeEngineTest {
    @Test
    fun `una verificacion sin expectativa no se da por buena`() =
        runTest {
            val engine = RecipeEngine(shell(), journal(), pause = {})
            val step =
                RecipeStep(
                    run = "settings put global demo 1",
                    verify = VerifySpec("settings get global demo"),
                    timeoutMs = 5_000,
                )

            val result = engine.run(recipe(step), device).steps.single()

            assertEquals(StepStatus.FAILED, result.status)
            assertTrue(result.error.orEmpty(), result.error.orEmpty().contains("expectativa"))
        }

    @Test
    fun `relee el estado hasta que el dispositivo refleja el cambio`() =
        runTest {
            val shell = shell(reads = listOf("0", "0", "1"))
            val engine = RecipeEngine(shell, journal(), pause = {})
            val step =
                RecipeStep(
                    run = "settings put global demo 1",
                    verify = VerifySpec("settings get global demo", expected = "1"),
                    timeoutMs = 5_000,
                    undo = "settings put global demo {{prev}}",
                )

            val result = engine.run(recipe(step), device).steps.single()

            assertEquals(StepStatus.OK, result.status)
            assertEquals(3, shell.history.count { it.startsWith("settings get") })
        }

    @Test
    fun `un valor que nunca cambia falla tras agotar los intentos`() =
        runTest {
            val shell = shell(reads = listOf("0", "0", "0", "0", "0"))
            val engine = RecipeEngine(shell, journal(), retry = RetryPolicy(attempts = 2), pause = {})
            val step =
                RecipeStep(
                    run = "settings put global demo 1",
                    verify = VerifySpec("settings get global demo", expected = "1"),
                    timeoutMs = 5_000,
                )

            val result = engine.run(recipe(step), device).steps.single()

            assertEquals(StepStatus.FAILED, result.status)
            // Una lectura previa al cambio mas los dos intentos configurados.
            assertEquals(3, shell.history.count { it.startsWith("settings get") })
            assertNotNull(result.error)
        }

    @Test
    fun `la bitacora guarda el comando de verificacion para poder comprobar el undo`() =
        runTest {
            val journal = journal()
            val engine = RecipeEngine(shell(reads = listOf("1")), journal, pause = {})
            val step =
                RecipeStep(
                    run = "settings put global demo 1",
                    verify = VerifySpec("settings get global demo", expected = "1"),
                    timeoutMs = 5_000,
                    undo = "settings put global demo {{prev}}",
                )

            engine.run(recipe(step), device)

            val stored =
                journal.entries
                    .single()
                    .steps
                    .single()

            assertEquals("settings get global demo", stored.verifyRun)
        }

    @Test
    fun `el undo falla cuando el valor no vuelve al previo y no marca la entrada`() =
        runTest {
            val journal = journal(entry(previous = "1", restored = "0"))
            val engine = RecipeEngine(shell(reads = listOf("0")), journal, pause = {})

            val result = engine.undo("e1").single()

            assertEquals(StepStatus.FAILED, result.status)
            assertTrue(result.error.orEmpty().contains("no restauro"))
            assertNull(journal.revertedId)
        }

    @Test
    fun `el undo se marca revertido solo cuando el valor vuelve al previo`() =
        runTest {
            val journal = journal(entry(previous = "1", restored = "1"))
            val engine = RecipeEngine(shell(reads = listOf("1")), journal, pause = {})

            val result = engine.undo("e1").single()

            assertEquals(StepStatus.OK, result.status)
            assertEquals("e1", journal.revertedId)
        }

    @Test
    fun `el undo no se da por bueno si no se puede leer el estado`() =
        runTest {
            val shell = shell(reads = listOf("1")).apply { readFailure = IllegalStateException("shell cerrado") }
            val journal = journal(entry(previous = "1", restored = "1"))
            val engine = RecipeEngine(shell, journal, pause = {})

            val result = engine.undo("e1").single()

            assertEquals(StepStatus.FAILED, result.status)
            assertTrue(result.error.orEmpty().contains("No se pudo leer"))
            assertNull(journal.revertedId)
        }

    // --- dobles ---

    private fun recipe(step: RecipeStep) =
        Recipe(
            id = "demo",
            category = Category.PERFORMANCE,
            risk = Risk.SAFE,
            requiresRoot = false,
            impact = Impact.LOW,
            appliesTo = AppliesTo(),
            titleKey = "demo.title",
            summaryKey = "demo.summary",
            steps = listOf(step),
        )

    private fun entry(
        previous: String?,
        restored: String,
    ) = JournalEntry(
        id = "e1",
        deviceId = device.id,
        deviceModel = device.displayName,
        recipeId = "demo",
        titleKey = "demo.title",
        risk = Risk.SAFE,
        appliedAt = "2026-01-01T00:00:00Z",
        steps =
            listOf(
                JournalStep(
                    index = 0,
                    run = "settings put global demo 1",
                    undo = "settings put global demo $restored",
                    previousValue = previous,
                    reversible = true,
                    verifyRun = "settings get global demo",
                ),
            ),
    )

    private fun shell(reads: List<String> = emptyList()) = FakeShell(reads)

    private fun journal(entry: JournalEntry? = null): FakeJournal = FakeJournal(entry?.let { mutableListOf(it) } ?: mutableListOf())

    private val device =
        DeviceInfo(
            id = "192.168.1.10:5555",
            model = "Pixel",
            manufacturer = "Google",
            sdk = 34,
            androidVersion = "14",
            isTv = false,
            hasRoot = false,
            abi = "arm64-v8a",
        )

    private class FakeShell(
        private val reads: List<String>,
    ) : AdbShell {
        val history = mutableListOf<String>()
        var readFailure: Exception? = null

        override suspend fun exec(
            command: String,
            timeoutMs: Long,
        ): ShellResult {
            history += command
            if (command.startsWith("settings put") || command.startsWith("settings delete")) {
                return ShellResult(0, "", "", 1)
            }
            readFailure?.let { throw it }
            val index = history.count { it.startsWith("settings get") } - 1
            return ShellResult(0, reads.getOrElse(index) { reads.lastOrNull() ?: "null" }, "", 1)
        }
    }

    private class FakeJournal(
        val entries: MutableList<JournalEntry> = mutableListOf(),
    ) : ChangeJournalStore {
        var revertedId: String? = null

        override fun append(entry: JournalEntry): JournalEntry {
            val stored = entry.copy(id = "e${entries.size + 1}")
            entries += stored
            return stored
        }

        override fun list(deviceId: String?): List<JournalEntry> = entries.filter { deviceId == null || it.deviceId == deviceId }

        override fun get(id: String): JournalEntry? = entries.firstOrNull { it.id == id }

        override fun markReverted(
            id: String,
            timestamp: String,
        ) {
            revertedId = id
        }
    }
}
