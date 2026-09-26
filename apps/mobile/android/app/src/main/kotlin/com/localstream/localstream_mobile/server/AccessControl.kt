package com.localstream.localstream_mobile.server

import android.content.Context
import android.content.SharedPreferences
import java.security.SecureRandom
import java.util.Base64
import java.util.concurrent.ConcurrentHashMap

/**
 * Optional PIN gate for the web client and API.
 * A valid PIN is required before any non-auth API/media request when a PIN is set.
 * Verified clients receive a cryptographically random session token (cookie / header).
 */
class AccessControl(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    // token -> millisUntilExpiry
    private val tokens = ConcurrentHashMap<String, Long>()

    companion object {
        private const val PREFS_NAME = "localstream_access"
        private const val KEY_PIN = "web_pin"
        private const val KEY_PIN_SALT = "web_pin_salt"
        private const val TOKEN_TTL_MS = 30L * 24 * 60 * 60 * 1000 // 30 days
        private val TOKEN_CHARS = ('a'..'z') + ('A'..'Z') + ('0'..'9') + "_-"

        fun normalizePin(raw: String): String = raw.trim()

        /** Cookie header value for the session token. */
        fun buildCookie(token: String): String = "ls_token=$token; Path=/; SameSite=Lax; HttpOnly"

        /** Cookie header that immediately expires the session. */
        fun buildClearCookie(): String = "ls_token=; Path=/; Max-Age=0; SameSite=Lax; HttpOnly"
    }

    private val storedPin: String?
        get() = prefs.getString(KEY_PIN, null)?.takeIf { it.isNotBlank() }

    val pinRequired: Boolean
        get() = prefs.getString(KEY_PIN, null)?.isNotBlank() == true

    /**
     * Sets (or replaces) the required PIN. Empty/blank disables the PIN requirement entirely.
     */
    fun setPin(rawPin: String) {
        val pin = rawPin.trim()
        if (pin.isEmpty()) {
            clearPin()
            return
        }
        val salt = randomToken(16)
        prefs.edit()
            .putString(KEY_PIN_SALT, salt)
            .putString(KEY_PIN, hashPin(pin, salt))
            .apply()
        tokens.clear()
    }

    fun clearPin() {
        prefs.edit().remove(KEY_PIN).remove(KEY_PIN_SALT).apply()
        tokens.clear()
    }

    /**
     * Validates a plaintext PIN against the stored hash.
     */
    fun verifyPin(rawPin: String): Boolean {
        val salt = prefs.getString(KEY_PIN_SALT, null) ?: return false
        val expected = storedPin ?: return false
        if (expected.isBlank()) return false
        return constantTimeEquals(expected, hashPin(rawPin.trim(), salt))
    }

    /**
     * Issues a fresh session token for a verified client.
     */
    fun issueToken(): String {
        val token = randomToken(32)
        tokens[token] = System.currentTimeMillis() + TOKEN_TTL_MS
        return token
    }

    /**
     * Returns true when the presented token is currently valid.
     */
    fun isValidToken(token: String?): Boolean {
        if (token.isNullOrBlank()) return false
        val expiry = tokens[token] ?: return false
        if (System.currentTimeMillis() > expiry) {
            tokens.remove(token)
            return false
        }
        return true
    }

    fun invalidateToken(token: String?) {
        if (token.isNullOrBlank()) return
        tokens.remove(token)
    }

    private fun hashPin(pin: String, salt: String): String {
        return sha256Hex("$salt:$pin")
    }

    private fun sha256Hex(input: String): String {
        val digest = java.security.MessageDigest.getInstance("SHA-256")
            .digest(input.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }

    private fun constantTimeEquals(a: String, b: String): Boolean {
        if (a.length != b.length) return false
        var diff = 0
        for (i in a.indices) {
            diff = diff or (a.codePointAt(i) xor b.codePointAt(i))
        }
        return diff == 0
    }

    private fun randomToken(length: Int): String {
        val rnd = SecureRandom()
        val sb = StringBuilder(length)
        repeat(length) {
            sb.append(TOKEN_CHARS[rnd.nextInt(TOKEN_CHARS.size)])
        }
        return sb.toString()
    }
}