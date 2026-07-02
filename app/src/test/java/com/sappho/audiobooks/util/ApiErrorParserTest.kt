package com.sappho.audiobooks.util

import com.google.common.truth.Truth.assertThat
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Test
import retrofit2.Response

class ApiErrorParserTest {

    private fun errorResponse(body: String): Response<Unit> = Response.error(
        500,
        body.toResponseBody("application/json".toMediaType())
    )

    @Test
    fun `should parse error field from json body`() {
        val result = parseApiErrorMessage(errorResponse("{\"error\":\"Something broke\"}"))

        assertThat(result).isEqualTo("Something broke")
    }

    @Test
    fun `should fall back to message field when error field missing`() {
        val result = parseApiErrorMessage(errorResponse("{\"message\":\"Not found\"}"))

        assertThat(result).isEqualTo("Not found")
    }

    @Test
    fun `should prefer error field over message field`() {
        val result = parseApiErrorMessage(
            errorResponse("{\"error\":\"primary\",\"message\":\"secondary\"}")
        )

        assertThat(result).isEqualTo("primary")
    }

    @Test
    fun `should return null for non-json body`() {
        val result = parseApiErrorMessage(errorResponse("Internal Server Error"))

        assertThat(result).isNull()
    }

    @Test
    fun `should return null for empty body`() {
        val result = parseApiErrorMessage(errorResponse(""))

        assertThat(result).isNull()
    }

    @Test
    fun `should return null when fields are not string primitives`() {
        val result = parseApiErrorMessage(errorResponse("{\"error\":{\"code\":1}}"))

        assertThat(result).isNull()
    }

    @Test
    fun `should return null for successful response without error body`() {
        val result = parseApiErrorMessage(Response.success(Unit))

        assertThat(result).isNull()
    }
}
