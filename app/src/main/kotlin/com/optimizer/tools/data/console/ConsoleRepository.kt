package com.optimizer.tools.data.console

import dev.adboptimizer.core.shell.AdbShell
import dev.adboptimizer.core.shell.ShellResult
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import java.io.File
import java.io.IOException
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/** Historial de comandos de la consola, con escritura atomica. */
class ConsoleHistoryStore(
    private val file: File,
) {
    private val json =
        Json {
            ignoreUnknownKeys = true
            prettyPrint = true
        }
    private val serializer = ListSerializer(String.serializer())
    private val lock = Any()

    private var commands: MutableList<String> = load()

    fun list(): List<String> = synchronized(lock) { commands.reversed() }

    fun append(command: String): List<String> =
        synchronized(lock) {
            val clean = command.trim()
            if (clean.isNotEmpty()) {
                commands.removeAll { it == clean }
                commands += clean
                while (commands.size > MAX_ENTRIES) commands.removeAt(0)
                persist()
            }
            commands.reversed()
        }

    private fun load(): MutableList<String> {
        if (!file.exists()) return mutableListOf()
        return runCatching { json.decodeFromString(serializer, file.readText()).toMutableList() }
            .getOrElse { mutableListOf() }
    }

    private fun persist() {
        val parent = file.absoluteFile.parentFile ?: return
        check(parent.exists() || parent.mkdirs()) { "No se pudo crear el directorio de la consola." }
        val tmp = File.createTempFile("${file.name}.", ".tmp", parent)
        try {
            tmp.writeText(json.encodeToString(serializer, commands))
            try {
                Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING)
            }
        } finally {
            if (tmp.exists()) tmp.delete()
        }
    }

    companion object {
        const val MAX_ENTRIES = 40
    }
}

/** Ejecuta comandos en el destino activo para la consola. */
class ConsoleRepository(
    private val shellProvider: () -> AdbShell?,
    private val history: ConsoleHistoryStore,
) {
    fun available(): Boolean = shellProvider() != null

    fun history(): List<String> = history.list()

    suspend fun run(command: String): ShellResult {
        val trimmed = command.trim()
        if (trimmed.isEmpty()) throw IOException("Escribe un comando.")
        val adb = shellProvider() ?: throw IOException("Conecta un dispositivo o autoriza Shizuku para ejecutar comandos.")
        val result = adb.exec(trimmed, COMMAND_TIMEOUT_MS)
        history.append(trimmed)
        return result
    }

    companion object {
        const val COMMAND_TIMEOUT_MS = 60_000L
    }
}
