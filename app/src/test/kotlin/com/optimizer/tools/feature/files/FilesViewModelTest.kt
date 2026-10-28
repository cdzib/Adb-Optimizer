package com.optimizer.tools.feature.files

import com.optimizer.tools.data.files.FileEntry
import com.optimizer.tools.testutil.runViewModelTest
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class FilesViewModelTest {
    @Test
    fun `sin dispositivo la pantalla lo explica`() =
        runViewModelTest {
            val viewModel = FilesViewModel(FakeEnvironment(device = false))

            viewModel.onEnter()
            runCurrent()

            assertEquals(false, viewModel.state.value.deviceAvailable)
            assertNotNull(viewModel.state.value.error)
        }

    @Test
    fun `abre la carpeta y navega al padre`() =
        runViewModelTest {
            val environment = FakeEnvironment()
            val viewModel = FilesViewModel(environment)
            viewModel.onEnter()
            runCurrent()

            assertEquals("/sdcard", viewModel.state.value.path)
            assertTrue(
                viewModel.state.value.entries
                    .any { it.isDirectory },
            )

            viewModel.openEntry(environment.entries.first())
            runCurrent()

            assertEquals("/sdcard/Download", viewModel.state.value.path)
            assertTrue(viewModel.state.value.canGoUp)

            viewModel.up()
            runCurrent()

            assertEquals("/sdcard", viewModel.state.value.path)
        }

    @Test
    fun `la busqueda filtra la carpeta actual`() =
        runViewModelTest {
            val viewModel = FilesViewModel(FakeEnvironment())
            viewModel.onEnter()
            runCurrent()

            viewModel.setQuery("apk")

            assertEquals(
                listOf("backup.apk"),
                viewModel.state.value.visible
                    .map { it.name },
            )
        }

    @Test
    fun `borrar pide confirmacion y recarga la carpeta`() =
        runViewModelTest {
            val environment = FakeEnvironment()
            val viewModel = FilesViewModel(environment)
            viewModel.onEnter()
            runCurrent()
            val file =
                viewModel.state.value.entries
                    .first { it.name == "backup.apk" }

            viewModel.requestDelete(file)
            assertNotNull(viewModel.state.value.pendingDelete)
            viewModel.confirmDelete()
            runCurrent()

            assertEquals(listOf("/sdcard/backup.apk"), environment.deleted)
            assertNull(viewModel.state.value.pendingDelete)
            assertNotNull(viewModel.state.value.message)
        }

    @Test
    fun `crear carpeta valida el nombre y la crea`() =
        runViewModelTest {
            val environment = FakeEnvironment()
            val viewModel = FilesViewModel(environment)
            viewModel.onEnter()
            runCurrent()
            viewModel.startCreateFolder()
            viewModel.setFolderName("Nueva")

            viewModel.confirmCreateFolder()
            runCurrent()

            assertEquals(listOf("Nueva"), environment.created)
            assertEquals(false, viewModel.state.value.creatingFolder)
        }

    private class FakeEnvironment(
        private val device: Boolean = true,
    ) : FilesEnvironment {
        val entries =
            listOf(
                FileEntry("Download", "/sdcard/Download", isDirectory = true, isLink = false, sizeBytes = 4096, mode = "drwxrws---"),
                FileEntry("backup.apk", "/sdcard/backup.apk", isDirectory = false, isLink = false, sizeBytes = 1024, mode = "-rw-rw----"),
            )
        val deleted = mutableListOf<String>()
        val created = mutableListOf<String>()

        override fun hasDevice(): Boolean = device

        override suspend fun list(path: String): List<FileEntry> = if (path == "/sdcard/Download") entries else entries

        override suspend fun readText(path: String): String = "contenido"

        override suspend fun createDirectory(
            parent: String,
            name: String,
        ) {
            created += name
        }

        override suspend fun rename(
            path: String,
            newName: String,
        ) = Unit

        override suspend fun delete(path: String) {
            deleted += path
        }

        override suspend fun installApk(path: String): String = "Success"
    }
}
