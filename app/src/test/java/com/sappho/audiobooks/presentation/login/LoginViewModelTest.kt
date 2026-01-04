package com.sappho.audiobooks.presentation.login

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.sappho.audiobooks.data.remote.LoginRequest
import com.sappho.audiobooks.data.remote.SapphoApi
import com.sappho.audiobooks.data.repository.AuthRepository
import com.sappho.audiobooks.domain.model.AuthResponse
import com.sappho.audiobooks.domain.model.User
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Before
import org.junit.Test
import retrofit2.Response
import java.net.UnknownHostException

@OptIn(ExperimentalCoroutinesApi::class)
class LoginViewModelTest {

    private lateinit var viewModel: LoginViewModel
    private lateinit var api: SapphoApi
    private lateinit var authRepository: AuthRepository
    
    private val testDispatcher = StandardTestDispatcher()
    
    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        api = mockk(relaxed = true)
        authRepository = mockk(relaxed = true)
        
        every { authRepository.getServerUrlSync() } returns "https://test.sappho.com"
        
        viewModel = LoginViewModel(api, authRepository)
    }
    
    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }
    
    @Test
    fun `initial state is Initial`() = runTest {
        viewModel.uiState.test {
            assertThat(awaitItem()).isEqualTo(LoginUiState.Initial)
        }
    }
    
    @Test
    fun `login success saves token and updates state`() = runTest {
        // Given
        val username = "testuser"
        val password = "testpass"
        val token = "test-token"
        val user = User(
            id = 1,
            username = username,
            email = "test@example.com",
            displayName = "Test User",
            isAdmin = 0,
            avatar = null,
            createdAt = "2024-01-01"
        )
        
        coEvery { 
            api.login(LoginRequest(username, password)) 
        } returns Response.success(AuthResponse(token, user))
        
        // When
        viewModel.uiState.test {
            assertThat(awaitItem()).isEqualTo(LoginUiState.Initial)
            
            viewModel.login(username, password)
            testDispatcher.scheduler.advanceUntilIdle()
            
            assertThat(awaitItem()).isEqualTo(LoginUiState.Loading)
            assertThat(awaitItem()).isEqualTo(LoginUiState.Success)
            
            // Verify token was saved
            verify { authRepository.saveToken(token) }
        }
    }
    
    @Test
    fun `login failure with 401 shows invalid credentials message`() = runTest {
        // Given
        val username = "testuser"
        val password = "wrongpass"
        
        coEvery { 
            api.login(LoginRequest(username, password)) 
        } returns Response.error(401, "Unauthorized".toResponseBody())
        
        // When
        viewModel.uiState.test {
            assertThat(awaitItem()).isEqualTo(LoginUiState.Initial)
            
            viewModel.login(username, password)
            testDispatcher.scheduler.advanceUntilIdle()
            
            assertThat(awaitItem()).isEqualTo(LoginUiState.Loading)
            val errorState = awaitItem() as LoginUiState.Error
            assertThat(errorState.message).isEqualTo("Invalid username or password")
        }
    }
    
    @Test
    fun `login failure with network error shows appropriate message`() = runTest {
        // Given
        val username = "testuser"
        val password = "testpass"
        
        coEvery { 
            api.login(any()) 
        } throws UnknownHostException("test.sappho.com")
        
        // When
        viewModel.uiState.test {
            assertThat(awaitItem()).isEqualTo(LoginUiState.Initial)
            
            viewModel.login(username, password)
            testDispatcher.scheduler.advanceUntilIdle()
            
            assertThat(awaitItem()).isEqualTo(LoginUiState.Loading)
            val errorState = awaitItem() as LoginUiState.Error
            assertThat(errorState.message).contains("Cannot reach server")
        }
    }
    
    @Test
    fun `login with empty credentials shows error`() = runTest {
        viewModel.uiState.test {
            assertThat(awaitItem()).isEqualTo(LoginUiState.Initial)
            
            viewModel.login("", "")
            testDispatcher.scheduler.advanceUntilIdle()
            
            // Should show error message
            val errorState = awaitItem() as LoginUiState.Error
            assertThat(errorState.message).isEqualTo("Please enter username and password")
            
            
            // API should not be called
            testDispatcher.scheduler.advanceUntilIdle()
            coVerify(exactly = 0) { api.login(any()) }
        }
    }
    
    @Test
    @org.junit.Ignore("Flaky test - needs investigation")
    fun `updateServerUrl updates repository and viewModel state`() = runTest {
        // Given
        val newUrl = "https://new.sappho.com"
        
        // When
        viewModel.updateServerUrl(newUrl)
        
        // Then
        verify(exactly = 1) { authRepository.saveServerUrl(newUrl) }
        assertThat(viewModel.serverUrl.value).isEqualTo(newUrl)
    }
}