package dev.adboptimizer.core.adb

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import java.io.ByteArrayInputStream
import java.math.BigInteger
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.PrivateKey
import java.security.cert.Certificate
import java.security.cert.CertificateFactory
import java.security.spec.PKCS8EncodedKeySpec
import java.util.Date
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

data class AdbKeyMaterial(
    val privateKey: PrivateKey,
    val certificate: Certificate,
)

internal object AdbKeyCipher {
    private const val PROVIDER = "AndroidKeyStore"
    private const val ALIAS = "adb_optimizer_identity_key"

    fun encrypt(plainText: ByteArray): Pair<ByteArray, ByteArray>? =
        try {
            val keyStore = KeyStore.getInstance(PROVIDER).apply { load(null) }
            var secretKey = keyStore.getKey(ALIAS, null) as? SecretKey
            if (secretKey == null) {
                val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, PROVIDER)
                val spec =
                    KeyGenParameterSpec
                        .Builder(
                            ALIAS,
                            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                        ).setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                        .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                        .setKeySize(256)
                        .build()
                generator.init(spec)
                secretKey = generator.generateKey()
            }
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, secretKey)
            cipher.iv to cipher.doFinal(plainText)
        } catch (_: Exception) {
            null
        }

    fun decrypt(
        iv: ByteArray,
        ciphertext: ByteArray,
    ): ByteArray? {
        return try {
            val keyStore = KeyStore.getInstance(PROVIDER).apply { load(null) }
            val secretKey = keyStore.getKey(ALIAS, null) as? SecretKey ?: return null
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            val spec = GCMParameterSpec(128, iv)
            cipher.init(Cipher.DECRYPT_MODE, secretKey, spec)
            cipher.doFinal(ciphertext)
        } catch (_: Exception) {
            null
        }
    }
}

/**
 * Par RSA + certificado autofirmado que identifica a esta app ante `adbd`.
 *
 * El dispositivo solo usa la clave publica del certificado para el reto AUTH;
 * la firma del certificado no se valida, pero se genera correctamente igual.
 */
object AdbKeys {
    private const val PREFS = "adb-identity"
    private const val VERSION = "key-version"
    private const val KEY_V1 = "private-key"
    private const val KEY_IV = "private-key-iv"
    private const val KEY_CIPHER = "private-key-cipher"
    private const val CERT = "certificate"
    private const val VALIDITY_MS = 10L * 365 * 24 * 60 * 60 * 1000

    fun load(context: Context): AdbKeyMaterial {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val version = prefs.getString(VERSION, null)

        if (version == "2") {
            val ivBase64 = prefs.getString(KEY_IV, null)
            val cipherBase64 = prefs.getString(KEY_CIPHER, null)
            val storedCert = prefs.getString(CERT, null)
            if (ivBase64 != null && cipherBase64 != null && storedCert != null) {
                val decrypted =
                    runCatching {
                        AdbKeyCipher.decrypt(
                            Base64.decode(ivBase64, Base64.NO_WRAP),
                            Base64.decode(cipherBase64, Base64.NO_WRAP),
                        )
                    }.getOrNull()
                if (decrypted != null) {
                    runCatching { return decode(Base64.encodeToString(decrypted, Base64.NO_WRAP), storedCert) }
                }
            }
        } else {
            val storedKey = prefs.getString(KEY_V1, null)
            val storedCert = prefs.getString(CERT, null)
            if (storedKey != null && storedCert != null) {
                val keyBytes = runCatching { Base64.decode(storedKey, Base64.NO_WRAP) }.getOrNull()
                if (keyBytes != null) {
                    val encrypted = AdbKeyCipher.encrypt(keyBytes)
                    if (encrypted != null) {
                        prefs
                            .edit()
                            .putString(VERSION, "2")
                            .putString(KEY_IV, Base64.encodeToString(encrypted.first, Base64.NO_WRAP))
                            .putString(KEY_CIPHER, Base64.encodeToString(encrypted.second, Base64.NO_WRAP))
                            .remove(KEY_V1)
                            .apply()
                    }
                    runCatching { return decode(storedKey, storedCert) }
                }
            }
        }

        val generated = generate()
        val privateKeyBytes = generated.privateKey.encoded
        val encrypted = AdbKeyCipher.encrypt(privateKeyBytes)
        val editor = prefs.edit()
        if (encrypted != null) {
            editor
                .putString(VERSION, "2")
                .putString(KEY_IV, Base64.encodeToString(encrypted.first, Base64.NO_WRAP))
                .putString(KEY_CIPHER, Base64.encodeToString(encrypted.second, Base64.NO_WRAP))
                .remove(KEY_V1)
        } else {
            editor
                .putString(VERSION, "1")
                .putString(KEY_V1, Base64.encodeToString(privateKeyBytes, Base64.NO_WRAP))
        }
        editor
            .putString(CERT, Base64.encodeToString(generated.certificate.encoded, Base64.NO_WRAP))
            .apply()

        return generated
    }

    private fun decode(
        key: String,
        cert: String,
    ): AdbKeyMaterial {
        val keyBytes = Base64.decode(key, Base64.NO_WRAP)
        val certBytes = Base64.decode(cert, Base64.NO_WRAP)
        val privateKey = KeyFactory.getInstance("RSA").generatePrivate(PKCS8EncodedKeySpec(keyBytes))
        val certificate =
            CertificateFactory
                .getInstance("X.509")
                .generateCertificate(ByteArrayInputStream(certBytes))
        return AdbKeyMaterial(privateKey, certificate)
    }

    private fun generate(): AdbKeyMaterial {
        val generator = KeyPairGenerator.getInstance("RSA")
        generator.initialize(2048)
        val keyPair = generator.generateKeyPair()

        val now = System.currentTimeMillis()
        val subject = X500Name("CN=ADB Optimizer,O=ADB Optimizer")
        val builder =
            JcaX509v3CertificateBuilder(
                subject,
                BigInteger.valueOf(now),
                Date(now - 24 * 60 * 60 * 1000),
                Date(now + VALIDITY_MS),
                subject,
                keyPair.public,
            )
        val signer = JcaContentSignerBuilder("SHA256withRSA").build(keyPair.private)
        val certificate = JcaX509CertificateConverter().getCertificate(builder.build(signer))

        return AdbKeyMaterial(keyPair.private, certificate)
    }
}
