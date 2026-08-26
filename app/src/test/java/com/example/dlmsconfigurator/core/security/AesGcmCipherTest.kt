package com.example.dlmsconfigurator.core.security

import org.junit.Assert.assertArrayEquals
import org.junit.Test

class AesGcmCipherTest {
    @Test
    fun encryptDecrypt_roundTrips() {
        val key = ByteArray(16) { it.toByte() }
        val iv = ByteArray(12) { (it + 1).toByte() }
        val aad = "meter-1".toByteArray()
        val plaintext = "dlms payload".toByteArray()

        val cipher = AesGcmCipher(key)
        val encrypted = cipher.encrypt(iv, plaintext, aad)
        val decrypted = cipher.decrypt(iv, encrypted, aad)

        assertArrayEquals(plaintext, decrypted)
    }
}
