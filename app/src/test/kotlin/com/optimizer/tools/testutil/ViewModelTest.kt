package com.optimizer.tools.testutil

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain

/**
 * Ejecuta pruebas de ViewModel con un Main dispatcher controlado por el scheduler
 * de `runTest`. El bloque debe usar `runCurrent()` o `advanceUntilIdle()` cuando
 * el ViewModel tenga corrutinas pendientes.
 *
 * El dispatcher se restaura después de que `runTest` termina su limpieza, para
 * evitar que corrutinas del ViewModel sigan usando un Main ya retirado.
 */
@OptIn(ExperimentalCoroutinesApi::class)
fun runViewModelTest(block: suspend TestScope.() -> Unit) {
    val dispatcher = UnconfinedTestDispatcher()
    Dispatchers.setMain(dispatcher)
    try {
        runTest(dispatcher) { block() }
    } finally {
        Dispatchers.resetMain()
    }
}
