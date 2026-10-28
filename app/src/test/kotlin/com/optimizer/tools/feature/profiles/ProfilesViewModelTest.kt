package com.optimizer.tools.feature.profiles

import com.optimizer.tools.domain.profile.AppProfile
import com.optimizer.tools.domain.profile.ProfileApp
import com.optimizer.tools.domain.profile.ProfileAppState
import com.optimizer.tools.domain.profile.ProfilePlan
import com.optimizer.tools.domain.profile.ProfileProgress
import com.optimizer.tools.domain.profile.ProfileRun
import com.optimizer.tools.domain.profile.ProfileStep
import com.optimizer.tools.domain.profile.ProfileStepKind
import com.optimizer.tools.testutil.runViewModelTest
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ProfilesViewModelTest {
    @Test
    fun `sin dispositivo la pantalla explica que falta la conexion`() =
        runViewModelTest {
            val environment = FakeEnvironment(device = false)
            val viewModel = ProfilesViewModel(environment)

            viewModel.onEnter()
            runCurrent()

            assertTrue(viewModel.state.value.error != null)
            assertEquals(false, viewModel.state.value.deviceAvailable)
        }

    @Test
    fun `crear un perfil guarda el snapshot y recarga la lista`() =
        runViewModelTest {
            val environment = FakeEnvironment()
            val viewModel = ProfilesViewModel(environment)

            viewModel.onEnter()
            runCurrent()
            viewModel.startCreate()
            viewModel.setNameDraft("Teléfono limpio")
            viewModel.createProfile()
            runCurrent()

            assertEquals(2, environment.profiles.size)
            assertEquals("Teléfono limpio", environment.profiles.last().name)
            assertTrue(
                viewModel.state.value.profiles
                    .any { it.name == "Teléfono limpio" },
            )
            assertNotNull(viewModel.state.value.message)
        }

    @Test
    fun `el plan se muestra antes de aplicar`() =
        runViewModelTest {
            val environment = FakeEnvironment()
            val viewModel = ProfilesViewModel(environment)
            viewModel.onEnter()
            runCurrent()

            viewModel.preview(environment.profiles.single())
            runCurrent()

            val preview = viewModel.state.value.preview
            assertNotNull(preview)
            assertEquals(1, preview?.plan?.appCount)
        }

    @Test
    fun `aplicar actualiza la ultima ejecucion y avisa del resultado`() =
        runViewModelTest {
            val environment = FakeEnvironment()
            val viewModel = ProfilesViewModel(environment)
            viewModel.onEnter()
            runCurrent()

            viewModel.apply(environment.profiles.single())
            runCurrent()

            assertEquals(1, environment.applied)
            assertEquals(
                1,
                viewModel.state.value.lastRun
                    ?.count,
            )
            assertNotNull(viewModel.state.value.message)
        }

    @Test
    fun `el aviso de drift aparece cuando el perfil vigilado cambia`() =
        runViewModelTest {
            val environment = FakeEnvironment(drift = true)
            val viewModel = ProfilesViewModel(environment)

            viewModel.onEnter()
            runCurrent()

            assertEquals(
                1,
                viewModel.state.value.drift
                    ?.plan
                    ?.appCount,
            )
        }

    @Test
    fun `deshacer llama al entorno con los cambios de la ultima ejecucion`() =
        runViewModelTest {
            val environment = FakeEnvironment()
            val viewModel = ProfilesViewModel(environment)
            viewModel.onEnter()
            runCurrent()
            viewModel.apply(environment.profiles.single())
            runCurrent()

            viewModel.undoLastRun()
            runCurrent()

            assertEquals(1, environment.undone)
        }

    private class FakeEnvironment(
        private val device: Boolean = true,
        private val drift: Boolean = false,
    ) : ProfilesEnvironment {
        val profiles = mutableListOf<AppProfile>()
        var applied = 0
        var undone = 0
        private var lastRun: ProfileRun? = null

        init {
            profiles +=
                AppProfile(
                    id = "p1",
                    name = "Limpio",
                    createdAt = 1L,
                    apps = listOf(ProfileApp("com.example.bloat", "Bloat", ProfileAppState.DISABLED)),
                    watched = drift,
                )
        }

        override fun hasDevice(): Boolean = device

        override suspend fun profiles(): List<AppProfile> = profiles.toList()

        override suspend fun lastRun(): ProfileRun? = lastRun

        override suspend fun snapshot(): List<ProfileApp> = listOf(ProfileApp("com.example", "Example", ProfileAppState.ENABLED))

        override suspend fun plan(profile: AppProfile): ProfilePlan =
            ProfilePlan(steps = listOf(ProfileStep(ProfileStepKind.DISABLE, listOf("com.example.bloat"))))

        override suspend fun save(profile: AppProfile): Boolean {
            val index = profiles.indexOfFirst { it.id == profile.id }
            if (index >= 0) profiles[index] = profile else profiles += profile
            return true
        }

        override suspend fun delete(id: String): Boolean = profiles.removeAll { it.id == id }

        override suspend fun setWatched(
            id: String,
            watched: Boolean,
        ): Boolean {
            val index = profiles.indexOfFirst { it.id == id }
            if (index < 0) return false
            profiles[index] = profiles[index].copy(watched = watched)
            return true
        }

        override suspend fun apply(
            profile: AppProfile,
            onProgress: (ProfileProgress) -> Unit,
        ): ProfileRun {
            applied++
            onProgress(ProfileProgress(1, 1, "com.example.bloat", ProfileStepKind.DISABLE))
            lastRun =
                ProfileRun(
                    id = "run",
                    profileId = profile.id,
                    profileName = profile.name,
                    appliedAt = 2L,
                    changes =
                        listOf(
                            com.optimizer.tools.domain.profile.ProfileChange(
                                "com.example.bloat",
                                ProfileAppState.ENABLED,
                                ProfileAppState.DISABLED,
                            ),
                        ),
                )
            return requireNotNull(lastRun)
        }

        override suspend fun undo(run: ProfileRun): Int {
            undone++
            return run.count
        }
    }
}
