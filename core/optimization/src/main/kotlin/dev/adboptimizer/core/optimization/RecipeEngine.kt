package dev.adboptimizer.core.optimization

import dev.adboptimizer.core.model.ActionSpec
import dev.adboptimizer.core.model.AppSafety
import dev.adboptimizer.core.model.Compatibility
import dev.adboptimizer.core.model.DeviceInfo
import dev.adboptimizer.core.model.JournalEntry
import dev.adboptimizer.core.model.JournalStep
import dev.adboptimizer.core.model.ParamKind
import dev.adboptimizer.core.model.PlanItem
import dev.adboptimizer.core.model.Profile
import dev.adboptimizer.core.model.Recipe
import dev.adboptimizer.core.model.RecipeRun
import dev.adboptimizer.core.model.RecipeStep
import dev.adboptimizer.core.model.RunStatus
import dev.adboptimizer.core.model.StepResult
import dev.adboptimizer.core.model.StepStatus
import dev.adboptimizer.core.model.VerifySpec
import dev.adboptimizer.core.shell.AdbShell
import dev.adboptimizer.core.shell.ShellResult
import java.time.Instant

class RecipeEngine(
    private val shell: AdbShell,
    private val journal: ChangeJournalStore,
    private val now: () -> Long = System::currentTimeMillis,
) {
    fun plan(
        recipes: List<Recipe>,
        device: DeviceInfo,
        profile: Profile,
    ): List<PlanItem> {
        val maxRisk = Compatibility.maxRisk(profile)
        return recipes.map { recipe ->
            val incompatible = Compatibility.check(recipe, device)
            when {
                incompatible != null -> PlanItem(recipe, supported = false, reason = incompatible)
                recipe.risk.ordinal > maxRisk.ordinal -> PlanItem(recipe, supported = false, reason = "profile")
                else -> PlanItem(recipe, supported = true)
            }
        }
    }

    suspend fun run(
        recipe: Recipe,
        device: DeviceInfo,
        dryRun: Boolean = false,
        onStep: (StepResult) -> Unit = {},
    ): RecipeRun {
        val startedAt = timestamp()
        val results = mutableListOf<StepResult>()
        val journalSteps = mutableListOf<JournalStep>()

        recipe.steps.forEachIndexed { index, step ->
            val result = executeStep(index, step, dryRun)
            results += result
            onStep(result)

            if (!dryRun && result.status == StepStatus.OK) {
                val undo = resolveUndo(step, result.previousValue)
                journalSteps +=
                    JournalStep(
                        index = index,
                        run = step.run,
                        undo = undo,
                        undoIfUnset = step.undoIfUnset,
                        previousValue = result.previousValue,
                        output = result.output,
                        reversible = undo != null,
                    )
            }
        }

        if (!dryRun && journalSteps.isNotEmpty()) {
            journal.append(
                JournalEntry(
                    id = "",
                    deviceId = device.id,
                    deviceModel = device.displayName,
                    recipeId = recipe.id,
                    titleKey = recipe.titleKey,
                    risk = recipe.risk,
                    appliedAt = startedAt,
                    steps = journalSteps,
                ),
            )
        }

        return RecipeRun(
            recipeId = recipe.id,
            status = summarize(results, dryRun),
            steps = results,
            startedAt = startedAt,
            finishedAt = timestamp(),
            dryRun = dryRun,
        )
    }

    /**
     * Acciones parametrizadas por paquete (cerrar app, compilar AOT, restringir,
     * suspender, deshabilitar). Respeta la blocklist del catalogo.
     */
    suspend fun runAction(
        action: ActionSpec,
        device: DeviceInfo,
        pkg: String,
    ): StepResult {
        val expandedRun = action.run.replace("{{pkg}}", pkg)
        val minSdk = action.minSdk
        if (minSdk != null && device.sdk < minSdk) {
            return StepResult(0, expandedRun, StepStatus.UNSUPPORTED, error = "Requiere Android $minSdk o posterior.")
        }
        if (action.paramKind == ParamKind.PACKAGE && !PACKAGE_NAME.matches(pkg)) {
            return StepResult(0, expandedRun, StepStatus.FAILED, error = "Nombre de paquete inválido.")
        }
        if (action.paramKind == ParamKind.PACKAGE && AppSafety.isCritical(pkg)) {
            return StepResult(0, expandedRun, StepStatus.FAILED, error = "Paquete crítico protegido.")
        }
        if (action.paramKind == ParamKind.PACKAGE && action.blocklist?.contains(pkg) == true) {
            return StepResult(0, expandedRun, StepStatus.FAILED, error = "Paquete protegido por el catálogo.")
        }

        val step =
            RecipeStep(
                run = action.run.replace("{{pkg}}", pkg),
                verify =
                    action.verify.copy(
                        run = action.verify.run.replace("{{pkg}}", pkg),
                        contains = action.verify.contains?.replace("{{pkg}}", pkg),
                    ),
                undo = action.undo?.replace("{{pkg}}", pkg),
                timeoutMs = action.timeoutMs,
            )

        val result = executeStep(0, step, dryRun = false)

        if (result.status == StepStatus.OK) {
            journal.append(
                JournalEntry(
                    id = "",
                    deviceId = device.id,
                    deviceModel = device.displayName,
                    recipeId = "${action.id}:$pkg",
                    titleKey = action.titleKey,
                    risk = action.risk,
                    appliedAt = timestamp(),
                    steps =
                        listOf(
                            JournalStep(
                                index = 0,
                                run = step.run,
                                undo = step.undo,
                                previousValue = result.previousValue,
                                output = result.output,
                                reversible = step.undo != null,
                            ),
                        ),
                ),
            )
        }

        return result
    }

    suspend fun undo(entryId: String): List<StepResult> {
        val entry = journal.get(entryId) ?: error("Entrada de bitacora no encontrada: $entryId")
        val results = mutableListOf<StepResult>()

        entry.steps.reversed().forEach { step ->
            val undo = step.undo
            if (!step.reversible || undo == null) {
                results +=
                    StepResult(
                        index = step.index,
                        run = step.run,
                        status = StepStatus.SKIPPED,
                        error = "Cambio no reversible",
                    )
                return@forEach
            }

            val startedAt = System.nanoTime()
            results +=
                try {
                    val response = shell.exec(undo, 20_000)
                    StepResult(
                        index = step.index,
                        run = undo,
                        status = StepStatus.OK,
                        output = response.output,
                        durationMs = (System.nanoTime() - startedAt) / 1_000_000,
                    )
                } catch (error: Exception) {
                    StepResult(
                        index = step.index,
                        run = undo,
                        status = StepStatus.FAILED,
                        error = error.message ?: "Fallo al revertir",
                        durationMs = (System.nanoTime() - startedAt) / 1_000_000,
                    )
                }
        }

        // Do not claim the whole entry was reverted when any step was skipped
        // (for example, an irreversible command). A partial undo must remain visible.
        if (results.isNotEmpty() && results.all { it.status == StepStatus.OK }) {
            journal.markReverted(entryId, timestamp())
        }
        return results
    }

    private suspend fun executeStep(
        index: Int,
        step: RecipeStep,
        dryRun: Boolean,
    ): StepResult {
        val startedAt = System.nanoTime()
        val previous = readValue(step.verify.run)

        if (dryRun) {
            return StepResult(
                index = index,
                run = step.run,
                status = StepStatus.SKIPPED,
                output = previous.orEmpty(),
                previousValue = previous,
                durationMs = (System.nanoTime() - startedAt) / 1_000_000,
            )
        }

        return try {
            val response = shell.exec(step.run, step.timeoutMs)
            val verification = verify(step.verify)
            StepResult(
                index = index,
                run = step.run,
                status = if (verification.ok) StepStatus.OK else StepStatus.FAILED,
                output = response.output,
                error = verification.detail.takeUnless { verification.ok },
                previousValue = previous,
                durationMs = (System.nanoTime() - startedAt) / 1_000_000,
            )
        } catch (error: Exception) {
            StepResult(
                index = index,
                run = step.run,
                status = StepStatus.FAILED,
                error = error.message ?: "Fallo desconocido",
                previousValue = previous,
                durationMs = (System.nanoTime() - startedAt) / 1_000_000,
            )
        }
    }

    private suspend fun readValue(command: String): String? =
        runCatching {
            val value = shell.exec(command, 10_000).stdout.trim()
            // `settings get` devuelve literal "null" cuando el ajuste no existe.
            value.ifBlank { null }?.takeUnless { it == "null" }
        }.getOrNull()

    private suspend fun verify(spec: VerifySpec): Verification {
        val result: ShellResult =
            runCatching { shell.exec(spec.run, 10_000) }
                .getOrElse { return Verification(false, it.message ?: "Fallo de verificacion") }

        val output = result.stdout.trim()
        val expected = spec.expected
        val contains = spec.contains
        val regex = spec.matches
        val expectedExit = spec.expectEmptyOrExitCode
        return when {
            expectedExit != null -> {
                Verification(
                    ok = output.isEmpty() || result.exitCode == expectedExit,
                    detail = "se esperaba salida vacia, se obtuvo: $output",
                )
            }

            expected != null -> {
                Verification(
                    ok = output == expected,
                    detail = "se esperaba \"$expected\" y se obtuvo \"$output\"",
                )
            }

            contains != null -> {
                Verification(
                    ok = output.contains(contains),
                    detail = "se esperaba contener \"$contains\" y se obtuvo \"$output\"",
                )
            }

            regex != null -> {
                Verification(
                    ok = Regex(regex).containsMatchIn(output),
                    detail = "se esperaba coincidir con /$regex/ y se obtuvo \"$output\"",
                )
            }

            else -> {
                Verification(true, "")
            }
        }
    }

    private fun summarize(
        results: List<StepResult>,
        dryRun: Boolean,
    ): RunStatus {
        if (dryRun) return RunStatus.OK
        val executed = results.filter { it.status != StepStatus.SKIPPED }
        if (executed.isEmpty()) return RunStatus.UNSUPPORTED
        if (executed.all { it.status == StepStatus.OK }) return RunStatus.OK
        if (executed.any { it.status == StepStatus.OK }) return RunStatus.PARTIAL
        return RunStatus.FAILED
    }

    private fun timestamp(): String = Instant.ofEpochMilli(now()).toString()

    private data class Verification(
        val ok: Boolean,
        val detail: String,
    )

    companion object {
        private val PACKAGE_NAME = Regex("[A-Za-z0-9_]+(?:\\.[A-Za-z0-9_]+)+")

        fun resolveUndo(
            step: RecipeStep,
            previousValue: String?,
        ): String? {
            val template = step.undo ?: return null
            if (!template.contains("{{prev}}")) return template
            if (previousValue == null || previousValue == "null") return step.undoIfUnset
            return template.replace("{{prev}}", previousValue)
        }
    }
}
