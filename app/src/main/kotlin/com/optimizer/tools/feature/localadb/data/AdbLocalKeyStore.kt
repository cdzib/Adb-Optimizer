package com.optimizer.tools.feature.localadb.data

import dadb.AdbKeyPair
import java.io.File

/**
 * Par de llaves ADB propio de esta función. Se guarda en filesDir y no comparte
 * identidad con las conexiones remotas del resto de la app.
 */
class AdbLocalKeyStore(
    private val directory: File,
) {
    private val privateKey = File(directory, "adbkey")
    private val publicKey = File(directory, "adbkey.pub")

    @Synchronized
    fun read(): AdbKeyPair {
        if (!privateKey.exists()) {
            directory.mkdirs()
            AdbKeyPair.generate(privateKey, publicKey)
        }
        return AdbKeyPair.read(privateKey, publicKey)
    }
}
