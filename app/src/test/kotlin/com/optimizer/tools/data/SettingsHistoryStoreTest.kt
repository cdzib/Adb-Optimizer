package com.optimizer.tools.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class SettingsHistoryStoreTest {
    @Test
    fun `el historial en memoria lista del mas reciente al mas antiguo`() {
        val store = InMemorySettingsHistoryStore()
        store.append(change("1", "a"))
        store.append(change("2", "b"))

        assertEquals(listOf("2", "1"), store.list().map { it.id })
        store.remove("2")
        assertEquals(listOf("1"), store.list().map { it.id })
    }

    @Test
    fun `el historial en disco sobrevive a una nueva instancia`() {
        val file = File.createTempFile("settings-history", ".json").apply { delete() }
        try {
            FileSettingsHistoryStore(file).append(change("1", "demo"))

            val reloaded = FileSettingsHistoryStore(file)
            assertEquals(1, reloaded.list().size)
            assertEquals("demo", reloaded.list().single().key)
        } finally {
            file.delete()
        }
    }

    @Test
    fun `el historial en memoria recorta al maximo`() {
        val store = InMemorySettingsHistoryStore()
        repeat(InMemorySettingsHistoryStore.MAX_ENTRIES + 5) { index -> store.append(change("$index", "k$index")) }

        assertEquals(InMemorySettingsHistoryStore.MAX_ENTRIES, store.list().size)
        assertTrue(store.list().first().id == "${InMemorySettingsHistoryStore.MAX_ENTRIES + 4}")
    }

    private fun change(
        id: String,
        key: String,
    ) = SettingsChange(
        id = id,
        namespace = "global",
        key = key,
        previousValue = "0",
        newValue = "1",
        appliedAt = "2026-01-01T00:00:00Z",
    )
}
