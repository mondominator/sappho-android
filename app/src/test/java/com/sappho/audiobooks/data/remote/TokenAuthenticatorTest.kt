package com.sappho.audiobooks.data.remote

import com.google.common.truth.Truth.assertThat
import com.google.gson.Gson
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
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

/**
 * End-to-end tests for [TokenAuthenticator] using MockWebServer. A real
 * OkHttpClient (with a simple auth-header interceptor + the authenticator) and
 * a real Retrofit refresh API both point at the same MockWebServer instance.
 */
class TokenAuthenticatorTest {

    private lateinit var mockWebServer: MockWebServer
    private lateinit var authRepository: AuthRepository
    private lateinit var refreshApi: SapphoApi
    private lateinit var client: OkHttpClient
    private val gson = Gson()

    @Before
    fun setup() {
        mockWebServer = MockWebServer()
        authRepository = mockk(relaxed = true)

        // Refresh API on the same server, no auth header (matches production wiring).
        refreshApi = Retrofit.Builder()
            .baseUrl(mockWebServer.url("/"))
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
            .create(SapphoApi::class.java)

        // Main client: injects the current access token then delegates 401s to the authenticator.
        client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val builder = chain.request().newBuilder()
                authRepository.getTokenSync()?.let { token ->
                    builder.header("Authorization", "Bearer $token")
                }
                chain.proceed(builder.build())
            }
            .authenticator(TokenAuthenticator(authRepository, refreshApi))
            .build()
    }

    @After
    fun tearDown() {
        mockWebServer.shutdown()
    }

    private fun protectedRequest(): Request =
        Request.Builder().url(mockWebServer.url("/api/audiobooks")).build()

    @Test
    fun `refreshes token and retries request on 401 then succeeds`() {
        // Given - a stored access token and refresh token
        every { authRepository.getTokenSync() } returns "stale-access"
        every { authRepository.getRefreshTokenSync() } returns "stored-refresh"

        // 401 for the protected call, 200 for the refresh (rotated tokens), 200 for the retry
        mockWebServer.enqueue(MockResponse().setResponseCode(401))
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"token":"fresh-access","refreshToken":"refresh-rotated"}""")
        )
        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody("[]"))

        // When
        val response = client.newCall(protectedRequest()).execute()

        // Then - the final response succeeds and tokens were rotated
        assertThat(response.isSuccessful).isTrue()
        assertThat(response.code).isEqualTo(200)
        verify { authRepository.saveTokens("fresh-access", "refresh-rotated") }

        // Verify the refresh request carried the camelCase refreshToken in the body
        mockWebServer.takeRequest() // initial protected (401)
        val refreshRequest = mockWebServer.takeRequest()
        assertThat(refreshRequest.path).isEqualTo("/api/auth/refresh")
        val refreshBody = refreshRequest.body.readUtf8()
        assertThat(refreshBody).contains("\"refreshToken\":\"stored-refresh\"")

        // Verify the retry used the fresh access token
        val retryRequest = mockWebServer.takeRequest()
        assertThat(retryRequest.getHeader("Authorization")).isEqualTo("Bearer fresh-access")

        response.close()
    }

    @Test
    fun `clears tokens and gives up when refresh fails`() {
        // Given
        every { authRepository.getTokenSync() } returns "stale-access"
        every { authRepository.getRefreshTokenSync() } returns "stored-refresh"

        // 401 for the protected call, 401 for the refresh
        mockWebServer.enqueue(MockResponse().setResponseCode(401))
        mockWebServer.enqueue(MockResponse().setResponseCode(401))

        // When
        val response = client.newCall(protectedRequest()).execute()

        // Then - authenticator gives up, final response is 401, tokens cleared
        assertThat(response.code).isEqualTo(401)
        verify { authRepository.clearToken() }
        verify(exactly = 0) { authRepository.saveTokens(any(), any()) }

        response.close()
    }

    @Test
    fun `does not attempt refresh when no refresh token stored`() {
        // Given - access token present but no refresh token
        every { authRepository.getTokenSync() } returns "stale-access"
        every { authRepository.getRefreshTokenSync() } returns null

        mockWebServer.enqueue(MockResponse().setResponseCode(401))

        // When
        val response = client.newCall(protectedRequest()).execute()

        // Then - the call fails with 401 and no refresh request was made
        assertThat(response.code).isEqualTo(401)
        assertThat(mockWebServer.requestCount).isEqualTo(1)
        verify(exactly = 0) { authRepository.saveTokens(any(), any()) }

        response.close()
    }
}
