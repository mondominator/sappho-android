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
    private lateinit var securePrefs: SharedPreferences
    private lateinit var secureEditor: SharedPreferences.Editor
    private lateinit var plainPrefs: SharedPreferences
    private lateinit var plainEditor: SharedPreferences.Editor
    private lateinit var filesDir: File

    // Alias for tests that don't care which prefs store is used
    private val sharedPreferences get() = securePrefs
    private val editor get() = secureEditor

    @Before
    fun setup() {
        context = mockk(relaxed = true)
        securePrefs = mockk(relaxed = true)
        secureEditor = mockk(relaxed = true)
        plainPrefs = mockk(relaxed = true)
        plainEditor = mockk(relaxed = true)
        filesDir = mockk(relaxed = true)

        every { context.filesDir } returns filesDir
        every { securePrefs.edit() } returns secureEditor
        every { secureEditor.putString(any(), any()) } returns secureEditor
        every { secureEditor.remove(any()) } returns secureEditor
        every { secureEditor.apply() } returns Unit
        every { plainPrefs.edit() } returns plainEditor
        every { plainEditor.putString(any(), any()) } returns plainEditor
        every { plainEditor.putFloat(any(), any()) } returns plainEditor
        every { plainEditor.remove(any()) } returns plainEditor
        every { plainEditor.apply() } returns Unit
        // Migration check: no server URL in plain prefs yet
        every { plainPrefs.getString("server_url", null) } returns null
        // No data in secure prefs to migrate
        every { securePrefs.getString("server_url", null) } returns null

        every { context.getSharedPreferences("sappho_prefs", Context.MODE_PRIVATE) } returns plainPrefs

        mockkStatic(EncryptedSharedPreferences::class)
        every {
            EncryptedSharedPreferences.create(
                any<Context>(),
                any<String>(),
                any(),
                any<EncryptedSharedPreferences.PrefKeyEncryptionScheme>(),
                any<EncryptedSharedPreferences.PrefValueEncryptionScheme>()
            )
        } returns securePrefs

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
    fun `saveServerUrl stores URL in plain preferences`() {
        // Given
        val url = "https://sappho.example.com"

        // When
        repository.saveServerUrl(url)

        // Then
        verify { plainEditor.putString("server_url", url) }
        verify { plainEditor.apply() }
    }

    @Test
    fun `getServerUrlSync returns stored URL`() {
        // Given
        val url = "https://sappho.example.com"
        every { plainPrefs.getString("server_url", null) } returns url

        // When
        val result = repository.getServerUrlSync()

        // Then
        assertThat(result).isEqualTo(url)
    }

    @Test
    fun `getServerUrlSync returns null when no URL stored`() {
        // Given
        every { plainPrefs.getString("server_url", null) } returns null

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
        verify { plainEditor.putString("server_url", "https://sappho.example.com") }
    }

    @Test
    fun `saveServerUrl removes trailing slash from URL`() {
        // Given
        val url = "https://sappho.example.com/"

        // When
        repository.saveServerUrl(url)

        // Then
        verify { plainEditor.putString("server_url", "https://sappho.example.com") }
    }

    @Test
    fun `saveServerUrl removes multiple trailing slashes from URL`() {
        // Given
        val url = "https://sappho.example.com///"

        // When
        repository.saveServerUrl(url)

        // Then - trimEnd('/') removes all trailing slashes
        verify { plainEditor.putString("server_url", "https://sappho.example.com") }
    }

    @Test
    fun `hasServerUrl returns true when URL is stored`() {
        // Given
        every { plainPrefs.getString("server_url", null) } returns "https://sappho.example.com"

        // When
        val result = repository.hasServerUrl()

        // Then
        assertThat(result).isTrue()
    }

    @Test
    fun `hasServerUrl returns false when no URL is stored`() {
        // Given
        every { plainPrefs.getString("server_url", null) } returns null

        // When
        val result = repository.hasServerUrl()

        // Then
        assertThat(result).isFalse()
    }

    @Test
    fun `isAuthenticated is true when both token and server URL exist`() {
        // Given - token in secure prefs, server URL in plain prefs
        every { securePrefs.getString("auth_token", null) } returns "test-token"
        every { plainPrefs.getString("server_url", null) } returns "https://sappho.example.com"

        // When
        val repo = AuthRepository(context)

        // Then
        assertThat(repo.isAuthenticated.value).isTrue()
    }

    @Test
    fun `isAuthenticated is false when token is missing`() {
        // Given
        every { securePrefs.getString("auth_token", null) } returns null
        every { plainPrefs.getString("server_url", null) } returns "https://sappho.example.com"

        // When
        val repo = AuthRepository(context)

        // Then
        assertThat(repo.isAuthenticated.value).isFalse()
    }

    @Test
    fun `isAuthenticated is false when server URL is missing`() {
        // Given
        every { securePrefs.getString("auth_token", null) } returns "test-token"
        every { plainPrefs.getString("server_url", null) } returns null

        // When
        val repo = AuthRepository(context)

        // Then
        assertThat(repo.isAuthenticated.value).isFalse()
    }

    @Test
    fun `clearToken sets isAuthenticated to false`() {
        // Given
        every { securePrefs.getString("auth_token", null) } returns "test-token"
        every { plainPrefs.getString("server_url", null) } returns "https://sappho.example.com"
        val repo = AuthRepository(context)
        assertThat(repo.isAuthenticated.value).isTrue()

        // When
        every { securePrefs.getString("auth_token", null) } returns null
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
    fun `saveUserInfo stores username, display name, and avatar in plain prefs`() {
        // When
        repository.saveUserInfo("testuser", "Test User", "avatar_hash")

        // Then
        verify { plainEditor.putString("cached_username", "testuser") }
        verify { plainEditor.putString("cached_display_name", "Test User") }
        verify { plainEditor.putString("cached_avatar", "avatar_hash") }
        verify { plainEditor.apply() }
    }

    @Test
    fun `getCachedUsername returns stored username`() {
        // Given
        every { plainPrefs.getString("cached_username", null) } returns "testuser"

        // When
        val result = repository.getCachedUsername()

        // Then
        assertThat(result).isEqualTo("testuser")
    }

    @Test
    fun `getCachedUsername returns null when no username stored`() {
        // Given
        every { plainPrefs.getString("cached_username", null) } returns null

        // When
        val result = repository.getCachedUsername()

        // Then
        assertThat(result).isNull()
    }

    @Test
    fun `getCachedDisplayName returns stored display name`() {
        // Given
        every { plainPrefs.getString("cached_display_name", null) } returns "Test User"

        // When
        val result = repository.getCachedDisplayName()

        // Then
        assertThat(result).isEqualTo("Test User")
    }

    @Test
    fun `getCachedAvatar returns stored avatar hash`() {
        // Given
        every { plainPrefs.getString("cached_avatar", null) } returns "avatar_hash"

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
        verify { plainEditor.remove("cached_username") }
        verify { plainEditor.remove("cached_display_name") }
        verify { plainEditor.remove("cached_avatar") }
        verify { plainEditor.apply() }
    }

    @Test
    fun `savePlaybackSpeed stores speed in plain preferences`() {
        // When
        repository.savePlaybackSpeed(1.5f)

        // Then
        verify { plainEditor.putFloat("playback_speed", 1.5f) }
        verify { plainEditor.apply() }
    }

    @Test
    fun `getPlaybackSpeed returns stored speed`() {
        // Given
        every { plainPrefs.getFloat("playback_speed", 1.0f) } returns 2.0f

        // When
        val result = repository.getPlaybackSpeed()

        // Then
        assertThat(result).isEqualTo(2.0f)
    }

    @Test
    fun `getPlaybackSpeed returns default 1_0 when not set`() {
        // Given
        every { plainPrefs.getFloat("playback_speed", 1.0f) } returns 1.0f

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
