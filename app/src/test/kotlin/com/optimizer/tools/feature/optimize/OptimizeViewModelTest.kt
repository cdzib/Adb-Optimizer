package com.optimizer.tools.feature.optimize

import com.optimizer.tools.R
import com.optimizer.tools.core.ui.asUiText
import com.optimizer.tools.core.ui.uiText
import com.optimizer.tools.domain.model.ShizukuLocalStatus
import dev.adboptimizer.core.model.AppTarget
import dev.adboptimizer.core.model.Category
import dev.adboptimizer.core.model.DeviceInfo
import dev.adboptimizer.core.model.Impact
import dev.adboptimizer.core.model.PlanItem
import dev.adboptimizer.core.model.Profile
import dev.adboptimizer.core.model.Recipe
import dev.adboptimizer.core.model.RecipeRun
import dev.adboptimizer.core.model.Risk
import dev.adboptimizer.core.model.RunStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class OptimizeViewModelTest {
    @Test
    fun `local target builds plan and preselects safe items`() =
        runViewModelTest {
            val engine =
                FakeOptimizeEngine(
                    planProvider = { _, _ ->
                        listOf(
                            planItem("safe"),
                            planItem("moderate", Risk.MODERATE),
                            planItem("unsupported", Risk.SAFE, supported = false),
                        )
                    },
                )
            val viewModel = OptimizeViewModel(FakeOptimizeEnvironment(localEngineFake = engine))

            advanceUntilIdle()

            assertEquals(AppTarget.LOCAL, viewModel.state.value.target)
            assertEquals(phone, viewModel.state.value.deviceInfo)
            assertEquals(Profile.RECOMMENDED, engine.plans.single().second)
            assertEquals(
                listOf("safe", "moderate", "unsupported"),
                viewModel.state.value.plan
                    .map { it.recipe.id },
            )
            assertEquals(setOf("safe"), viewModel.state.value.selected)
        }

    @Test
    fun `remote target is rejected without session`() =
        runViewModelTest {
            val viewModel = OptimizeViewModel(FakeOptimizeEnvironment())
            advanceUntilIdle()
            val events = collectEvents(viewModel)

            viewModel.setTarget(AppTarget.REMOTE)
            advanceUntilIdle()

            assertEquals(AppTarget.LOCAL, viewModel.state.value.target)
            assertEquals(
                listOf(OptimizeEvent.Fail("error.notConnected", uiText(R.string.optimize_error_remote_no_device))),
                events,
            )
        }

    @Test
    fun `remote target uses remote device and engine`() =
        runViewModelTest {
            val local = FakeOptimizeEngine(planProvider = { _, _ -> listOf(planItem("local")) })
            val remote = FakeOptimizeEngine(planProvider = { _, _ -> listOf(planItem("remote")) })
            val environment =
                FakeOptimizeEnvironment(
                    localEngineFake = local,
                    remoteEngineFake = remote,
                    remoteSession = true,
                    remoteAvailable = MutableStateFlow(true),
                )
            val viewModel = OptimizeViewModel(environment)
            advanceUntilIdle()

            viewModel.setTarget(AppTarget.REMOTE)
            advanceUntilIdle()

            assertEquals(AppTarget.REMOTE, viewModel.state.value.target)
            assertEquals(remoteDevice, viewModel.state.value.deviceInfo)
            assertEquals(
                "remote",
                viewModel.state.value.plan
                    .single()
                    .recipe.id,
            )
            assertEquals(remoteDevice, remote.plans.single().first)
        }

    @Test
    fun `changing profile rebuilds the plan`() =
        runViewModelTest {
            val engine =
                FakeOptimizeEngine(
                    planProvider = { _, profile ->
                        if (profile == Profile.AGGRESSIVE) {
                            listOf(planItem("advanced", Risk.ADVANCED))
                        } else {
                            listOf(planItem("safe"))
                        }
                    },
                )
            val viewModel = OptimizeViewModel(FakeOptimizeEnvironment(localEngineFake = engine))
            advanceUntilIdle()

            viewModel.setProfile(Profile.AGGRESSIVE)
            advanceUntilIdle()

            assertEquals(Profile.AGGRESSIVE, engine.plans.last().second)
            assertEquals(
                "advanced",
                viewModel.state.value.plan
                    .single()
                    .recipe.id,
            )
            assertEquals(emptySet<String>(), viewModel.state.value.selected)
        }

    @Test
    fun `toggle removes and restores selection`() =
        runViewModelTest {
            val engine = FakeOptimizeEngine(planProvider = { _, _ -> listOf(planItem("safe")) })
            val viewModel = OptimizeViewModel(FakeOptimizeEnvironment(localEngineFake = engine))
            advanceUntilIdle()

            assertEquals(setOf("safe"), viewModel.state.value.selected)

            viewModel.toggle("safe")
            assertEquals(emptySet<String>(), viewModel.state.value.selected)

            viewModel.toggle("safe")
            assertEquals(setOf("safe"), viewModel.state.value.selected)
        }

    @Test
    fun `optimize runs only selected supported recipes and emits done`() =
        runViewModelTest {
            val engine =
                FakeOptimizeEngine(
                    planProvider = { _, _ ->
                        listOf(planItem("safe"), planItem("moderate", Risk.MODERATE))
                    },
                )
            val viewModel = OptimizeViewModel(FakeOptimizeEnvironment(localEngineFake = engine))
            advanceUntilIdle()
            val events = collectEvents(viewModel)

            viewModel.optimize()
            advanceUntilIdle()

            assertEquals(1, engine.runs.size)
            assertEquals(
                "safe",
                engine.runs
                    .single()
                    .first.id,
            )
            assertEquals(listOf(runResult("safe")), viewModel.state.value.results)
            assertEquals(listOf(OptimizeEvent.Done("optimize.done")), events)
        }

    @Test
    fun `optimize fails when nothing is selected`() =
        runViewModelTest {
            val engine = FakeOptimizeEngine(planProvider = { _, _ -> listOf(planItem("safe")) })
            val viewModel = OptimizeViewModel(FakeOptimizeEnvironment(localEngineFake = engine))
            advanceUntilIdle()
            val events = collectEvents(viewModel)

            viewModel.toggle("safe")
            viewModel.optimize()
            advanceUntilIdle()

            assertEquals(listOf(OptimizeEvent.Fail("optimize.nothingSelected", null)), events)
            assertTrue(engine.runs.isEmpty())
        }

    @Test
    fun `local optimize is rejected when shizuku is not ready`() =
        runViewModelTest {
            val environment = FakeOptimizeEnvironment(localStatus = ShizukuLocalStatus.PERMISSION_REQUIRED)
            val viewModel = OptimizeViewModel(environment)
            advanceUntilIdle()
            val events = collectEvents(viewModel)

            viewModel.optimize()
            advanceUntilIdle()

            assertEquals(
                listOf(
                    OptimizeEvent.Fail(
                        "error.unknown",
                        uiText(R.string.optimize_error_shizuku),
                    ),
                ),
                events,
            )
            assertTrue(environment.localEngineFake.runs.isEmpty())
        }

    @Test
    fun `run failure clears running and emits error`() =
        runViewModelTest {
            val engine =
                FakeOptimizeEngine(
                    planProvider = { _, _ -> listOf(planItem("safe")) },
                    runProvider = { _, _ -> error("boom") },
                )
            val viewModel = OptimizeViewModel(FakeOptimizeEnvironment(localEngineFake = engine))
            advanceUntilIdle()
            val events = collectEvents(viewModel)

            viewModel.optimize()
            advanceUntilIdle()

            assertFalse(viewModel.state.value.running)
            assertEquals(listOf(OptimizeEvent.Fail("error.unknown", "boom".asUiText())), events)
        }

    @Test
    fun `remote availability updates state`() =
        runViewModelTest {
            val environment = FakeOptimizeEnvironment(remoteAvailable = MutableStateFlow(false))
            val viewModel = OptimizeViewModel(environment)
            advanceUntilIdle()

            assertFalse(viewModel.state.value.remoteAvailable)

            environment.remoteAvailable.value = true
            advanceUntilIdle()

            assertTrue(viewModel.state.value.remoteAvailable)
        }

    private fun TestScope.collectEvents(viewModel: OptimizeViewModel): List<OptimizeEvent> {
        val events = mutableListOf<OptimizeEvent>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.events.toList(events)
        }
        return events
    }

    private fun runViewModelTest(block: suspend TestScope.() -> Unit) =
        runTest {
            Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
            try {
                block()
            } finally {
                Dispatchers.resetMain()
            }
        }
}

private class FakeOptimizeEnvironment(
    val localEngineFake: FakeOptimizeEngine = FakeOptimizeEngine(),
    val remoteEngineFake: FakeOptimizeEngine? = null,
    var localDeviceInfo: DeviceInfo? = phone,
    var remoteDeviceInfo: DeviceInfo? = remoteDevice,
    var localStatus: ShizukuLocalStatus = ShizukuLocalStatus.READY,
    var remoteSession: Boolean = false,
    override val remoteAvailable: MutableStateFlow<Boolean> = MutableStateFlow(false),
) : OptimizeEnvironment {
    override fun hasRemoteSession(): Boolean = remoteSession

    override fun remoteDevice(): DeviceInfo? = remoteDeviceInfo

    override fun remoteEngine(): OptimizeEngine? = remoteEngineFake

    override fun localEngine(): OptimizeEngine = localEngineFake

    override suspend fun localDevice(): DeviceInfo? = localDeviceInfo

    override fun localShizukuStatus(): ShizukuLocalStatus = localStatus
}

private class FakeOptimizeEngine(
    private val planProvider: (DeviceInfo, Profile) -> List<PlanItem> = { _, _ -> emptyList() },
    private val runProvider: suspend (Recipe, DeviceInfo) -> RecipeRun =
        { recipe, _ -> runResult(recipe.id) },
) : OptimizeEngine {
    val plans = mutableListOf<Pair<DeviceInfo, Profile>>()
    val runs = mutableListOf<Pair<Recipe, DeviceInfo>>()

    override fun plan(
        device: DeviceInfo,
        profile: Profile,
    ): List<PlanItem> {
        plans += device to profile
        return planProvider(device, profile)
    }

    override suspend fun run(
        recipe: Recipe,
        device: DeviceInfo,
    ): RecipeRun {
        runs += recipe to device
        return runProvider(recipe, device)
    }
}

private val phone =
    DeviceInfo(
        id = "local:Pixel",
        model = "Pixel",
        manufacturer = "Google",
        sdk = 35,
        androidVersion = "15",
        isTv = false,
        hasRoot = false,
        abi = "arm64-v8a",
    )

private val remoteDevice = phone.copy(id = "192.0.2.10:5555", model = "Remote")

private fun planItem(
    id: String,
    risk: Risk = Risk.SAFE,
    supported: Boolean = true,
    reason: String? = null,
): PlanItem = PlanItem(recipe(id, risk), supported, reason)

private fun recipe(
    id: String,
    risk: Risk,
): Recipe =
    Recipe(
        id = id,
        category = Category.PERFORMANCE,
        risk = risk,
        requiresRoot = false,
        impact = Impact.LOW,
        titleKey = "recipe.$id.title",
        summaryKey = "recipe.$id.summary",
        steps = emptyList(),
    )

private fun runResult(id: String): RecipeRun =
    RecipeRun(
        recipeId = id,
        status = RunStatus.OK,
        steps = emptyList(),
        startedAt = "2026-01-01T00:00:00Z",
        finishedAt = "2026-01-01T00:00:01Z",
        dryRun = false,
    )
