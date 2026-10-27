package com.optimizer.tools.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.UUID

/** Un cambio aplicado en las tablas de ajustes, con lo necesario para deshacerlo. */
@Serializable
data class SettingsChange(
    val id: String,
    val namespace: String,
    val key: String,
    val previousValue: String,
    val newValue: String,
    val appliedAt: String,
)

/** Contrato de la bitacora del editor de ajustes. */
interface SettingsHistoryStore {
    fun append(change: SettingsChange): SettingsChange

    fun list(): List<SettingsChange>

    fun remove(id: String)
}

/** Historial en memoria, para pruebas y para cuando no hay archivo disponible. */
class InMemorySettingsHistoryStore : SettingsHistoryStore {
    private val entries = mutableListOf<SettingsChange>()

    override fun append(change: SettingsChange): SettingsChange {
        entries += change
        while (entries.size > MAX_ENTRIES) entries.removeAt(0)
        return change
    }

    override fun list(): List<SettingsChange> = entries.reversed()

    override fun remove(id: String) {
        entries.removeAll { it.id == id }
    }

    companion object {
        const val MAX_ENTRIES = 50
    }
}

/** Historial en disco con escritura atomica, igual que la bitacora de recetas. */
class FileSettingsHistoryStore(
    private val file: File,
) : SettingsHistoryStore {
    private val json =
        Json {
            ignoreUnknownKeys = true
            explicitNulls = false
            prettyPrint = true
        }
    private val serializer = ListSerializer(SettingsChange.serializer())
    private val lock = Any()

    private var entries: MutableList<SettingsChange> = load()

    private fun load(): MutableList<SettingsChange> {
        if (!file.exists()) return mutableListOf()
        return runCatching { json.decodeFromString(serializer, file.readText()).toMutableList() }
            .getOrElse { mutableListOf() }
    }

    private fun persist() {
        val parent = file.absoluteFile.parentFile ?: return
        check(parent.exists() || parent.mkdirs()) { "No se pudo crear el directorio del historial" }
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

    override fun append(change: SettingsChange): SettingsChange =
        synchronized(lock) {
            val stored = change.takeIf { it.id.isNotBlank() } ?: change.copy(id = UUID.randomUUID().toString())
            entries += stored
            while (entries.size > InMemorySettingsHistoryStore.MAX_ENTRIES) entries.removeAt(0)
            persist()
            stored
        }

    override fun list(): List<SettingsChange> = synchronized(lock) { entries.reversed() }

    override fun remove(id: String) {
        synchronized(lock) {
            if (entries.removeAll { it.id == id }) persist()
        }
    }
}
