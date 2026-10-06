package com.sappho.audiobooks.data.repository

import java.util.Base64
import java.util.Locale

/**
 * Identifies "which user on which server" so data that belongs to an account
 * (the offline progress queue, downloads) is never replayed into, or shown to,
 * a different account after logout/login.
 *
 * Format: `<normalised server url>#<user id>`. The user id is read from the
 * access token's JWT payload (`{ id, username, jti }`, server/auth.js), which
 * works offline and needs no extra API call.
 */
object AccountKey {

    fun of(serverUrl: String?, accessToken: String?): String? {
        val server = normaliseServer(serverUrl) ?: return null
        val user = userIdFromJwt(accessToken) ?: return null
        return "$server#$user"
    }

    internal fun normaliseServer(serverUrl: String?): String? {
        val trimmed = serverUrl?.trim()?.trimEnd('/')?.lowercase(Locale.ROOT)
        return trimmed?.ifEmpty { null }
    }

    /**
     * Returns the `id` claim (or `username` when there is no id) from a JWT,
     * or null for anything that isn't a decodable JWT (e.g. `sapho_` API keys).
     */
    internal fun userIdFromJwt(token: String?): String? {
        val parts = token?.split('.') ?: return null
        if (parts.size < 2) return null
        val payload = try {
            String(Base64.getUrlDecoder().decode(padBase64(parts[1])), Charsets.UTF_8)
        } catch (_: IllegalArgumentException) {
            return null
        }
        return claim(payload, "id") ?: claim(payload, "username")
    }

    private fun padBase64(s: String): String {
        val rem = s.length % 4
        return if (rem == 0) s else s + "=".repeat(4 - rem)
    }

    private fun claim(json: String, name: String): String? {
        return try {
            val obj = com.google.gson.JsonParser.parseString(json).asJsonObject
            val el = obj.get(name) ?: return null
            if (el.isJsonNull) return null
            val prim = el.asJsonPrimitive
            when {
                prim.isNumber -> prim.asNumber.toLong().toString()
                else -> prim.asString.ifEmpty { null }
            }
        } catch (_: Exception) {
            null
        }
    }

    /** Short, filesystem-safe directory name for an account. */
    fun directoryName(accountKey: String): String {
        val digest = java.security.MessageDigest.getInstance("SHA-256")
            .digest(accountKey.toByteArray(Charsets.UTF_8))
        return digest.take(8).joinToString("") { "%02x".format(it) }
    }
}
