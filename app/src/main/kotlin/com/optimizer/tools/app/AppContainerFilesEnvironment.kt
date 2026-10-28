package com.optimizer.tools.app

import com.optimizer.tools.data.AppContainer
import com.optimizer.tools.data.files.FileEntry
import com.optimizer.tools.feature.files.FilesEnvironment

/** Adaptador de composición para el explorador de archivos. */
internal class AppContainerFilesEnvironment(
    private val container: AppContainer,
) : FilesEnvironment {
    override fun hasDevice(): Boolean = container.shell != null || localReady()

    override suspend fun list(path: String): List<FileEntry> = container.files.list(path)

    override suspend fun readText(path: String): String = container.files.readText(path)

    override suspend fun createDirectory(
        parent: String,
        name: String,
    ) = container.files.createDirectory(parent, name)

    override suspend fun rename(
        path: String,
        newName: String,
    ) = container.files.rename(path, newName)

    override suspend fun delete(path: String) = container.files.delete(path)

    override suspend fun installApk(path: String): String = container.files.installApk(path)

    private fun localReady(): Boolean = container.localPrivilegeReady()
}
