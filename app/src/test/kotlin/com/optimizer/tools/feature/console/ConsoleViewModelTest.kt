package com.optimizer.tools.feature.console

import com.optimizer.tools.testutil.runViewModelTest
import dev.adboptimizer.core.shell.ShellResult
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ConsoleViewModelTest {
    @Test
    fun `ejecutar guarda la salida y el historial`() =
        runViewModelTest {
            val environment = FakeEnvironment()
            val viewModel = ConsoleViewModel(environment)
            viewModel.onEnter()
            viewModel.setCommand("getprop ro.product.model")

            viewModel.run()
            runCurrent()

            assertEquals(1, viewModel.state.value.entries.size)
            assertEquals(
                "Pixel\n",
                viewModel.state.value.entries
                    .single()
                    .stdout,
            )
            assertEquals(listOf("getprop ro.product.model"), viewModel.state.value.history)
        }

    @Test
    fun `un comando con codigo distinto de cero avisa`() =
        runViewModelTest {
            val viewModel = ConsoleViewModel(FakeEnvironment(result = ShellResult(1, "", "bad", 5)))
            viewModel.onEnter()
            viewModel.setCommand("false")

            viewModel.run()
            runCurrent()

            assertNotNull(viewModel.state.value.message)
            assertEquals(
                1,
                viewModel.state.value.entries
                    .single()
                    .exitCode,
            )
        }

    @Test
    fun `sin dispositivo lo explica y no ejecuta`() =
        runViewModelTest {
            val viewModel = ConsoleViewModel(FakeEnvironment(device = false))
            viewModel.onEnter()
            viewModel.setCommand("id")

            viewModel.run()
            runCurrent()

            assertEquals(false, viewModel.state.value.deviceAvailable)
            assertTrue(
                viewModel.state.value.entries
                    .isEmpty(),
            )
            assertNotNull(viewModel.state.value.error)
        }

    private class FakeEnvironment(
        private val device: Boolean = true,
        private val result: ShellResult = ShellResult(0, "Pixel\n", "", 42),
    ) : ConsoleEnvironment {
        private val history = mutableListOf<String>()

        override fun hasDevice(): Boolean = device

        override fun history(): List<String> = history.reversed()

        override suspend fun run(command: String): ShellResult {
            if (!device) throw IllegalStateException("sin dispositivo")
            history += command
            return result
        }
    }
}
