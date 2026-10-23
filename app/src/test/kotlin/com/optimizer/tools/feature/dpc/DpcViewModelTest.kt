package com.optimizer.tools.feature.dpc

import android.net.Uri
import com.optimizer.tools.R
import com.optimizer.tools.core.ui.uiText
import com.optimizer.tools.domain.dpc.DpcMode
import com.optimizer.tools.domain.dpc.DpcPreflight
import com.optimizer.tools.domain.dpc.DpcReceiver
import com.optimizer.tools.domain.dpc.DpcResult
import com.optimizer.tools.testutil.runViewModelTest
import dev.adboptimizer.core.adb.RemoteApkInstallOutcome
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class DpcViewModelTest {
    @Test
    fun `preflight without a remote device reports the connection error`() =
        runViewModelTest {
            val environment = FakeDpcEnvironment(remote = false)
            val viewModel = DpcViewModel(environment)

            viewModel.onEnter()
            runCurrent()

            assertEquals(uiText(R.string.dpc_error_no_device), viewModel.state.value.message)
            assertEquals(0, environment.preflightCalls)
        }

    @Test
    fun `preflight loads the remote state`() =
        runViewModelTest {
            val environment = FakeDpcEnvironment()
            val viewModel = DpcViewModel(environment)

            viewModel.onEnter()
            runCurrent()

            assertEquals(preflight, viewModel.state.value.preflight)
            assertEquals(1, environment.preflightCalls)
            assertEquals(false, viewModel.state.value.busy)
        }

    @Test
    fun `empty receiver detection explains how to continue`() =
        runViewModelTest {
            val environment = FakeDpcEnvironment(receivers = emptyList())
            val viewModel = DpcViewModel(environment)

            viewModel.detectReceivers()
            runCurrent()

            assertEquals(
                uiText(R.string.dpc_error_no_receivers, "com.macropay.dpcmacro"),
                viewModel.state.value.message,
            )
        }

    @Test
    fun `owner requires a component and a remote device`() =
        runViewModelTest {
            val environment = FakeDpcEnvironment(remote = false)
            val viewModel = DpcViewModel(environment)

            viewModel.setComponent("sin-componente")
            viewModel.setOwner()
            assertEquals(uiText(R.string.dpc_error_component), viewModel.state.value.message)

            viewModel.setComponent("com.example/.Admin")
            viewModel.setOwner()
            assertEquals(uiText(R.string.dpc_error_no_device), viewModel.state.value.message)
            assertEquals(0, environment.setOwnerCalls)
        }

    @Test
    fun `owner uses the selected component and refreshes preflight`() =
        runViewModelTest {
            val environment = FakeDpcEnvironment()
            val viewModel = DpcViewModel(environment)
            val component = "com.example/.Admin"

            viewModel.setComponent(component)
            viewModel.setOwner()
            runCurrent()

            assertEquals(1, environment.setOwnerCalls)
            assertEquals(component, environment.lastOwnerComponent)
            assertEquals(DpcMode.DEVICE, environment.lastOwnerMode)
            assertEquals(1, environment.preflightCalls)
        }

    @Test
    fun `provisioning qr requires a url`() =
        runViewModelTest {
            val viewModel = DpcViewModel(FakeDpcEnvironment())

            viewModel.generateProvisioningQr()
            runCurrent()

            assertEquals(
                uiText(R.string.dpc_error_url_qr),
                viewModel.state.value.message,
            )
        }

    @Test
    fun `provisioning qr downloads and includes url and checksum`() =
        runViewModelTest {
            val environment =
                FakeDpcEnvironment(
                    downloadedFile = File("build/tmp/dpc-test.apk"),
                    checksum = "abc123",
                )
            val viewModel = DpcViewModel(environment)
            viewModel.setUrl("https://example.com/dpc.apk")

            viewModel.generateProvisioningQr()
            runCurrent()

            assertEquals(listOf("https://example.com/dpc.apk"), environment.downloadedUrls)
            assertEquals("abc123", viewModel.state.value.checksum)
            val payload =
                viewModel.state.value.qrPayload
                    .orEmpty()
            assertTrue(payload.contains("https://example.com/dpc.apk"))
            assertTrue(payload.contains("abc123"))
            assertTrue(payload.contains("com.macropay.dpcmacro"))
            assertEquals(false, viewModel.state.value.busy)
        }

    @Test
    fun `installing from url runs the installer and detects receivers`() =
        runViewModelTest {
            val environment =
                FakeDpcEnvironment(
                    downloadedFile = File("build/tmp/dpc-test.apk"),
                    installOutcomes =
                        listOf(
                            RemoteApkInstallOutcome("com.macropay.dpcmacro", true, "Success"),
                        ),
                )
            val viewModel = DpcViewModel(environment)
            viewModel.setUrl("https://example.com/dpc.apk")

            viewModel.installFromUrl()
            runCurrent()

            assertEquals(1, environment.installFilesCalls)
            assertEquals(1, environment.receiversCalls)
            assertEquals(false, viewModel.state.value.busy)
        }
}

private class FakeDpcEnvironment(
    var remote: Boolean = true,
    var sessionId: String? = "192.0.2.10:5555",
    var preflightResult: DpcPreflight = preflight,
    private var receivers: List<DpcReceiver> = listOf(receiver),
    private val setOwnerResult: DpcResult = DpcResult(true, "ok"),
    private val removeOwnerResult: DpcResult = DpcResult(true, "ok"),
    val downloadedFile: File = File("build/tmp/dpc-test.apk"),
    private val checksum: String = "checksum",
    private val installOutcomes: List<RemoteApkInstallOutcome> = emptyList(),
) : DpcEnvironment {
    var preflightCalls = 0
    var receiversCalls = 0
    var setOwnerCalls = 0
    var removeOwnerCalls = 0
    var installFilesCalls = 0
    var lastOwnerComponent: String? = null
    var lastOwnerMode: DpcMode? = null
    val downloadedUrls = mutableListOf<String>()

    override fun hasRemoteDevice(): Boolean = remote

    override fun activeSessionId(): String? = sessionId

    override suspend fun preflight(mode: DpcMode): DpcPreflight {
        preflightCalls += 1
        return preflightResult
    }

    override suspend fun receivers(packageName: String?): List<DpcReceiver> {
        receiversCalls += 1
        return receivers
    }

    override suspend fun setOwner(
        component: String,
        mode: DpcMode,
    ): DpcResult {
        setOwnerCalls += 1
        lastOwnerComponent = component
        lastOwnerMode = mode
        return setOwnerResult
    }

    override suspend fun removeOwner(component: String): DpcResult {
        removeOwnerCalls += 1
        return removeOwnerResult
    }

    override suspend fun copyToCache(
        uri: Uri,
        prefix: String,
    ): File = downloadedFile

    override suspend fun installFiles(
        files: List<File>,
        onProgress: (String) -> Unit,
    ): List<RemoteApkInstallOutcome> {
        installFilesCalls += 1
        onProgress("Instalando")
        return installOutcomes
    }

    override suspend fun installApks(
        uris: List<Uri>,
        onProgress: (String) -> Unit,
    ): List<RemoteApkInstallOutcome> = installOutcomes

    override suspend fun download(
        url: String,
        onProgress: (String) -> Unit,
    ): File {
        downloadedUrls += url
        onProgress("Descargando")
        return downloadedFile
    }

    override fun checksum(file: File): String = checksum
}

private val preflight =
    DpcPreflight(
        sdk = 35,
        owners = emptyList(),
        users = emptyList(),
        accounts = 0,
        checks = emptyList(),
        ready = true,
    )

private val receiver =
    DpcReceiver(
        component = "com.macropay.dpcmacro/com.macropay.dpcmacro.DeviceAdminReceiver",
        packageName = "com.macropay.dpcmacro",
    )
