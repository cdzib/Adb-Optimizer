package dev.adboptimizer.core.adb

import org.junit.Assert.assertNotNull
import org.junit.Test
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.spec.PKCS8EncodedKeySpec

class AdbKeysTest {
    @Test
    fun `generates valid RSA keypair and certificate material`() {
        val generator = KeyPairGenerator.getInstance("RSA")
        generator.initialize(2048)
        val keyPair = generator.generateKeyPair()

        assertNotNull(keyPair.private)
        assertNotNull(keyPair.public)

        val encoded = keyPair.private.encoded
        val decoded = KeyFactory.getInstance("RSA").generatePrivate(PKCS8EncodedKeySpec(encoded))
        assertNotNull(decoded)
    }
}
