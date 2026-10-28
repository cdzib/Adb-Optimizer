package com.optimizer.tools.data.files

import dev.adboptimizer.core.shell.AdbShell
import dev.adboptimizer.core.shell.ShellQuote
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException

/** Una entrada de directorio tal como la imprime `ls -la`. */
data class FileEntry(
    val name: String,
    val path: String,
    val isDirectory: Boolean,
    val isLink: Boolean,
    val sizeBytes: Long,
    val mode: String,
) {
    val isApk: Boolean get() = name.endsWith(".apk", ignoreCase = true) || name.endsWith(".apks", ignoreCase = true)
}

/**
 * Navegacion y operaciones de archivos por shell.
 *
 * No usa el protocolo de sincronizacion: todo pasa por comandos POSIX sobre la sesion
 * activa, asi funciona igual con ADB y con Shizuku. Las rutas se citan siempre y las
 * operaciones destructivas se validan antes de ejecutarse.
 */
class RemoteFileRepository(
    private val shell: () -> AdbShell?,
) {
    suspend fun list(path: String): List<FileEntry> =
        withContext(Dispatchers.IO) {
            val directory = normalize(path)
            val response = requireShell().exec("ls -la ${ShellQuote.of(directory)}", LIST_TIMEOUT_MS)
            if (response.exitCode != 0) {
                throw IOException(response.output.trim().ifBlank { "No se pudo abrir $directory." })
            }
            parseListing(directory, response.stdout)
        }

    suspend fun readText(
        path: String,
        maxBytes: Int = MAX_TEXT_BYTES,
    ): String =
        withContext(Dispatchers.IO) {
            val target = normalize(path)
            val size = sizeOf(target)
            if (size != null && size > maxBytes) {
                throw IOException("El archivo pesa $size bytes: es demasiado grande para verlo aquí.")
            }
            val response = requireShell().exec("head -c $maxBytes ${ShellQuote.of(target)}", READ_TIMEOUT_MS)
            if (response.exitCode != 0) throw IOException(response.output.trim().ifBlank { "No se pudo leer el archivo." })
            if (response.stdout.any { it == '\u0000' }) throw IOException("El archivo no es texto.")
            response.stdout
        }

    suspend fun createDirectory(
        parent: String,
        name: String,
    ) {
        val target = childPath(parent, name)
        run("mkdir -p ${ShellQuote.of(target)}", "No se pudo crear la carpeta.")
    }

    suspend fun rename(
        path: String,
        newName: String,
    ) {
        val source = normalize(path)
        val parent = source.substringBeforeLast('/', "/")
        val target = childPath(parent, newName)
        run("mv ${ShellQuote.of(source)} ${ShellQuote.of(target)}", "No se pudo renombrar.")
    }

    suspend fun delete(path: String) {
        val target = normalize(path)
        require(!isProtected(target)) { "Esa ruta no se puede borrar desde la app." }
        run("rm -rf ${ShellQuote.of(target)}", "No se pudo borrar.")
    }

    /** Instala un APK que ya esta en el dispositivo con el propio `pm` del telefono. */
    suspend fun installApk(path: String): String {
        val target = normalize(path)
        val response = requireShell().exec("pm install -r ${ShellQuote.of(target)}", INSTALL_TIMEOUT_MS)
        val output = response.output.trim()
        if (response.exitCode != 0 || !output.contains("Success", ignoreCase = true)) {
            throw IOException(output.ifBlank { "El dispositivo rechazó la instalación." })
        }
        return output
    }

    private suspend fun sizeOf(path: String): Long? {
        val response = requireShell().exec("wc -c ${ShellQuote.of(path)}", READ_TIMEOUT_MS)
        if (response.exitCode != 0) return null
        return response.stdout
            .trim()
            .split(Regex("\\s+"))
            .firstOrNull()
            ?.toLongOrNull()
    }

    private suspend fun run(
        command: String,
        failure: String,
    ) {
        val response = requireShell().exec(command, WRITE_TIMEOUT_MS)
        if (response.exitCode != 0) throw IOException(response.output.trim().ifBlank { failure })
    }

    private fun requireShell(): AdbShell = shell() ?: throw IOException("Conecta un dispositivo o autoriza Shizuku para explorar archivos.")

    private fun childPath(
        parent: String,
        name: String,
    ): String {
        val clean = name.trim()
        require(clean.isNotEmpty() && clean != "." && clean != ".." && !clean.contains('/') && clean.none { it.isISOControl() }) {
            "Nombre de archivo inválido."
        }
        return "${normalize(parent).trimEnd('/')}/$clean"
    }

    companion object {
        const val MAX_TEXT_BYTES = 128 * 1024
        private const val LIST_TIMEOUT_MS = 20_000L
        private const val READ_TIMEOUT_MS = 20_000L
        private const val WRITE_TIMEOUT_MS = 30_000L
        private const val INSTALL_TIMEOUT_MS = 120_000L
        private val PROTECTED = setOf("/", "/sdcard", "/storage", "/system", "/data", "/vendor", "/product", "/apex", "/proc", "/sys")

        fun normalize(path: String): String {
            val raw = path.trim().ifBlank { "/" }
            require(!raw.any { it.isISOControl() }) { "Ruta inválida." }
            val absolute = if (raw.startsWith('/')) raw else "/$raw"
            val parts = absolute.split('/').filter { it.isNotEmpty() && it != "." }
            val stack = ArrayDeque<String>()
            parts.forEach { part ->
                when (part) {
                    ".." -> if (stack.isNotEmpty()) stack.removeLast()
                    else -> stack.addLast(part)
                }
            }
            return if (stack.isEmpty()) "/" else stack.joinToString("/", prefix = "/")
        }

        fun isProtected(path: String): Boolean = normalize(path) in PROTECTED || normalize(path).let { it == "/" || it == "/sdcard" }

        fun parent(path: String): String {
            val normalized = normalize(path)
            if (normalized == "/") return "/"
            val cut = normalized.lastIndexOf('/')
            return if (cut <= 0) "/" else normalized.substring(0, cut)
        }

        fun segments(path: String): List<Pair<String, String>> {
            val normalized = normalize(path)
            var accumulated = ""
            return normalized.split('/').filter { it.isNotEmpty() }.map { part ->
                accumulated += "/$part"
                part to accumulated
            }
        }

        /** Convierte la salida de `ls -la` en entradas, ignorando `.` y `..`. */
        fun parseListing(
            directory: String,
            output: String,
        ): List<FileEntry> =
            output
                .lineSequence()
                .mapNotNull { line -> parseLine(directory, line) }
                .sortedWith(compareBy<FileEntry>({ !it.isDirectory }, { it.name.lowercase() }))
                .toList()

        private val LISTING =
            Regex(
                """^([bcdlps-][rwxsStT-]{9}[.+@]?)\s+\d+\s+\S+\s+\S+\s+(\d+)\s+""" +
                    """(?:\d{4}-\d{2}-\d{2}\s+\d{2}:\d{2}|[A-Z][a-z]{2}\s+\d{1,2}\s+(?:\d{2}:\d{2}|\d{4}))\s+(.+)$""",
            )

        private fun parseLine(
            directory: String,
            line: String,
        ): FileEntry? {
            val match = LISTING.find(line.trim()) ?: return null
            val mode = match.groupValues[1]
            val size = match.groupValues[2].toLongOrNull() ?: 0L
            val rawName = match.groupValues[3].trim()
            if (rawName == "." || rawName == ".." || rawName.isEmpty()) return null
            val isLink = mode.startsWith("l")
            val name = if (isLink) rawName.substringBefore(" -> ") else rawName
            return FileEntry(
                name = name,
                path = if (directory == "/") "/$name" else "$directory/$name",
                isDirectory = mode.startsWith("d"),
                isLink = isLink,
                sizeBytes = size,
                mode = mode,
            )
        }
    }
}
