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
import dev.adboptimizer.core.model.RecipeStepType
import dev.adboptimizer.core.model.RunStatus
import dev.adboptimizer.core.model.StepResult
import dev.adboptimizer.core.model.StepStatus
import dev.adboptimizer.core.model.VerifySpec
import dev.adboptimizer.core.shell.AdbCommandRules
import dev.adboptimizer.core.shell.AdbShell
import dev.adboptimizer.core.shell.ShellResult
import kotlinx.coroutines.delay
import java.time.Instant

/**
 * Cuantas veces se vuelve a leer el estado antes de dar un paso por fallido.
 *
 * `settings put`, `wm size` y `resolve-activity` no siempre reflejan el cambio en la misma
 * lectura; sin reintento un dispositivo lento convierte un cambio correcto en un fallo.
 */
data class RetryPolicy(
    val attempts: Int = 3,
    val delayMs: Long = 200,
) {
    init {
        require(attempts >= 1) { "attempts debe ser al menos 1" }
        require(delayMs >= 0) { "delayMs no puede ser negativo" }
    }
}

class RecipeEngine(
    private val shell: AdbShell,
    private val journal: ChangeJournalStore,
    private val now: () -> Long = System::currentTimeMillis,
    private val retry: RetryPolicy = RetryPolicy(),
    private val pause: suspend (Long) -> Unit = { delay(it) },
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
                        verifyRun = step.verify.run,
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
        if (action.paramKind == ParamKind.PACKAGE && !AdbCommandRules.isPackageName(pkg)) {
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
                                verifyRun = step.verify.run,
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
                    val restoreProblem = restoreProblem(step)
                    StepResult(
                        index = step.index,
                        run = undo,
                        status = if (restoreProblem == null) StepStatus.OK else StepStatus.FAILED,
                        output = response.output,
                        error = restoreProblem,
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
        if (step.type == RecipeStepType.INSTALL_SYSTEM_APK) {
            return StepResult(
                index = index,
                run = step.run,
                status = StepStatus.UNSUPPORTED,
                error =
                    "La instalación en /system requiere adb root/remount desde un host. " +
                        "dadb ejecuta como uid 2000; Android solo permite pm install y elegir Home.",
            )
        }
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
            val verification = verifyWithRetry(step.verify)
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

    private suspend fun readValue(command: String): String? = readState(command).getOrNull()

    /** Lee el estado y no confunde un fallo de lectura con "sin valor". */
    private suspend fun readState(command: String): Result<String?> =
        runCatching {
            val value = shell.exec(command, 10_000).stdout.trim()
            // `settings get` devuelve literal "null" cuando el ajuste no existe.
            value.ifBlank { null }?.takeUnless { it == "null" }
        }

    /**
     * Comprueba que el undo dejo el estado en el valor previo. Devuelve el motivo cuando no
     * coincide, o `null` cuando no hay nada que comprobar o si la restauracion fue correcta.
     */
    private suspend fun restoreProblem(step: JournalStep): String? {
        val verifyRun = step.verifyRun ?: return null
        val state = readState(verifyRun)
        val failure = state.exceptionOrNull()
        if (failure != null) return "No se pudo leer el estado al revertir: ${failure.message ?: "fallo de lectura"}"

        val restored = state.getOrNull()
        if (restored == step.previousValue) return null
        return "El undo no restauro el valor: se esperaba \"${step.previousValue ?: "sin valor"}\" y quedo \"${restored ?: "sin valor"}\""
    }

    /**
     * Verifica y, si el dispositivo todavia no refleja el cambio, vuelve a leer hasta agotar
     * [retry]. Un valor que nunca cambia termina en fallo, nunca en un falso OK.
     */
    private suspend fun verifyWithRetry(spec: VerifySpec): Verification {
        var verification = verify(spec)
        var attempt = 1
        while (!verification.ok && attempt < retry.attempts) {
            pause(retry.delayMs)
            attempt++
            verification = verify(spec)
        }
        return verification
    }

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
                Verification(false, "la verificacion no declara expectativa (equals, contains, matches o expectEmptyOrExitCode)")
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
