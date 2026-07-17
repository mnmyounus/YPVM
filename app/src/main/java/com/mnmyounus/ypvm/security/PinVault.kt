package com.mnmyounus.ypvm.security

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * Stores only a salted PBKDF2 hash of each PIN, inside
 * EncryptedSharedPreferences — itself backed by an AES-256 key that never
 * leaves the Android Keystore's hardware-backed StrongBox/TEE where the
 * device supports it. The raw PIN is never written to disk.
 *
 * Also tracks failed attempts per role with exponential backoff, since a
 * short numeric PIN is only as strong as its rate limit.
 */
class PinVault(context: Context) {

    enum class PinRole(val key: String) { HOST("host"), GUEST("guest") }

    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val prefs = EncryptedSharedPreferences.create(
        context,
        "ypvm_pin_vault",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    fun setPin(role: PinRole, pin: CharArray) {
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val hash = deriveHash(pin, salt)
        prefs.edit()
            .putString("${role.key}_salt", salt.toBase64())
            .putString("${role.key}_hash", hash.toBase64())
            .apply()
        pin.fill('0')
    }

    fun verify(role: PinRole, pin: CharArray): Boolean {
        val salt = prefs.getString("${role.key}_salt", null)?.fromBase64()
        val storedHash = prefs.getString("${role.key}_hash", null)
        if (salt == null || storedHash == null) {
            pin.fill('0')
            return false
        }
        val candidateHash = deriveHash(pin, salt).toBase64()
        pin.fill('0')
        return constantTimeEquals(candidateHash, storedHash)
    }

    fun isConfigured(role: PinRole): Boolean = prefs.contains("${role.key}_hash")

    fun recordFailedAttempt(role: PinRole): Int {
        val count = prefs.getInt("${role.key}_fail_count", 0) + 1
        prefs.edit()
            .putInt("${role.key}_fail_count", count)
            .putLong("${role.key}_last_fail", System.currentTimeMillis())
            .apply()
        return count
    }

    fun clearFailedAttempts(role: PinRole) {
        prefs.edit()
            .remove("${role.key}_fail_count")
            .remove("${role.key}_last_fail")
            .apply()
    }

    /** Exponential backoff starting after the 5th consecutive failure,
     *  capped at 15 minutes. */
    fun lockoutRemainingMs(role: PinRole): Long {
        val count = prefs.getInt("${role.key}_fail_count", 0)
        if (count < 5) return 0
        val lastFail = prefs.getLong("${role.key}_last_fail", 0)
        val backoffMs = (30_000L * (1L shl (count - 5))).coerceAtMost(15 * 60_000L)
        return ((lastFail + backoffMs) - System.currentTimeMillis()).coerceAtLeast(0)
    }

    private fun deriveHash(pin: CharArray, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(pin, salt, 120_000, 256)
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        return factory.generateSecret(spec).encoded
    }

    private fun constantTimeEquals(a: String, b: String): Boolean =
        java.security.MessageDigest.isEqual(a.toByteArray(), b.toByteArray())

    private fun ByteArray.toBase64(): String =
        android.util.Base64.encodeToString(this, android.util.Base64.NO_WRAP)

    private fun String.fromBase64(): ByteArray =
        android.util.Base64.decode(this, android.util.Base64.NO_WRAP)
}
