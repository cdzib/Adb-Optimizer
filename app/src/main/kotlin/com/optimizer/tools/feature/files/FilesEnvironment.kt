package com.optimizer.tools.feature.files

import com.optimizer.tools.data.files.FileEntry

/** Puertos del explorador de archivos sobre el destino activo. */
interface FilesEnvironment {
    fun hasDevice(): Boolean

    suspend fun list(path: String): List<FileEntry>

    suspend fun readText(path: String): String

    suspend fun createDirectory(
        parent: String,
        name: String,
    )

    suspend fun rename(
        path: String,
        newName: String,
    )

    suspend fun delete(path: String)

    suspend fun installApk(path: String): String
}
