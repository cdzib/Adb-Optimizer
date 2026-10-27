package com.optimizer.tools.optimization

import com.optimizer.tools.data.optimization.CatalogLoader
import com.optimizer.tools.data.optimization.ChangeJournal
import com.optimizer.tools.data.optimization.FileCatalogSource
import dev.adboptimizer.core.model.DeviceInfo
import dev.adboptimizer.core.model.Profile
import dev.adboptimizer.core.model.Recipe
import dev.adboptimizer.core.model.RunStatus
import dev.adboptimizer.core.model.StepStatus
import dev.adboptimizer.core.optimization.RecipeEngine
import dev.adboptimizer.core.shell.AdbShell
import dev.adboptimizer.core.shell.ShellResult
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/** Emula `settings` y comandos sin salida, igual que FakeRunner de desktop. */
class FakeAdbShell(
    initial: Map<String, String> = emptyMap(),
) : AdbShell {
    val values = mutableMapOf<String, String>().apply { putAll(initial) }
    val history = mutableListOf<String>()

    override suspend fun exec(
        command: String,
        timeoutMs: Long,
    ): ShellResult {
        history += command
        val get = Regex("""^settings get (\w+) ([\w.]+)$""").find(command)
        if (get != null) {
            val value = values["${get.groupValues[1]}:${get.groupValues[2]}"] ?: "null"
            return ShellResult(0, value, "", 1)
        }
        val put = Regex("""^settings put (\w+) ([\w.]+) (.+)$""").find(command)
        if (put != null) {
            values["${put.groupValues[1]}:${put.groupValues[2]}"] = put.groupValues[3]
            return ShellResult(0, "", "", 1)
        }
        val delete = Regex("""^settings delete (\w+) ([\w.]+)$""").find(command)
        if (delete != null) {
            values.remove("${delete.groupValues[1]}:${delete.groupValues[2]}")
            return ShellResult(0, "", "", 1)
        }
        if (command.startsWith("echo ")) return ShellResult(0, command.removePrefix("echo "), "", 1)
        return ShellResult(0, "", "", 1)
    }
}

class CatalogAndEngineTest {
    private val sharedRoot: File? =
        System.getProperty("adb.shared.dir")?.let(::File)?.takeIf(::hasCatalog)
            ?: generateSequence(File(".").absoluteFile) { it.parentFile }
                .map { File(it, "shared") }
                .firstOrNull(::hasCatalog)
            ?: listOf(File("../AdbOptimizer-Desktop/shared"), File("../../AdbOptimizer-Desktop/shared"))
                .firstOrNull(::hasCatalog)

    private val bundle by lazy {
        assumeTrue(
            "Catalogo compartido no disponible: usa -PsharedDir=... o ADB_OPTIMIZER_SHARED",
            sharedRoot != null,
        )
        CatalogLoader(FileCatalogSource(requireNotNull(sharedRoot))).load()
    }

    private fun hasCatalog(dir: File) = File(dir, "recipes/recipes.json").isFile

    private val tv =
        DeviceInfo(
            id = "192.168.1.40:5555",
            model = "Chromecast HD",
            manufacturer = "Google",
            sdk = 31,
            androidVersion = "12",
            isTv = true,
            hasRoot = false,
            abi = "arm64-v8a",
        )

    private fun recipe(id: String): Recipe = bundle.catalog.recipes.first { it.id == id }

    private fun engine(shell: AdbShell): RecipeEngine {
        val journalFile = File.createTempFile("journal", ".json").apply { delete() }
        return RecipeEngine(shell, ChangeJournal(journalFile), now = { 1_700_000_000_000 })
    }

    @Test
    fun `el catalogo compartido incluye las acciones de appops e hibernacion`() {
        val actions = bundle.actions.actions.associateBy { it.id }

        val appOps = actions["restrict-background-appops"]
        assertNotNull(appOps)
        assertTrue(appOps!!.run.contains("appops set"))
        assertTrue(appOps.verify.run.contains("appops get"))
        assertNotNull(appOps.undo)

        val hibernate = actions["hibernate-app"]
        assertNotNull(hibernate)
        assertTrue(hibernate!!.run.contains("am set-inactive"))
        assertNotNull(hibernate.undo)
    }

    @Test
    fun `el undo de compilar restaura el estado posterior a la instalacion`() {
        val compile = bundle.actions.actions.first { it.id == "compile-app" }

        assertEquals("cmd package compile --reset {{pkg}}", compile.undo)
    }

    @Test
    fun `el catalogo compartido permite root solo para WanboOS`() {
        assertTrue(bundle.catalog.recipes.isNotEmpty())
        assertTrue(bundle.actions.actions.isNotEmpty())
        assertEquals(
            listOf("wanboos-launcher"),
            bundle.catalog.recipes
                .filter { it.requiresRoot }
                .map { it.id },
        )
        assertEquals(1, bundle.catalog.schemaVersion)
    }

    @Test
    fun `el preview no muta el dispositivo`() =
        runTest {
            val shell =
                FakeAdbShell(
                    mapOf(
                        "global:window_animation_scale" to "1",
                        "global:transition_animation_scale" to "1",
                        "global:animator_duration_scale" to "1",
                    ),
                )
            val result = engine(shell).run(recipe("animations-off"), tv, dryRun = true)

            assertEquals(RunStatus.OK, result.status)
            assertTrue(result.steps.all { it.status == StepStatus.SKIPPED })
            assertEquals("1", shell.values["global:window_animation_scale"])
        }

    @Test
    fun `aplica y deshace restaurando el valor previo`() =
        runTest {
            val shell =
                FakeAdbShell(
                    mapOf(
                        "global:window_animation_scale" to "1",
                        "global:transition_animation_scale" to "1",
                        "global:animator_duration_scale" to "1",
                    ),
                )
            val journalFile = File.createTempFile("journal", ".json").apply { delete() }
            val journal = ChangeJournal(journalFile)
            val engine = RecipeEngine(shell, journal, now = { 1_700_000_000_000 })

            val result = engine.run(recipe("animations-off"), tv)
            assertEquals(RunStatus.OK, result.status)
            assertEquals("0", shell.values["global:window_animation_scale"])

            val entry = journal.list().first()
            assertEquals("settings put global window_animation_scale 1", entry.steps.first().undo)

            val undo = engine.undo(entry.id)
            assertTrue(undo.all { it.status == StepStatus.OK })
            assertEquals("1", shell.values["global:window_animation_scale"])
            assertNotNull(journal.get(entry.id)?.revertedAt)
        }

    @Test
    fun `usa undoIfUnset cuando el ajuste no existia`() =
        runTest {
            val shell = FakeAdbShell()
            val journalFile = File.createTempFile("journal", ".json").apply { delete() }
            val journal = ChangeJournal(journalFile)
            val engine = RecipeEngine(shell, journal)

            engine.run(recipe("animations-off"), tv)
            val entry = journal.list().first()
            assertEquals(null, entry.steps.first().previousValue)
            assertEquals("settings delete global window_animation_scale", entry.steps.first().undo)

            engine.undo(entry.id)
            assertFalse(shell.values.containsKey("global:window_animation_scale"))
        }

    @Test
    fun `runAction aplica una accion por paquete y la registra`() =
        runTest {
            val shell = FakeAdbShell()
            val journalFile = File.createTempFile("journal", ".json").apply { delete() }
            val journal = ChangeJournal(journalFile)
            val engine = RecipeEngine(shell, journal)
            val action = bundle.actions.actions.first { it.id == "kill-app" }

            val result = engine.runAction(action, tv, "com.example.video")

            assertEquals(StepStatus.OK, result.status)
            assertTrue(shell.history.contains("am force-stop com.example.video"))
            assertEquals("kill-app:com.example.video", journal.list().first().recipeId)
            assertTrue(
                journal
                    .list()
                    .first()
                    .steps
                    .first()
                    .reversible
                    .not(),
            )
        }

    @Test
    fun `no marca como revertida una entrada con pasos irreversibles`() =
        runTest {
            val shell = FakeAdbShell()
            val journalFile = File.createTempFile("journal", ".json").apply { delete() }
            val journal = ChangeJournal(journalFile)
            val engine = RecipeEngine(shell, journal)
            val action = bundle.actions.actions.first { it.id == "kill-app" }

            engine.runAction(action, tv, "com.example.video")
            val entry = journal.list().first()
            val undo = engine.undo(entry.id)

            assertEquals(StepStatus.SKIPPED, undo.single().status)
            assertEquals(null, journal.get(entry.id)?.revertedAt)
            assertTrue(journalFile.exists())
            assertEquals(entry.id, ChangeJournal(journalFile).get(entry.id)?.id)
        }

    @Test
    fun `runAction respeta la blocklist del catalogo`() =
        runTest {
            val shell = FakeAdbShell()
            val engine = engine(shell)
            val action = bundle.actions.actions.first { it.id == "disable-app" }

            val result = engine.runAction(action, tv, "com.android.systemui")

            assertEquals(StepStatus.FAILED, result.status)
            assertTrue(shell.history.isEmpty())
        }

    @Test
    fun `bloquea paquetes criticos tambien en acciones avanzadas no listadas`() =
        runTest {
            val shell = FakeAdbShell()
            val engine = engine(shell)
            val action = bundle.actions.actions.first { it.id == "compile-app" }

            val result = engine.runAction(action, tv, "com.android.systemui")

            assertEquals(StepStatus.FAILED, result.status)
            assertTrue(shell.history.isEmpty())
        }

    @Test
    fun `bloquea nombres de paquete invalidos antes de invocar shell`() =
        runTest {
            val shell = FakeAdbShell()
            val engine = engine(shell)
            val action = bundle.actions.actions.first { it.id == "compile-app" }

            val result = engine.runAction(action, tv, "com.example;settings put global x 1")

            assertEquals(StepStatus.FAILED, result.status)
            assertTrue(shell.history.isEmpty())
        }

    @Test
    fun `el plan filtra por perfil`() {
        val conservative = engine(FakeAdbShell()).plan(bundle.catalog.recipes, tv, Profile.CONSERVATIVE)
        val aggressive = engine(FakeAdbShell()).plan(bundle.catalog.recipes, tv, Profile.AGGRESSIVE)

        assertTrue(conservative.first { it.recipe.id == "animations-off" }.supported)
        assertFalse(conservative.first { it.recipe.id == "force-doze" }.supported)
        assertTrue(aggressive.first { it.recipe.id == "force-doze" }.supported)
    }

    @Test
    fun `las recetas de TV no aplican a un celular`() {
        val phone = tv.copy(isTv = false)
        val plan = engine(FakeAdbShell()).plan(bundle.catalog.recipes, phone, Profile.AGGRESSIVE)
        assertFalse(plan.first { it.recipe.id == "screensaver-off" }.supported)
    }
}
