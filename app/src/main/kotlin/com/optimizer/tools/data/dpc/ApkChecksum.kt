package com.optimizer.tools.data.dpc

import android.util.Base64
import java.io.File
import java.security.MessageDigest

/** Checksum SHA-256 en Base64 que exige el QR de aprovisionamiento de Android. */
object ApkChecksum {
    fun sha256Base64(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        return Base64.encodeToString(digest.digest(), Base64.NO_WRAP)
    }
}
