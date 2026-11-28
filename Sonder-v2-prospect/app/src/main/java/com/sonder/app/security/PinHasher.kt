package com.sonder.app.security

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

object PinHasher {

    private const val ITERATIONS = 60_000
    private const val KEY_LENGTH_BITS = 256

    fun newSalt(): String {
        val bytes = ByteArray(16)
        SecureRandom().nextBytes(bytes)
        return Base64.getEncoder().encodeToString(bytes)
    }

    fun hash(pin: String, salt: String): String {
        val spec = PBEKeySpec(
            pin.toCharArray(),
            Base64.getDecoder().decode(salt),
            ITERATIONS,
            KEY_LENGTH_BITS
        )
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val hashed = factory.generateSecret(spec).encoded
        return Base64.getEncoder().encodeToString(hashed)
    }

    fun verify(pin: String, salt: String?, expectedHash: String?): Boolean {
        if (salt.isNullOrBlank() || expectedHash.isNullOrBlank()) return false
        val actual = runCatching { hash(pin, salt) }.getOrNull() ?: return false
        return MessageDigest.isEqual(
            actual.toByteArray(Charsets.UTF_8),
            expectedHash.toByteArray(Charsets.UTF_8)
        )
    }
}
