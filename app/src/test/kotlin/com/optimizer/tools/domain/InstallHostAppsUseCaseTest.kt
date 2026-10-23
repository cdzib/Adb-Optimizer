package com.optimizer.tools.domain

import com.optimizer.tools.domain.apk.ApkDescriptor
import com.optimizer.tools.domain.port.ApkInstaller
import com.optimizer.tools.domain.port.HostAppsSource
import com.optimizer.tools.domain.usecase.InstallHostAppsUseCase
import dev.adboptimizer.core.adb.RemoteApkInstallOutcome
import dev.adboptimizer.core.model.ManagedApp
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class InstallHostAppsUseCaseTest {
    private class FakeHostApps(
        private val files: List<File>,
    ) : HostAppsSource {
        override suspend fun list(includeSystem: Boolean): List<ManagedApp> = emptyList()

        override suspend fun stageApks(packageName: String): List<ApkDescriptor> =
            files.mapIndexed { index, file -> ApkDescriptor(file, packageName, if (index == 0) null else "split$index") }
    }

    private class RecordingInstaller : ApkInstaller {
        var received: List<ApkDescriptor> = emptyList()

        override suspend fun installDescriptors(
            descriptors: List<ApkDescriptor>,
            onProgress: (String) -> Unit,
        ): List<RemoteApkInstallOutcome> {
            received = descriptors
            onProgress("instalando")
            return emptyList()
        }
    }

    @Test
    fun `stages selected packages and deletes temporary files after install`() =
        runBlocking {
            val base = File.createTempFile("base-", ".apk")
            val split = File.createTempFile("split-", ".apk")
            val installer = RecordingInstaller()
            val useCase = InstallHostAppsUseCase(FakeHostApps(listOf(base, split)), installer)

            useCase(listOf("com.example.app")) { }

            assertEquals(listOf("com.example.app", "com.example.app"), installer.received.map { it.packageName })
            assertTrue(installer.received.first().splitName == null)
            assertFalse(base.exists())
            assertFalse(split.exists())
        }

    @Test
    fun `requires at least one package`() =
        runBlocking<Unit> {
            val result =
                runCatching {
                    InstallHostAppsUseCase(FakeHostApps(emptyList()), RecordingInstaller())(emptyList()) { }
                }
            assertTrue(result.exceptionOrNull() is IllegalArgumentException)
        }
}
