package dev.aprax.tmaii.backend.security

import java.security.MessageDigest
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * AES-256-GCM encryption for provider mailbox tokens stored at rest.
 * Key is derived from TOKEN_ENCRYPTION_KEY via SHA-256 so any length works.
 */
class TokenCipher(keyMaterial: String) {
    private val key: ByteArray = MessageDigest.getInstance("SHA-256")
        .digest(keyMaterial.toByteArray(Charsets.UTF_8))

    fun encrypt(plain: String): String {
        val iv = ByteArray(12).also { java.security.SecureRandom().nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, iv))
        val ciphertext = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        val out = ByteArray(iv.size + ciphertext.size)
        System.arraycopy(iv, 0, out, 0, iv.size)
        System.arraycopy(ciphertext, 0, out, iv.size, ciphertext.size)
        return Base64.getEncoder().encodeToString(out)
    }

    fun decrypt(encoded: String): String {
        val raw = Base64.getDecoder().decode(encoded)
        val iv = raw.copyOfRange(0, 12)
        val ciphertext = raw.copyOfRange(12, raw.size)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, iv))
        return String(cipher.doFinal(ciphertext), Charsets.UTF_8)
    }
}
