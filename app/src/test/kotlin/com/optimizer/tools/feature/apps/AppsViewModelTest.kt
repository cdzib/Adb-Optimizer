package com.optimizer.tools.feature.apps

import com.optimizer.tools.R
import com.optimizer.tools.core.ui.uiText
import com.optimizer.tools.domain.model.AppSettings
import com.optimizer.tools.testutil.runViewModelTest
import dev.adboptimizer.core.model.ActionSpec
import dev.adboptimizer.core.model.AppAction
import dev.adboptimizer.core.model.AppActionResult
import dev.adboptimizer.core.model.AppFilter
import dev.adboptimizer.core.model.AppTarget
import dev.adboptimizer.core.model.DeviceInfo
import dev.adboptimizer.core.model.ManagedApp
import dev.adboptimizer.core.model.ParamKind
import dev.adboptimizer.core.model.Risk
import dev.adboptimizer.core.model.StepResult
import dev.adboptimizer.core.model.StepStatus
import dev.adboptimizer.core.model.VerifySpec
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runCurrent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AppsViewModelTest {
    @Test
    fun `sin privilegios la lista queda en solo lectura pero sigue mostrando apps`() =
        runViewModelTest {
            val environment = FakeAppsEnvironment(canModify = false, apps = listOf(ManagedApp("com.example")))
            val viewModel = AppsViewModel(environment)

            viewModel.onEnter(AppTarget.LOCAL)
            runCurrent()

            assertEquals(false, viewModel.state.value.privileged)
            assertEquals(1, viewModel.state.value.apps.size)
        }

    @Test
    fun `con privilegios la lista permite cambios`() =
        runViewModelTest {
            val environment = FakeAppsEnvironment(canModify = true, apps = listOf(ManagedApp("com.example")))
            val viewModel = AppsViewModel(environment)

            viewModel.onEnter(AppTarget.LOCAL)
            runCurrent()

            assertEquals(true, viewModel.state.value.privileged)
        }

    @Test
    fun `settings enable system apps when the filter is untouched`() =
        runViewModelTest {
            val environment = FakeAppsEnvironment()
            val viewModel = AppsViewModel(environment)
            runCurrent()
            assertEquals(setOf(AppFilter.USER), viewModel.state.value.filter)

            environment.settingsFlow.value = AppSettings(showSystemApps = true)
            runCurrent()

            assertEquals(setOf(AppFilter.USER, AppFilter.SYSTEM), viewModel.state.value.filter)
        }

    @Test
    fun `a manual filter is not overwritten by settings`() =
        runViewModelTest {
            val environment = FakeAppsEnvironment()
            val viewModel = AppsViewModel(environment)
            runCurrent()

            viewModel.setFilter(AppFilter.SYSTEM)
            environment.settingsFlow.value = AppSettings(showSystemApps = false)
            runCurrent()

            assertEquals(setOf(AppFilter.USER, AppFilter.SYSTEM), viewModel.state.value.filter)
        }

    @Test
    fun `remote target without a session keeps local target and reports it`() =
        runViewModelTest {
            val environment = FakeAppsEnvironment(sessionId = null)
            val viewModel = AppsViewModel(environment)
            runCurrent()

            viewModel.setTarget(AppTarget.REMOTE)

            assertEquals(AppTarget.LOCAL, viewModel.state.value.target)
            assertEquals(uiText(R.string.apps_error_remote_needs_connection), viewModel.state.value.message)
        }

    @Test
    fun `local target loads device info and apps`() =
        runViewModelTest {
            val environment = FakeAppsEnvironment(apps = listOf(app))
            val viewModel = AppsViewModel(environment)

            viewModel.setTarget(AppTarget.LOCAL)
            runCurrent()

            assertEquals(phone, viewModel.state.value.deviceInfo)
            assertEquals(listOf(app), viewModel.state.value.apps)
            assertEquals(false, viewModel.state.value.loading)
        }

    @Test
    fun `destructive actions wait for confirmation`() =
        runViewModelTest {
            val environment = FakeAppsEnvironment(apps = listOf(app))
            val viewModel = AppsViewModel(environment)
            viewModel.setTarget(AppTarget.LOCAL)
            runCurrent()

            viewModel.requestAction(app, AppAction.DISABLE)
            assertEquals(
                app,
                viewModel.state.value.pendingAction
                    ?.app,
            )
            assertEquals(
                AppAction.DISABLE,
                viewModel.state.value.pendingAction
                    ?.action,
            )
            assertEquals(0, environment.actionCalls.size)

            viewModel.confirmAction()
            runCurrent()

            assertEquals(1, environment.actionCalls.size)
            assertEquals(AppAction.DISABLE, environment.actionCalls.single().action)
            assertNull(viewModel.state.value.pendingAction)
        }

    @Test
    fun `critical packages are blocked before running an action`() =
        runViewModelTest {
            val critical = app.copy(packageName = "com.android.systemui", isSystem = true)
            val environment = FakeAppsEnvironment(apps = listOf(critical))
            val viewModel = AppsViewModel(environment)
            viewModel.setTarget(AppTarget.LOCAL)
            runCurrent()

            viewModel.requestAction(critical, AppAction.CLEAR_DATA)
            viewModel.confirmAction()
            runCurrent()

            assertEquals(0, environment.actionCalls.size)
            assertEquals(
                uiText(R.string.apps_error_critical),
                viewModel.state.value.message,
            )
        }

    @Test
    fun `advanced package actions require confirmation`() =
        runViewModelTest {
            val environment = FakeAppsEnvironment(actions = listOf(advancedAction))
            val viewModel = AppsViewModel(environment)
            viewModel.setTarget(AppTarget.LOCAL)
            runCurrent()

            viewModel.runAdvancedAction(advancedAction.id, app.packageName)
            assertEquals(
                advancedAction,
                viewModel.state.value.pendingAdvancedAction
                    ?.action,
            )
            assertEquals(0, environment.advancedCalls)

            viewModel.confirmAdvancedAction()
            runCurrent()

            assertEquals(1, environment.advancedCalls)
            assertNull(viewModel.state.value.pendingAdvancedAction)
        }

    @Test
    fun `details are loaded for the selected app`() =
        runViewModelTest {
            val environment = FakeAppsEnvironment(apps = listOf(app), detail = app.copy(label = "Detalle"))
            val viewModel = AppsViewModel(environment)
            runCurrent()

            viewModel.loadDetails(app)
            runCurrent()

            assertEquals(
                "Detalle",
                viewModel.state.value.detail
                    ?.label,
            )
            assertEquals(false, viewModel.state.value.detailsLoading)
        }
}

private class FakeAppsEnvironment(
    settings: AppSettings = AppSettings(),
    override val actions: List<ActionSpec> = emptyList(),
    var sessionId: String? = "192.0.2.10:5555",
    private val remoteAvailable: Boolean = true,
    private val canModify: Boolean = true,
    private val apps: List<ManagedApp> = emptyList(),
    private val detail: ManagedApp? = null,
    private val actionResult: AppActionResult = AppActionResult(true, "ok"),
    private val advancedResult: StepResult =
        StepResult(
            index = 0,
            run = "true",
            status = StepStatus.OK,
        ),
) : AppsEnvironment {
    val settingsFlow = MutableStateFlow(settings)
    val actionCalls = mutableListOf<ActionCall>()
    var advancedCalls = 0

    override val settings = settingsFlow

    override fun activeSessionId(): String? = sessionId

    override fun hasRemoteApps(): Boolean = remoteAvailable

    override fun privileged(target: AppTarget): Boolean = canModify

    override suspend fun localDevice(): DeviceInfo? = phone

    override suspend fun listApps(target: AppTarget): List<ManagedApp> = apps

    override suspend fun appDetails(
        target: AppTarget,
        packageName: String,
    ): ManagedApp? = detail

    override suspend fun runAction(
        target: AppTarget,
        packageName: String,
        action: AppAction,
    ): AppActionResult {
        actionCalls += ActionCall(target, packageName, action)
        return actionResult
    }

    override suspend fun runAdvancedAction(
        target: AppTarget,
        action: ActionSpec,
        packageName: String,
    ): StepResult {
        advancedCalls += 1
        return advancedResult
    }
}

private data class ActionCall(
    val target: AppTarget,
    val packageName: String,
    val action: AppAction,
)

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

private val app = ManagedApp(packageName = "com.example.app", label = "Example")

private val advancedAction =
    ActionSpec(
        id = "advanced.test",
        titleKey = "advanced.test",
        paramKind = ParamKind.PACKAGE,
        risk = Risk.ADVANCED,
        run = "true",
        verify = VerifySpec(run = "true"),
        timeoutMs = 5_000,
    )
