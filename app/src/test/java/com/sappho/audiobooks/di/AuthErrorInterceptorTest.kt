package com.sappho.audiobooks.di

import com.google.common.truth.Truth.assertThat
import com.sappho.audiobooks.data.repository.AuthRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Before
import org.junit.Test

/**
 * The production auth-error interceptor against a real server: linked-server
 * failures must reach the caller untouched without signing the user out.
 */
class AuthErrorInterceptorTest {

    private lateinit var mockWebServer: MockWebServer
    private lateinit var authRepository: AuthRepository
    private lateinit var client: OkHttpClient

    @Before
    fun setup() {
        mockWebServer = MockWebServer()
        mockWebServer.start()
        authRepository = mockk(relaxed = true)
        every { authRepository.getServerUrlSync() } returns mockWebServer.url("/").toString().trimEnd('/')
        client = OkHttpClient.Builder()
            .addInterceptor(NetworkModule.authErrorInterceptor(authRepository))
            .build()
    }

    @After
    fun tearDown() {
        mockWebServer.shutdown()
    }

    private fun get(status: Int, body: String): Pair<Int, String> {
        mockWebServer.enqueue(
            MockResponse().setResponseCode(status).setHeader("Content-Type", "application/json").setBody(body)
        )
        val request = Request.Builder().url(mockWebServer.url("/api/audiobooks/7/stream")).build()
        return client.newCall(request).execute().use { it.code to it.body!!.string() }
    }

    @Test
    fun `503 REMOTE_UNAVAILABLE does not log out`() {
        val (code, _) = get(503, """{"error":"Linked server unavailable","code":"REMOTE_UNAVAILABLE","source":{"id":4,"name":"Robert"}}""")

        assertThat(code).isEqualTo(503)
        verify(exactly = 0) { authRepository.triggerAuthError() }
        verify(exactly = 0) { authRepository.clearToken() }
    }

    @Test
    fun `502 REMOTE_AUTH_FAILED does not log out`() {
        get(502, """{"error":"Linked server rejected the key","code":"REMOTE_AUTH_FAILED"}""")

        verify(exactly = 0) { authRepository.triggerAuthError() }
        verify(exactly = 0) { authRepository.clearToken() }
    }

    @Test
    fun `other linked-server errors do not log out`() {
        get(502, """{"code":"REMOTE_ERROR"}""")
        get(502, """{"code":"REMOTE_INVALID"}""")
        get(404, """{"code":"REMOTE_BOOK_GONE"}""")
        get(409, """{"code":"REMOTE_BOOK_READ_ONLY"}""")

        verify(exactly = 0) { authRepository.triggerAuthError() }
        verify(exactly = 0) { authRepository.clearToken() }
    }

    @Test
    fun `401 from our server logs out`() {
        get(401, """{"error":"Invalid token"}""")

        verify(exactly = 1) { authRepository.triggerAuthError() }
    }

    @Test
    fun `401 carrying a linked-server code does not log out`() {
        get(401, """{"code":"REMOTE_AUTH_FAILED"}""")

        verify(exactly = 0) { authRepository.triggerAuthError() }
    }

    @Test
    fun `interceptor leaves the error body readable for the caller`() {
        val body = """{"code":"REMOTE_UNAVAILABLE"}"""

        val (_, readBody) = get(401, body)

        assertThat(readBody).isEqualTo(body)
    }
}
