package com.inktodo.app.core.focus

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

data class FullFocusCredential(
    val saltBase64: String,
    val verifierBase64: String,
)

object FullFocusPinSecurity {
    private val validPin = Regex("""\d{6}""")

    fun isValid(pin: String): Boolean = validPin.matches(pin)

    fun create(pin: String, random: SecureRandom = SecureRandom()): FullFocusCredential {
        require(isValid(pin))
        val salt = ByteArray(SALT_BYTES).also(random::nextBytes)
        return create(pin, salt)
    }

    internal fun create(pin: String, salt: ByteArray): FullFocusCredential {
        require(isValid(pin))
        require(salt.isNotEmpty())
        return FullFocusCredential(
            saltBase64 = Base64.getEncoder().encodeToString(salt),
            verifierBase64 = Base64.getEncoder().encodeToString(derive(pin, salt)),
        )
    }

    fun verify(pin: String, credential: FullFocusCredential): Boolean {
        if (!isValid(pin)) return false
        return runCatching {
            val salt = Base64.getDecoder().decode(credential.saltBase64)
            val expected = Base64.getDecoder().decode(credential.verifierBase64)
            MessageDigest.isEqual(expected, derive(pin, salt))
        }.getOrDefault(false)
    }

    private fun derive(pin: String, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(pin.toCharArray(), salt, ITERATIONS, KEY_BITS)
        return try {
            SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }

    private const val SALT_BYTES = 16
    private const val ITERATIONS = 80_000
    private const val KEY_BITS = 256
}
