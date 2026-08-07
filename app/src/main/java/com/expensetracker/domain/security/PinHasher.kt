package com.expensetracker.domain.security

import java.security.SecureRandom
import java.util.Base64
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * PBKDF2-based PIN hashing so the raw PIN is never stored — only a salted
 * hash, matching how a lightweight offline app-lock should protect its PIN.
 */
object PinHasher {

    private const val ALGORITHM = "PBKDF2WithHmacSHA256"
    private const val ITERATIONS = 120_000
    private const val KEY_LENGTH_BITS = 256
    private const val SALT_BYTES = 16

    fun generateSalt(): String {
        val bytes = ByteArray(SALT_BYTES)
        SecureRandom().nextBytes(bytes)
        return Base64.getEncoder().encodeToString(bytes)
    }

    fun hash(pin: String, saltBase64: String): String {
        val salt = Base64.getDecoder().decode(saltBase64)
        val spec = PBEKeySpec(pin.toCharArray(), salt, ITERATIONS, KEY_LENGTH_BITS)
        val factory = SecretKeyFactory.getInstance(ALGORITHM)
        val derived = factory.generateSecret(spec).encoded
        return Base64.getEncoder().encodeToString(derived)
    }

    fun matches(pin: String, saltBase64: String, expectedHashBase64: String): Boolean {
        val actual = hash(pin, saltBase64)
        return constantTimeEquals(actual, expectedHashBase64)
    }

    /** Avoids leaking hash-comparison timing differences. */
    private fun constantTimeEquals(a: String, b: String): Boolean {
        if (a.length != b.length) return false
        var result = 0
        for (i in a.indices) {
            result = result or (a[i].code xor b[i].code)
        }
        return result == 0
    }
}
