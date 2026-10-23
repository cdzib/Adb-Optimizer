package com.optimizer.tools.data.dpc

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/** Descarga un APK desde una URL a la cache local antes de enviarlo al remoto. */
class ApkDownloader(
    private val context: Context,
) {
    suspend fun download(
        url: String,
        onProgress: (String) -> Unit = {},
    ): File =
        withContext(Dispatchers.IO) {
            require(url.startsWith("http://") || url.startsWith("https://")) {
                "La URL del APK debe empezar por http:// o https://"
            }
            val connection =
                (URL(url).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 20_000
                    readTimeout = 120_000
                    instanceFollowRedirects = true
                    requestMethod = "GET"
                }
            try {
                val code = connection.responseCode
                if (code !in 200..299) throw IOException("El servidor respondió HTTP $code al descargar el APK.")
                val total = connection.contentLengthLong
                val target = File(context.cacheDir, "dpc-${System.currentTimeMillis()}.apk")
                connection.inputStream.use { input ->
                    target.outputStream().use { output ->
                        val buffer = ByteArray(64 * 1024)
                        var copied = 0L
                        while (true) {
                            val read = input.read(buffer)
                            if (read < 0) break
                            output.write(buffer, 0, read)
                            copied += read
                            if (total > 0) onProgress("Descargando APK ${copied * 100 / total}%")
                        }
                    }
                }
                if (target.length() == 0L) throw IOException("El APK descargado está vacío.")
                target
            } finally {
                connection.disconnect()
            }
        }
}
