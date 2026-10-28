package com.optimizer.tools.feature.debloat

import com.optimizer.tools.domain.debloat.DebloatAction
import com.optimizer.tools.domain.debloat.DebloatEntry
import com.optimizer.tools.domain.debloat.DebloatRisk
import com.optimizer.tools.domain.debloat.DebloatRun
import com.optimizer.tools.domain.profile.ProfileAppState
import com.optimizer.tools.testutil.runViewModelTest
import dev.adboptimizer.core.model.ManagedApp
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DebloatViewModelTest {
    private val catalog =
        listOf(
            DebloatEntry("com.facebook.katana", "Oem", DebloatRisk.RECOMMENDED, "Facebook."),
            DebloatEntry("com.samsung.android.bixby.agent", "Oem", DebloatRisk.ADVANCED, "Bixby."),
            DebloatEntry("com.example.expert", "Misc", DebloatRisk.EXPERT, "Expert."),
        )

    @Test
    fun `sin dispositivo explica que falta la conexion`() =
        runViewModelTest {
            val viewModel = DebloatViewModel(FakeEnvironment(device = false, catalog = catalog))

            viewModel.onEnter()
            runCurrent()

            assertEquals(false, viewModel.state.value.deviceAvailable)
            assertNotNull(viewModel.state.value.error)
        }

    @Test
    fun `sin catalogo la pantalla pide descargarlo`() =
        runViewModelTest {
            val viewModel = DebloatViewModel(FakeEnvironment(catalog = emptyList()))

            viewModel.onEnter()
            runCurrent()

            assertEquals(false, viewModel.state.value.catalogReady)
        }

    @Test
    fun `solo sugiere paquetes del catalogo que estan en el dispositivo`() =
        runViewModelTest {
            val viewModel = DebloatViewModel(FakeEnvironment(catalog = catalog))

            viewModel.onEnter()
            runCurrent()

            assertEquals(2, viewModel.state.value.suggestions.size)
            assertEquals(
                listOf("com.facebook.katana", "com.samsung.android.bixby.agent"),
                viewModel.state.value.visible
                    .map { it.packageName },
            )
            assertEquals(3, viewModel.state.value.catalogSize)
        }

    @Test
    fun `descargar el catalogo lo deja listo y avisa`() =
        runViewModelTest {
            val environment = FakeEnvironment(catalog = emptyList(), downloadable = catalog)
            val viewModel = DebloatViewModel(environment)

            viewModel.onEnter()
            runCurrent()
            viewModel.downloadCatalog()
            runCurrent()

            assertTrue(viewModel.state.value.catalogReady)
            assertEquals(2, viewModel.state.value.suggestions.size)
            assertNotNull(viewModel.state.value.message)
        }

    @Test
    fun `una accion sin apps aplicables avisa y no abre el dialogo`() =
        runViewModelTest {
            val viewModel = DebloatViewModel(FakeEnvironment(catalog = catalog))
            viewModel.onEnter()
            runCurrent()

            viewModel.toggleSelection("com.samsung.android.bixby.agent")
            viewModel.requestAction(DebloatAction.FREEZE)

            assertNull(viewModel.state.value.pendingAction)
            assertNotNull(viewModel.state.value.message)
        }

    @Test
    fun `confirmar aplica solo a las apps compatibles con la accion`() =
        runViewModelTest {
            val environment = FakeEnvironment(catalog = catalog)
            val viewModel = DebloatViewModel(environment)
            viewModel.onEnter()
            runCurrent()

            viewModel.toggleSelection("com.facebook.katana")
            viewModel.toggleSelection("com.samsung.android.bixby.agent")
            viewModel.requestAction(DebloatAction.FREEZE)
            assertNotNull(viewModel.state.value.pendingAction)
            viewModel.confirmAction()
            runCurrent()

            assertEquals(listOf(DebloatAction.FREEZE), environment.actions)
            assertEquals(listOf("com.facebook.katana"), environment.targets)
            assertNotNull(viewModel.state.value.lastRun)
        }

    @Test
    fun `deshacer pide al entorno volver al estado anterior`() =
        runViewModelTest {
            val environment = FakeEnvironment(catalog = catalog)
            val viewModel = DebloatViewModel(environment)
            viewModel.onEnter()
            runCurrent()
            viewModel.toggleSelection("com.facebook.katana")
            viewModel.requestAction(DebloatAction.FREEZE)
            viewModel.confirmAction()
            runCurrent()

            viewModel.undo()
            runCurrent()

            assertEquals(1, environment.undos)
        }

    private class FakeEnvironment(
        private val device: Boolean = true,
        private val catalog: List<DebloatEntry> = emptyList(),
        private val downloadable: List<DebloatEntry>? = null,
    ) : DebloatEnvironment {
        var actions = mutableListOf<DebloatAction>()
        var targets: List<String> = emptyList()
        var undos = 0

        override fun hasDevice(): Boolean = device

        override fun cachedCatalog(): List<DebloatEntry>? = catalog.takeIf { it.isNotEmpty() }

        override fun catalogUpdatedAt(): Long? = 1L

        override suspend fun downloadCatalog(onProgress: (Int) -> Unit): List<DebloatEntry> {
            onProgress(100)
            return downloadable ?: emptyList()
        }

        override suspend fun apps(): List<ManagedApp> =
            listOf(
                ManagedApp("com.facebook.katana", "Facebook"),
                ManagedApp("com.samsung.android.bixby.agent", "Bixby"),
            )

        override suspend fun states(): Map<String, ProfileAppState> =
            mapOf(
                "com.facebook.katana" to ProfileAppState.ENABLED,
                "com.samsung.android.bixby.agent" to ProfileAppState.DISABLED,
                "com.example.other" to ProfileAppState.ENABLED,
            )

        override suspend fun apply(
            action: DebloatAction,
            targets: List<String>,
            onProgress: (Int, Int, String) -> Unit,
        ): DebloatRun {
            actions += action
            this.targets = targets
            targets.forEachIndexed { index, packageName -> onProgress(index + 1, targets.size, packageName) }
            return DebloatRun(
                action = action,
                changes =
                    targets.map {
                        com.optimizer.tools.domain.profile
                            .ProfileChange(it, ProfileAppState.ENABLED, action.targetState)
                    },
            )
        }

        override suspend fun undo(run: DebloatRun): Int {
            undos++
            return run.count
        }
    }
}
