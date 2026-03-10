package com.sappho.audiobooks.data.remote

import com.google.common.truth.Truth.assertThat
import com.google.gson.Gson
import com.sappho.audiobooks.data.repository.AuthRepository
import com.sappho.audiobooks.di.NetworkModule
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class ReadingListApiTest {

    private lateinit var mockWebServer: MockWebServer
    private lateinit var api: SapphoApi
    private lateinit var authRepository: AuthRepository
    private val gson = Gson()

    @Before
    fun setup() {
        mockWebServer = MockWebServer()
        authRepository = mockk()

        every { authRepository.getServerUrlSync() } returns mockWebServer.url("/").toString()
        every { authRepository.getTokenSync() } returns "test-token"

        val okHttpClient = NetworkModule.provideOkHttpClient(authRepository)
        val retrofit = Retrofit.Builder()
            .baseUrl(mockWebServer.url("/"))
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()

        api = retrofit.create(SapphoApi::class.java)
    }

    @After
    fun tearDown() {
        mockWebServer.shutdown()
    }

    @Test
    fun `getFavorites defaults to sort=custom query parameter`() = runBlocking {
        // Given
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("[]")
        )

        // When
        api.getFavorites()

        // Then
        val recordedRequest = mockWebServer.takeRequest()
        assertThat(recordedRequest.method).isEqualTo("GET")
        assertThat(recordedRequest.path).isEqualTo("/api/audiobooks/favorites?sort=custom")
    }

    @Test
    fun `getFavorites with title sort sends sort=title query parameter`() = runBlocking {
        // Given
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("[]")
        )

        // When
        api.getFavorites("title")

        // Then
        val recordedRequest = mockWebServer.takeRequest()
        assertThat(recordedRequest.method).isEqualTo("GET")
        assertThat(recordedRequest.path).isEqualTo("/api/audiobooks/favorites?sort=title")
    }

    @Test
    fun `reorderFavorites sends PUT with correct body`() = runBlocking {
        // Given
        val request = ReorderFavoritesRequest(order = listOf(3, 1, 2))
        mockWebServer.enqueue(MockResponse().setResponseCode(200))

        // When
        val response = api.reorderFavorites(request)

        // Then
        assertThat(response.isSuccessful).isTrue()

        val recordedRequest = mockWebServer.takeRequest()
        assertThat(recordedRequest.method).isEqualTo("PUT")
        assertThat(recordedRequest.path).isEqualTo("/api/audiobooks/favorites/reorder")
        assertThat(recordedRequest.getHeader("Content-Type")).contains("application/json")

        val body = gson.fromJson(recordedRequest.body.readUtf8(), ReorderFavoritesRequest::class.java)
        assertThat(body.order).containsExactly(3, 1, 2).inOrder()
    }

    @Test
    fun `removeFavorite sends DELETE to correct path`() = runBlocking {
        // Given
        mockWebServer.enqueue(MockResponse().setResponseCode(200))

        // When
        val response = api.removeFavorite(42)

        // Then
        assertThat(response.isSuccessful).isTrue()

        val recordedRequest = mockWebServer.takeRequest()
        assertThat(recordedRequest.method).isEqualTo("DELETE")
        assertThat(recordedRequest.path).isEqualTo("/api/audiobooks/42/favorite")
    }
}
