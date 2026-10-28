package com.optimizer.tools.data.debloat

import com.optimizer.tools.domain.debloat.DebloatRisk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class DebloatCatalogTest {
    private val sample =
        """
        {
          "com.facebook.katana": {"list": "Oem", "description": "Facebook.", "removal": "Recommended"},
          "com.samsung.android.bixby.agent": {"list": "Oem", "description": "Bixby.", "removal": "Advanced"},
          "android.ext.shared": {"list": "Aosp", "description": "Shared library.", "removal": "Unsafe"},
          "com.example.nodata": {"list": "Misc"}
        }
        """.trimIndent()

    @Test
    fun `lee el formato de UAD y descarta lo inseguro y lo desconocido`() {
        val entries = DebloatCatalog(tempFile()).parse(sample)

        assertEquals(listOf("com.facebook.katana", "com.samsung.android.bixby.agent"), entries.map { it.packageName })
        assertEquals(DebloatRisk.RECOMMENDED, entries.first().risk)
        assertEquals("Oem", entries.first().source)
    }

    @Test
    fun `el catalogo se guarda y se vuelve a leer desde el archivo`() {
        val file = tempFile()
        file.writeText(sample)

        val cached = DebloatCatalog(file).cached()

        assertEquals(2, cached?.size)
        assertTrue(cached.orEmpty().none { it.packageName == "android.ext.shared" })
        file.delete()
    }

    private fun tempFile(): File = File.createTempFile("uad", ".json").apply { delete() }
}
