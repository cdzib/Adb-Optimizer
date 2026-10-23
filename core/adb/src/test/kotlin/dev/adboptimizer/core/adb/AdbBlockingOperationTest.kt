package dev.adboptimizer.core.adb

import dev.adboptimizer.core.shell.AdbErrorKind
import dev.adboptimizer.core.shell.AdbException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch

class AdbBlockingOperationTest {
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    @Test
    fun `deadline interrupts a stuck read and reports timeout instead of waiting forever`() =
        runTest {
            supervisorScope {
                val started = CompletableDeferred<Unit>()
                var aborted = false
                val operation =
                    async(start = CoroutineStart.UNDISPATCHED) {
                        runAdbBlockingOperation(100, Dispatchers.IO, { aborted = true }) {
                            started.complete(Unit)
                            CountDownLatch(1).await()
                        }
                    }
                // Wait for the real blocking worker without advancing the virtual deadline.
                withContext(Dispatchers.Default) { started.await() }
                advanceTimeBy(100)
                runCurrent()
                val failure =
                    try {
                        operation.await()
                        error("A stuck read must not complete successfully")
                    } catch (error: AdbException) {
                        error
                    }
                assertEquals(AdbErrorKind.TIMEOUT, failure.kind)
                assertTrue(aborted)
            }
        }

    @Test fun `cancelling a stuck read interrupts worker and closes transport`() =
        runBlocking {
            val started = CompletableDeferred<Unit>()
            var aborted = false
            var published = false
            val job =
                launch {
                    runAdbBlockingOperation(60_000, Dispatchers.IO, { aborted = true }) {
                        started.complete(Unit)
                        CountDownLatch(1).await()
                    }
                    published = true
                }
            started.await()
            job.cancelAndJoin()
            assertTrue(aborted)
            assertFalse(published)
        }

    @Test fun `successful command preserves transport and result`() =
        runBlocking {
            var aborted = false
            assertEquals("ok", runAdbBlockingOperation(60_000, Dispatchers.IO, { aborted = true }) { "ok" })
            assertFalse(aborted)
        }
}
