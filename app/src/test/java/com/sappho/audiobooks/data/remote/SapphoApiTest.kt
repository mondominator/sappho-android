package com.sappho.audiobooks.data.remote

import com.google.common.truth.Truth.assertThat
import com.google.gson.Gson
import com.sappho.audiobooks.data.repository.AuthRepository
import com.sappho.audiobooks.di.NetworkModule
import com.sappho.audiobooks.domain.model.AuthResponse
import com.sappho.audiobooks.domain.model.Progress
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

class SapphoApiTest {

    private lateinit var mockWebServer: MockWebServer
    private lateinit var api: SapphoApi
    private lateinit var authRepository: AuthRepository
    private val gson = Gson()
    
    @Before
    fun setup() {
        mockWebServer = MockWebServer()
        authRepository = mockk()
        
        // Mock auth repository
        every { authRepository.getServerUrlSync() } returns mockWebServer.url("/").toString()
        every { authRepository.getTokenSync() } returns "test-token"
        
        // Create API with mock server
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
    fun `login request sends correct data`() = runBlocking {
        // Given
        val loginRequest = LoginRequest("testuser", "testpass")
        val authResponse = AuthResponse(
            token = "test-token",
            user = createTestUser()
        )
        
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(gson.toJson(authResponse))
        )
        
        // When
        val response = api.login(loginRequest)
        
        // Then
        assertThat(response.isSuccessful).isTrue()
        assertThat(response.body()?.token).isEqualTo("test-token")
        assertThat(response.body()?.user?.username).isEqualTo("testuser")
        
        // Verify request
        val recordedRequest = mockWebServer.takeRequest()
        assertThat(recordedRequest.method).isEqualTo("POST")
        assertThat(recordedRequest.path).isEqualTo("/api/auth/login")
        assertThat(recordedRequest.getHeader("Content-Type")).contains("application/json")
        
        val requestBody = gson.fromJson(recordedRequest.body.readUtf8(), LoginRequest::class.java)
        assertThat(requestBody.username).isEqualTo("testuser")
        assertThat(requestBody.password).isEqualTo("testpass")
    }
    
    @Test
    fun `getAudiobook includes auth header`() = runBlocking {
        // Given
        val audiobook = createTestAudiobook()
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(gson.toJson(audiobook))
        )
        
        // When
        val response = api.getAudiobook(123)
        
        // Then
        assertThat(response.isSuccessful).isTrue()
        assertThat(response.body()?.id).isEqualTo(123)
        
        // Verify auth header
        val recordedRequest = mockWebServer.takeRequest()
        assertThat(recordedRequest.getHeader("Authorization")).isEqualTo("Bearer test-token")
    }
    
    @Test
    fun `updateProgress sends correct data`() = runBlocking {
        // Given
        val progressRequest = ProgressUpdateRequest(
            position = 1500,
            completed = 0,
            state = "playing"
        )
        
        mockWebServer.enqueue(MockResponse().setResponseCode(200))
        
        // When
        val response = api.updateProgress(123, progressRequest)
        
        // Then
        assertThat(response.isSuccessful).isTrue()
        
        // Verify request
        val recordedRequest = mockWebServer.takeRequest()
        assertThat(recordedRequest.method).isEqualTo("POST")
        assertThat(recordedRequest.path).isEqualTo("/api/audiobooks/123/progress")
        
        val requestBody = gson.fromJson(recordedRequest.body.readUtf8(), ProgressUpdateRequest::class.java)
        assertThat(requestBody.position).isEqualTo(1500)
        assertThat(requestBody.state).isEqualTo("playing")
    }
    
    @Test
    fun `getInProgress handles empty response`() = runBlocking {
        // Given
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("[]")
        )
        
        // When
        val response = api.getInProgress(10)
        
        // Then
        assertThat(response.isSuccessful).isTrue()
        assertThat(response.body()).isEmpty()
    }
    
    private fun createTestUser() = com.sappho.audiobooks.domain.model.User(
        id = 1,
        username = "testuser",
        email = "test@example.com",
        displayName = "Test User",
        isAdmin = 0,
        avatar = null,
        createdAt = "2024-01-01"
    )
    
    private fun createTestAudiobook() = com.sappho.audiobooks.domain.model.Audiobook(
        id = 123,
        title = "Test Book",
        subtitle = null,
        author = "Test Author",
        narrator = "Test Narrator",
        series = null,
        seriesPosition = null,
        duration = 3600,
        genre = "Fiction",
        tags = null,
        publishYear = 2024,
        copyrightYear = null,
        publisher = null,
        isbn = null,
        asin = null,
        language = "en",
        rating = null,
        userRating = null,
        averageRating = null,
        abridged = 0,
        description = "Test description",
        coverImage = null,
        fileCount = 1,
        isMultiFile = 0,
        createdAt = "2024-01-01",
        progress = null,
        chapters = null,
        isFavorite = false
    )
}