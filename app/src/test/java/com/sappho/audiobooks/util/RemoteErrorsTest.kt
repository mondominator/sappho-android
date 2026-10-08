package com.sappho.audiobooks.util

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class RemoteErrorsTest {

    private val allRemoteCodes = listOf(
        "REMOTE_UNAVAILABLE", "REMOTE_AUTH_FAILED", "REMOTE_ERROR",
        "REMOTE_INVALID", "REMOTE_BOOK_GONE", "REMOTE_BOOK_READ_ONLY"
    )

    // --- RemoteErrors ---

    @Test
    fun `every linked-server code is recognised and has a message`() {
        allRemoteCodes.forEach { code ->
            assertThat(RemoteErrors.isRemoteError(code)).isTrue()
            assertThat(RemoteErrors.messageFor(code)).isNotEmpty()
        }
    }

    @Test
    fun `other codes are not linked-server errors`() {
        listOf(null, "", "FILE_VERSION_CHANGED", "MULTI_FILE_NOT_MERGED", "remote_unavailable").forEach { code ->
            assertThat(RemoteErrors.isRemoteError(code)).isFalse()
            assertThat(RemoteErrors.messageFor(code)).isNull()
        }
    }

    @Test
    fun `unavailable message tells the user to retry rather than sign in`() {
        val message = RemoteErrors.messageFor("REMOTE_UNAVAILABLE")!!

        assertThat(message).contains("Try again later")
        assertThat(message.lowercase()).doesNotContain("sign in")
        assertThat(message.lowercase()).doesNotContain("log in")
    }

    // --- parseApiErrorCode ---

    @Test
    fun `reads the code from a JSON error body`() {
        val body = """{"error":"Linked server unavailable","code":"REMOTE_UNAVAILABLE","source":{"id":4,"name":"Robert"}}"""

        assertThat(parseApiErrorCode(body)).isEqualTo("REMOTE_UNAVAILABLE")
    }

    @Test
    fun `no code for empty, non-JSON or code-less bodies`() {
        assertThat(parseApiErrorCode(null)).isNull()
        assertThat(parseApiErrorCode("")).isNull()
        assertThat(parseApiErrorCode("<html>Bad Gateway</html>")).isNull()
        assertThat(parseApiErrorCode("""{"error":"Unauthorized"}""")).isNull()
        assertThat(parseApiErrorCode("""["REMOTE_UNAVAILABLE"]""")).isNull()
        assertThat(parseApiErrorCode("""{"code":{"nested":true}}""")).isNull()
    }

    // --- AuthErrorPolicy: only a real 401 from our own server logs out ---

    private val server = "sappho.example.com"

    @Test
    fun `401 from our own server logs out`() {
        assertThat(AuthErrorPolicy.shouldLogout(401, server, server)).isTrue()
        assertThat(AuthErrorPolicy.shouldLogout(401, "SAPPHO.example.com", server)).isTrue()
    }

    @Test
    fun `401 logs out when no server is configured yet`() {
        assertThat(AuthErrorPolicy.shouldLogout(401, server, serverHost = null)).isTrue()
    }

    @Test
    fun `linked-server error statuses never log out`() {
        val cases = listOf(
            503 to "REMOTE_UNAVAILABLE",
            502 to "REMOTE_AUTH_FAILED",
            502 to "REMOTE_ERROR",
            502 to "REMOTE_INVALID",
            404 to "REMOTE_BOOK_GONE",
            409 to "REMOTE_BOOK_READ_ONLY"
        )
        cases.forEach { (status, code) ->
            assertThat(AuthErrorPolicy.shouldLogout(status, server, server, code)).isFalse()
        }
    }

    @Test
    fun `a 401 carrying a linked-server code does not log out`() {
        allRemoteCodes.forEach { code ->
            assertThat(AuthErrorPolicy.shouldLogout(401, server, server, code)).isFalse()
        }
    }

    @Test
    fun `a 401 from another host does not log out`() {
        assertThat(AuthErrorPolicy.shouldLogout(401, "covers.example.org", server)).isFalse()
    }

    @Test
    fun `non-401 statuses never log out`() {
        listOf(200, 400, 403, 404, 409, 500, 502, 503).forEach { status ->
            assertThat(AuthErrorPolicy.shouldLogout(status, server, server)).isFalse()
        }
    }
}
