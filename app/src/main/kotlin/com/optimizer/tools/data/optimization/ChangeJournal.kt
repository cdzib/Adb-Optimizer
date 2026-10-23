package com.optimizer.tools.data.optimization

import dev.adboptimizer.core.model.JournalEntry
import dev.adboptimizer.core.optimization.ChangeJournalStore
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.UUID

/**
 * Bitacora local con escritura atomica. Sin backend, sin telemetria.
 * Ver docs/adr/ADR-006-bitacora-json.md.
 */
class ChangeJournal(
    private val file: File,
) : ChangeJournalStore {
    private val json =
        Json {
            ignoreUnknownKeys = true
            explicitNulls = false
            prettyPrint = true
        }
    private val serializer = ListSerializer(JournalEntry.serializer())
    private val lock = Any()

    private var entries: MutableList<JournalEntry> = load()

    private fun load(): MutableList<JournalEntry> {
        if (!file.exists()) return mutableListOf()
        return runCatching { json.decodeFromString(serializer, file.readText()).toMutableList() }
            .getOrElse { mutableListOf() }
    }

    private fun persist() {
        val parent =
            file.absoluteFile.parentFile
                ?: error("La bitácora no tiene directorio padre")
        check(parent.exists() || parent.mkdirs()) { "No se pudo crear el directorio de la bitácora" }
        val tmp = File.createTempFile("${file.name}.", ".tmp", parent)
        try {
            tmp.writeText(json.encodeToString(serializer, entries))
            try {
                Files.move(
                    tmp.toPath(),
                    file.toPath(),
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING,
                )
            } catch (_: java.nio.file.AtomicMoveNotSupportedException) {
                Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING)
            }
        } finally {
            if (tmp.exists()) tmp.delete()
        }
    }

    override fun append(entry: JournalEntry): JournalEntry =
        synchronized(lock) {
            val stored = entry.copy(id = "j_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(4)}")
            entries.add(0, stored)
            persist()
            stored
        }

    override fun list(deviceId: String?): List<JournalEntry> =
        synchronized(lock) {
            if (deviceId == null) entries.toList() else entries.filter { it.deviceId == deviceId }
        }

    override fun get(id: String): JournalEntry? = synchronized(lock) { entries.firstOrNull { it.id == id } }

    override fun markReverted(
        id: String,
        timestamp: String,
    ) = synchronized(lock) {
        val index = entries.indexOfFirst { it.id == id }
        if (index >= 0) {
            entries[index] = entries[index].copy(revertedAt = timestamp)
            persist()
        }
    }
}
