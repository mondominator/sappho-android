package com.sappho.audiobooks.data.repository

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import com.google.common.truth.Truth.assertThat
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkConstructor
import io.mockk.mockkStatic
import io.mockk.verify
import org.junit.Before
import org.junit.Test
import java.io.File

class AuthRepositoryTest {

    private lateinit var repository: AuthRepository
    private lateinit var context: Context
    private lateinit var sharedPreferences: SharedPreferences
    private lateinit var editor: SharedPreferences.Editor
    private lateinit var filesDir: File
    
    @Before
    fun setup() {
        context = mockk(relaxed = true)
        sharedPreferences = mockk(relaxed = true)
        editor = mockk(relaxed = true)
        filesDir = mockk(relaxed = true)
        
        every { context.filesDir } returns filesDir
        every { sharedPreferences.edit() } returns editor
        every { editor.putString(any(), any()) } returns editor
        every { editor.remove(any()) } returns editor
        every { editor.apply() } returns Unit
        
        mockkStatic(EncryptedSharedPreferences::class)
        every {
            EncryptedSharedPreferences.create(
                any<Context>(),
                any<String>(),
                any(),
                any<EncryptedSharedPreferences.PrefKeyEncryptionScheme>(),
                any<EncryptedSharedPreferences.PrefValueEncryptionScheme>()
            )
        } returns sharedPreferences
        
        repository = AuthRepository(context)
    }
    
    @Test
    fun `saveToken stores token in encrypted preferences`() {
        // Given
        val token = "test-token-123"
        
        // When
        repository.saveToken(token)
        
        // Then
        verify { editor.putString("auth_token", token) }
        verify { editor.apply() }
    }
    
    @Test
    fun `getTokenSync returns stored token`() {
        // Given
        val token = "test-token-123"
        every { sharedPreferences.getString("auth_token", null) } returns token
        
        // When
        val result = repository.getTokenSync()
        
        // Then
        assertThat(result).isEqualTo(token)
    }
    
    @Test
    fun `getTokenSync returns null when no token stored`() {
        // Given
        every { sharedPreferences.getString("auth_token", null) } returns null
        
        // When
        val result = repository.getTokenSync()
        
        // Then
        assertThat(result).isNull()
    }
    
    @Test
    fun `saveServerUrl stores URL in preferences`() {
        // Given
        val url = "https://sappho.example.com"
        
        // When
        repository.saveServerUrl(url)
        
        // Then
        verify { editor.putString("server_url", url) }
        verify { editor.apply() }
    }
    
    @Test
    fun `getServerUrlSync returns stored URL`() {
        // Given
        val url = "https://sappho.example.com"
        every { sharedPreferences.getString("server_url", null) } returns url
        
        // When
        val result = repository.getServerUrlSync()
        
        // Then
        assertThat(result).isEqualTo(url)
    }
    
    @Test
    fun `getServerUrlSync returns null when no URL stored`() {
        // Given
        every { sharedPreferences.getString("server_url", null) } returns null
        
        // When
        val result = repository.getServerUrlSync()
        
        // Then
        assertThat(result).isNull()
    }
    
    @Test
    fun `clearToken removes token`() {
        // When
        repository.clearToken()

        // Then
        verify { editor.remove("auth_token") }
        verify { editor.apply() }
    }

    @Test
    fun `saveServerUrl trims whitespace from URL`() {
        // Given
        val url = "  https://sappho.example.com  "

        // When
        repository.saveServerUrl(url)

        // Then
        verify { editor.putString("server_url", "https://sappho.example.com") }
    }

    @Test
    fun `saveServerUrl removes trailing slash from URL`() {
        // Given
        val url = "https://sappho.example.com/"

        // When
        repository.saveServerUrl(url)

        // Then
        verify { editor.putString("server_url", "https://sappho.example.com") }
    }

    @Test
    fun `saveServerUrl removes multiple trailing slashes from URL`() {
        // Given
        val url = "https://sappho.example.com///"

        // When
        repository.saveServerUrl(url)

        // Then - trimEnd('/') removes all trailing slashes
        verify { editor.putString("server_url", "https://sappho.example.com") }
    }

    @Test
    fun `hasServerUrl returns true when URL is stored`() {
        // Given
        every { sharedPreferences.getString("server_url", null) } returns "https://sappho.example.com"

        // When
        val result = repository.hasServerUrl()

        // Then
        assertThat(result).isTrue()
    }

    @Test
    fun `hasServerUrl returns false when no URL is stored`() {
        // Given
        every { sharedPreferences.getString("server_url", null) } returns null

        // When
        val result = repository.hasServerUrl()

        // Then
        assertThat(result).isFalse()
    }

    @Test
    fun `isAuthenticated is true when both token and server URL exist`() {
        // Given
        every { sharedPreferences.getString("auth_token", null) } returns "test-token"
        every { sharedPreferences.getString("server_url", null) } returns "https://sappho.example.com"

        // When
        val repo = AuthRepository(context)

        // Then
        assertThat(repo.isAuthenticated.value).isTrue()
    }

    @Test
    fun `isAuthenticated is false when token is missing`() {
        // Given
        every { sharedPreferences.getString("auth_token", null) } returns null
        every { sharedPreferences.getString("server_url", null) } returns "https://sappho.example.com"

        // When
        val repo = AuthRepository(context)

        // Then
        assertThat(repo.isAuthenticated.value).isFalse()
    }

    @Test
    fun `isAuthenticated is false when server URL is missing`() {
        // Given
        every { sharedPreferences.getString("auth_token", null) } returns "test-token"
        every { sharedPreferences.getString("server_url", null) } returns null

        // When
        val repo = AuthRepository(context)

        // Then
        assertThat(repo.isAuthenticated.value).isFalse()
    }

    @Test
    fun `clearToken sets isAuthenticated to false`() {
        // Given
        every { sharedPreferences.getString("auth_token", null) } returns "test-token"
        every { sharedPreferences.getString("server_url", null) } returns "https://sappho.example.com"
        val repo = AuthRepository(context)
        assertThat(repo.isAuthenticated.value).isTrue()

        // When
        every { sharedPreferences.getString("auth_token", null) } returns null
        repo.clearToken()

        // Then
        assertThat(repo.isAuthenticated.value).isFalse()
    }

    @Test
    fun `triggerAuthError sets authError to true`() {
        // When
        repository.triggerAuthError()

        // Then
        assertThat(repository.authError.value).isTrue()
    }

    @Test
    fun `clearAuthError sets authError to false`() {
        // Given
        repository.triggerAuthError()
        assertThat(repository.authError.value).isTrue()

        // When
        repository.clearAuthError()

        // Then
        assertThat(repository.authError.value).isFalse()
    }

    @Test
    fun `saveUserInfo stores username, display name, and avatar`() {
        // When
        repository.saveUserInfo("testuser", "Test User", "avatar_hash")

        // Then
        verify { editor.putString("cached_username", "testuser") }
        verify { editor.putString("cached_display_name", "Test User") }
        verify { editor.putString("cached_avatar", "avatar_hash") }
        verify { editor.apply() }
    }

    @Test
    fun `getCachedUsername returns stored username`() {
        // Given
        every { sharedPreferences.getString("cached_username", null) } returns "testuser"

        // When
        val result = repository.getCachedUsername()

        // Then
        assertThat(result).isEqualTo("testuser")
    }

    @Test
    fun `getCachedUsername returns null when no username stored`() {
        // Given
        every { sharedPreferences.getString("cached_username", null) } returns null

        // When
        val result = repository.getCachedUsername()

        // Then
        assertThat(result).isNull()
    }

    @Test
    fun `getCachedDisplayName returns stored display name`() {
        // Given
        every { sharedPreferences.getString("cached_display_name", null) } returns "Test User"

        // When
        val result = repository.getCachedDisplayName()

        // Then
        assertThat(result).isEqualTo("Test User")
    }

    @Test
    fun `getCachedAvatar returns stored avatar hash`() {
        // Given
        every { sharedPreferences.getString("cached_avatar", null) } returns "avatar_hash"

        // When
        val result = repository.getCachedAvatar()

        // Then
        assertThat(result).isEqualTo("avatar_hash")
    }

    @Test
    fun `clearUserInfo removes all cached user data`() {
        // When
        repository.clearUserInfo()

        // Then
        verify { editor.remove("cached_username") }
        verify { editor.remove("cached_display_name") }
        verify { editor.remove("cached_avatar") }
        verify { editor.apply() }
    }

    @Test
    fun `savePlaybackSpeed stores speed in preferences`() {
        // Given
        every { editor.putFloat(any(), any()) } returns editor

        // When
        repository.savePlaybackSpeed(1.5f)

        // Then
        verify { editor.putFloat("playback_speed", 1.5f) }
        verify { editor.apply() }
    }

    @Test
    fun `getPlaybackSpeed returns stored speed`() {
        // Given
        every { sharedPreferences.getFloat("playback_speed", 1.0f) } returns 2.0f

        // When
        val result = repository.getPlaybackSpeed()

        // Then
        assertThat(result).isEqualTo(2.0f)
    }

    @Test
    fun `getPlaybackSpeed returns default 1_0 when not set`() {
        // Given
        every { sharedPreferences.getFloat("playback_speed", 1.0f) } returns 1.0f

        // When
        val result = repository.getPlaybackSpeed()

        // Then
        assertThat(result).isEqualTo(1.0f)
    }

    @Test
    fun `getCachedAvatarFile returns file in filesDir`() {
        // Given
        val testFilesDir = File("/test/files")
        every { context.filesDir } returns testFilesDir

        // Need to re-create repository after changing mock
        val repo = AuthRepository(context)

        // When
        val result = repo.getCachedAvatarFile()

        // Then
        assertThat(result.name).isEqualTo("cached_avatar.jpg")
        assertThat(result.parent).isEqualTo("/test/files")
    }
}
