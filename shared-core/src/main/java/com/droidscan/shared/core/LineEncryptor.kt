package com.droidscan.shared.core

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Transforms a single JSON-line into an opaque stored form and back.
 *
 * [MetricHistoryStore] is line-oriented; plugging in a [LineEncryptor] encrypts
 * each line at rest without changing the append-only/JSONL shape. A null
 * encryptor keeps the store in plaintext (the default, and the JVM-testable
 * path).
 */
interface LineEncryptor {
    /** Obfuscate/encrypt a plaintext line for persistence. */
    fun encrypt(line: String): String

    /** Recover the plaintext line. Implementations must throw on tamper. */
    fun decrypt(line: String): String
}

/**
 * AES-256-GCM line encryptor with the key held in the Android Keystore.
 *
 * - The key is created in the [ANDROID_KEYSTORE] and is never extractable
 *   (`setUnlockedDeviceRequired(false)` so it works on a locked device — we
 *   don't need to gate telemetry history on the device being unlocked).
 * - Each line is encrypted with a fresh random 12-byte IV; the stored form is
 *   `Base64(IV || ciphertext+tag)`. GCM's tag authenticates the IV + data, so
 *   any tamper (bit flip, line swap, truncation) fails decryption and the line
 *   is skipped — the store can never silently present corrupted history.
 * - The `+`/`/`/`=` Base64 alphabet is deliberately distinct from the `{`
 *   JSON prefix, which [MetricHistoryStore] uses to detect legacy plaintext.
 */
class KeystoreAesGcmLineEncryptor(
    private val keyAlias: String = DEFAULT_KEY_ALIAS
) : LineEncryptor {

    @Volatile
    private var key: SecretKey? = null

    private fun getOrCreateKey(): SecretKey {
        key?.let { return it }
        synchronized(this) {
            key?.let { return it }
            val ks = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
            val existing = (ks.getEntry(keyAlias, null) as? KeyStore.SecretKeyEntry)?.secretKey
            val k = existing ?: generateKey()
            key = k
            return k
        }
    }

    private fun generateKey(): SecretKey {
        val kg = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        kg.init(
            KeyGenParameterSpec.Builder(keyAlias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .setUnlockedDeviceRequired(false)
                .setRandomizedEncryptionRequired(true)
                .build()
        )
        return kg.generateKey()
    }

    override fun encrypt(line: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val ct = cipher.doFinal(line.toByteArray(Charsets.UTF_8))
        val iv = cipher.iv // 12 bytes for GCM
        return Base64.encodeToString(iv + ct, Base64.NO_WRAP)
    }

    override fun decrypt(line: String): String {
        val raw = Base64.decode(line, Base64.NO_WRAP)
        if (raw.size < IV_LEN + (TAG_LEN_BITS / 8)) throw IllegalArgumentException("ciphertext too short")
        val iv = raw.copyOfRange(0, IV_LEN)
        val ct = raw.copyOfRange(IV_LEN, raw.size)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(TAG_LEN_BITS, iv))
        return String(cipher.doFinal(ct), Charsets.UTF_8)
    }

    companion object {
        const val DEFAULT_KEY_ALIAS = "droidscan.metrics.history"
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_LEN = 12
        const val TAG_LEN_BITS = 128
    }
}
