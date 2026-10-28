package com.optimizer.tools.data.debloat

import com.optimizer.tools.domain.debloat.DebloatEntry
import com.optimizer.tools.domain.debloat.DebloatRisk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * Catalogo curado de paquetes del sistema (lista UAD-NG).
 *
 * No se empaqueta con la app: se descarga a pedido y se guarda en el almacenamiento
 * privado, como hace la app de referencia, de modo que el proyecto no redistribuye la
 * lista de terceros ni depende de que este actualizada en cada release.
 */
class DebloatCatalog(
    private val file: File,
) {
    private val json = Json { ignoreUnknownKeys = true }

    fun cached(): List<DebloatEntry>? {
        if (!file.exists()) return null
        return runCatching { parse(file.readText()) }.getOrNull()?.takeIf { it.isNotEmpty() }
    }

    fun updatedAt(): Long? = file.takeIf { it.exists() }?.lastModified()?.takeIf { it > 0L }

    suspend fun download(onProgress: (Int) -> Unit = {}): List<DebloatEntry> =
        withContext(Dispatchers.IO) {
            val connection =
                (URL(CATALOG_URL).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 20_000
                    readTimeout = 120_000
                    instanceFollowRedirects = true
                    requestMethod = "GET"
                }
            try {
                val code = connection.responseCode
                if (code !in 200..299) throw IOException("El servidor respondió HTTP $code al descargar la lista.")
                val total = connection.contentLengthLong
                val raw = StringBuilder()
                connection.inputStream.bufferedReader().use { reader ->
                    val buffer = CharArray(64 * 1024)
                    var read = reader.read(buffer)
                    while (read >= 0) {
                        raw.append(buffer, 0, read)
                        if (total > 0) onProgress((raw.length * 100L / total).coerceIn(0L, 100L).toInt())
                        read = reader.read(buffer)
                    }
                }
                val entries = parse(raw.toString())
                if (entries.isEmpty()) throw IOException("La lista descargada no tiene paquetes válidos.")
                file.absoluteFile.parentFile?.mkdirs()
                file.writeText(raw.toString())
                entries
            } finally {
                connection.disconnect()
            }
        }

    /** Lee el formato de UAD-NG: `{ "paquete": { "list": ..., "removal": ..., ... } }`. */
    fun parse(raw: String): List<DebloatEntry> {
        val root = json.parseToJsonElement(raw).jsonObject
        return root
            .mapNotNull { (packageName, value) ->
                val fields = value as? JsonObject ?: return@mapNotNull null
                val risk = DebloatRisk.from(fields.text("removal"))
                if (risk == DebloatRisk.UNSAFE || risk == DebloatRisk.UNKNOWN) return@mapNotNull null
                DebloatEntry(
                    packageName = packageName,
                    source = fields.text("list").orEmpty(),
                    risk = risk,
                    description = fields.text("description").orEmpty().trim(),
                )
            }.sortedBy { it.packageName }
    }

    private fun JsonObject.text(key: String): String? = (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.content

    companion object {
        const val CATALOG_URL =
            "https://raw.githubusercontent.com/Universal-Debloater-Alliance/" +
                "universal-android-debloater-next-generation/main/resources/assets/uad_lists.json"
    }
}
