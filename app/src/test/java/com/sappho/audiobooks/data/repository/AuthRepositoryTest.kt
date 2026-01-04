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
    

}