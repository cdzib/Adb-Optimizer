package dev.adboptimizer.core.optimization

import dev.adboptimizer.core.model.JournalEntry

/** Contrato de persistencia de la bitacora de cambios. */
interface ChangeJournalStore {
    fun append(entry: JournalEntry): JournalEntry

    fun list(deviceId: String? = null): List<JournalEntry>

    fun get(id: String): JournalEntry?

    fun markReverted(
        id: String,
        timestamp: String,
    )
}
