package com.sappho.audiobooks.util

import com.google.gson.JsonParser
import retrofit2.Response

/**
 * Extract a human-readable error message from a JSON error body of the form
 * `{"error": "..."}` or `{"message": "..."}`.
 *
 * Returns null if the body is missing, blank, or not parseable as a JSON
 * object with one of those fields.
 */
fun parseApiErrorMessage(response: Response<*>): String? =
    try {
        val body = response.errorBody()?.string()
        if (body.isNullOrBlank()) {
            null
        } else {
            val json = JsonParser.parseString(body).asJsonObject
            json.get("error")?.takeIf { it.isJsonPrimitive }?.asString
                ?: json.get("message")?.takeIf { it.isJsonPrimitive }?.asString
        }
    } catch (e: Exception) {
        null
    }

/**
 * The machine-readable `code` field of a JSON error body such as
 * `{"error": "...", "code": "REMOTE_UNAVAILABLE"}`, or null when the body is
 * missing, not JSON (an HTML error page from a proxy, say) or has no code.
 */
fun parseApiErrorCode(body: String?): String? {
    if (body.isNullOrBlank()) return null
    return try {
        val json = JsonParser.parseString(body)
        if (!json.isJsonObject) return null
        val code = json.asJsonObject.get("code") ?: return null
        if (code.isJsonPrimitive) code.asString else null
    } catch (e: RuntimeException) {
        null
    }
}
