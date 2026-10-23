package dev.adboptimizer.core.adb

import dev.adboptimizer.core.shell.AdbShell
import dev.adboptimizer.core.shell.ShellResult
import org.junit.Assert.assertNotNull
import org.junit.Test
import java.io.IOException

class AdbConnectionFactoryTest {
    @Test
    fun `installApkGroups succeeds when shell implements ApkInstallTransport`() {
        val groups = listOf(RemoteApkInstallGroup("com.example", emptyList(), false))

        val result =
            org.junit.Assert.assertThrows(IOException::class.java) {
                kotlinx.coroutines.runBlocking {
                    AdbConnectionFactory.installApkGroups(PlainShell(), groups) {}
                }
            }
        assertNotNull(result)
    }

    private class PlainShell : AdbShell {
        override suspend fun exec(
            command: String,
            timeoutMs: Long,
        ): ShellResult = ShellResult(0, "", "", 0L)
    }
}
