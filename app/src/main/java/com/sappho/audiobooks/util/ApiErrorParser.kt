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
