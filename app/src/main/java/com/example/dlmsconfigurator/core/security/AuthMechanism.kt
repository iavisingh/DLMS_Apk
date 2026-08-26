package com.example.dlmsconfigurator.core.security

import java.security.MessageDigest
import javax.crypto.Mac
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

interface AuthMechanism {
    fun buildResponse(challenge: ByteArray): ByteArray
}

class LlsAuth(private val password: ByteArray) : AuthMechanism {
    override fun buildResponse(challenge: ByteArray): ByteArray = password
}

class HlsAuth(
    private val secret: ByteArray,
    private val algorithm: HlsAlgorithm
) : AuthMechanism {
    override fun buildResponse(challenge: ByteArray): ByteArray {
        return when (algorithm) {
            HlsAlgorithm.MD5 -> digest("MD5", challenge)
            HlsAlgorithm.SHA256 -> digest("SHA-256", challenge)
            HlsAlgorithm.HMAC_SHA256 -> hmac("HmacSHA256", challenge)
        }
    }

    private fun digest(name: String, challenge: ByteArray): ByteArray {
        return MessageDigest.getInstance(name).digest(secret + challenge)
    }

    private fun hmac(name: String, challenge: ByteArray): ByteArray {
        val mac = Mac.getInstance(name)
        mac.init(SecretKeySpec(secret, name))
        return mac.doFinal(challenge)
    }
}

enum class HlsAlgorithm {
    MD5,
    SHA256,
    HMAC_SHA256
}

class HlsGmacAuth(
    private val authenticationKey: ByteArray,
    private val systemTitle: ByteArray,
    private val invocationCounterProvider: () -> Long,
    private val securityControl: Byte = 0x10
) : AuthMechanism {
    init {
        require(systemTitle.size == 8) { "DLMS system title must be 8 bytes for GMAC nonce construction" }
        require(authenticationKey.size == 16 || authenticationKey.size == 24 || authenticationKey.size == 32) {
            "AES authentication key must be 16, 24, or 32 bytes"
        }
    }

    override fun buildResponse(challenge: ByteArray): ByteArray {
        val invocationCounter = invocationCounterProvider()
        val counterBytes = byteArrayOf(
            ((invocationCounter ushr 24) and 0xFF).toByte(),
            ((invocationCounter ushr 16) and 0xFF).toByte(),
            ((invocationCounter ushr 8) and 0xFF).toByte(),
            (invocationCounter and 0xFF).toByte()
        )
        val iv = systemTitle + counterBytes
        val aad = byteArrayOf(securityControl) + authenticationKey + challenge
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(authenticationKey, "AES"), GCMParameterSpec(128, iv))
        cipher.updateAAD(aad)
        val tag = cipher.doFinal(ByteArray(0))
        return byteArrayOf(securityControl) + counterBytes + tag
    }
}
