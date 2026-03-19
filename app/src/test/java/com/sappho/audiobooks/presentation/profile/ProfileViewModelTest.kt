package com.sappho.audiobooks.presentation.profile

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.sappho.audiobooks.data.remote.HealthResponse
import com.sappho.audiobooks.data.remote.PasswordUpdateRequest
import com.sappho.audiobooks.data.remote.ProfileUpdateRequest
import com.sappho.audiobooks.data.remote.SapphoApi
import com.sappho.audiobooks.data.repository.AuthRepository
import com.sappho.audiobooks.data.repository.UserPreferencesRepository
import com.sappho.audiobooks.domain.model.User
import com.sappho.audiobooks.domain.model.UserStats
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
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

@OptIn(ExperimentalCoroutinesApi::class)
class ProfileViewModelTest {

    private lateinit var viewModel: ProfileViewModel
    private lateinit var api: SapphoApi
    private lateinit var authRepository: AuthRepository
    private lateinit var userPreferences: UserPreferencesRepository

    private val testDispatcher = StandardTestDispatcher()

    private val testUser = User(
        id = 1,
        username = "testuser",
        email = "test@example.com",
        displayName = "Test User",
        isAdmin = 0,
        avatar = null,
        createdAt = "2024-01-01"
    )

    private val testStats = UserStats(
        totalListenTime = 36000,
        booksStarted = 10,
        booksCompleted = 5,
        currentlyListening = 2,
        topAuthors = emptyList(),
        topGenres = emptyList(),
        recentActivity = emptyList(),
        activeDaysLast30 = 15,
        currentStreak = 3,
        avgSessionLength = 45f
    )

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)

        api = mockk(relaxed = true)
        authRepository = mockk(relaxed = true)
        userPreferences = mockk(relaxed = true)

        every { authRepository.getServerUrlSync() } returns "https://test.sappho.com"

        // Default API responses for init
        coEvery { api.getProfile() } returns Response.success(testUser)
        coEvery { api.getProfileStats() } returns Response.success(testStats)
        coEvery { api.getHealth() } returns Response.success(
            HealthResponse(status = "ok", message = "Healthy", version = "1.2.3")
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(): ProfileViewModel {
        return ProfileViewModel(api, authRepository, userPreferences)
    }

    // --- Initialization Tests ---

    @Test
    fun `should initialize with server URL from auth repository`() = runTest {
        // When
        viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        // Then
        viewModel.serverUrl.test {
            assertThat(awaitItem()).isEqualTo("https://test.sappho.com")
        }
    }

    @Test
    fun `should load user profile on initialization`() = runTest {
        // When
        viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        // Then
        viewModel.user.test {
            assertThat(awaitItem()).isEqualTo(testUser)
        }
    }

    @Test
    fun `should load user stats on initialization`() = runTest {
        // When
        viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        // Then
        viewModel.stats.test {
            val stats = awaitItem()
            assertThat(stats).isNotNull()
            assertThat(stats!!.booksCompleted).isEqualTo(5)
            assertThat(stats.totalListenTime).isEqualTo(36000)
        }
    }

    @Test
    fun `should load server version on initialization`() = runTest {
        // When
        viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        // Then
        viewModel.serverVersion.test {
            assertThat(awaitItem()).isEqualTo("1.2.3")
        }
    }

    @Test
    fun `should handle profile load failure gracefully`() = runTest {
        // Given
        coEvery { api.getProfile() } throws RuntimeException("Network error")

        // When
        viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        // Then - user should be null, no crash
        viewModel.user.test {
            assertThat(awaitItem()).isNull()
        }
        viewModel.isLoading.test {
            assertThat(awaitItem()).isFalse()
        }
    }

    @Test
    fun `should handle stats load failure gracefully`() = runTest {
        // Given
        coEvery { api.getProfileStats() } throws RuntimeException("Network error")

        // When
        viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        // Then
        viewModel.stats.test {
            assertThat(awaitItem()).isNull()
        }
    }

    @Test
    fun `should handle server version load failure gracefully`() = runTest {
        // Given
        coEvery { api.getHealth() } throws RuntimeException("Network error")

        // When
        viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        // Then
        viewModel.serverVersion.test {
            assertThat(awaitItem()).isNull()
        }
    }

    // --- Update Profile Tests ---

    @Test
    fun `should update profile successfully`() = runTest {
        // Given
        viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        val updatedUser = testUser.copy(displayName = "New Name")
        coEvery {
            api.updateProfile(ProfileUpdateRequest("New Name", "new@test.com"))
        } returns Response.success(updatedUser)

        // When
        viewModel.updateProfile("New Name", "new@test.com")
        testDispatcher.scheduler.advanceUntilIdle()

        // Then
        viewModel.user.test {
            assertThat(awaitItem()?.displayName).isEqualTo("New Name")
        }
        viewModel.saveMessage.test {
            assertThat(awaitItem()).isEqualTo("Profile updated successfully")
        }
        viewModel.isSaving.test {
            assertThat(awaitItem()).isFalse()
        }
    }

    @Test
    fun `should handle profile update failure`() = runTest {
        // Given
        viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        coEvery {
            api.updateProfile(any())
        } returns Response.error(500, "Server error".toResponseBody())

        // When
        viewModel.updateProfile("New Name", null)
        testDispatcher.scheduler.advanceUntilIdle()

        // Then
        viewModel.saveMessage.test {
            assertThat(awaitItem()).isEqualTo("Failed to update profile")
        }
    }

    @Test
    fun `should handle profile update network error`() = runTest {
        // Given
        viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        coEvery {
            api.updateProfile(any())
        } throws RuntimeException("Connection failed")

        // When
        viewModel.updateProfile("New Name", null)
        testDispatcher.scheduler.advanceUntilIdle()

        // Then
        viewModel.saveMessage.test {
            assertThat(awaitItem()).isEqualTo("Error: Connection failed")
        }
    }

    // --- Password Update Tests ---

    @Test
    fun `should update password successfully`() = runTest {
        // Given
        viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        coEvery {
            api.updatePassword(PasswordUpdateRequest("oldpass", "newpass"))
        } returns Response.success(Unit)

        // When
        viewModel.updatePassword("oldpass", "newpass")
        testDispatcher.scheduler.advanceUntilIdle()

        // Then
        viewModel.saveMessage.test {
            assertThat(awaitItem()).isEqualTo("Password updated successfully")
        }
    }

    @Test
    fun `should show incorrect password error on 401`() = runTest {
        // Given
        viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        coEvery {
            api.updatePassword(any())
        } returns Response.error(401, "Unauthorized".toResponseBody())

        // When
        viewModel.updatePassword("wrongpass", "newpass")
        testDispatcher.scheduler.advanceUntilIdle()

        // Then
        viewModel.saveMessage.test {
            assertThat(awaitItem()).isEqualTo("Current password is incorrect")
        }
    }

    @Test
    fun `should handle password update network error`() = runTest {
        // Given
        viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        coEvery {
            api.updatePassword(any())
        } throws RuntimeException("Network error")

        // When
        viewModel.updatePassword("oldpass", "newpass")
        testDispatcher.scheduler.advanceUntilIdle()

        // Then
        viewModel.saveMessage.test {
            assertThat(awaitItem()).isEqualTo("Error: Network error")
        }
    }

    // --- Delete Avatar Tests ---

    @Test
    fun `should delete avatar successfully`() = runTest {
        // Given
        viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        coEvery { api.deleteAvatar() } returns Response.success(Unit)
        // Profile reload after deletion
        coEvery { api.getProfile() } returns Response.success(testUser.copy(avatar = null))

        // When
        viewModel.deleteAvatar()
        testDispatcher.scheduler.advanceUntilIdle()

        // Then
        viewModel.saveMessage.test {
            assertThat(awaitItem()).isEqualTo("Avatar removed")
        }
    }

    @Test
    fun `should handle avatar delete failure`() = runTest {
        // Given
        viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        coEvery { api.deleteAvatar() } returns
            Response.error(500, "Server error".toResponseBody())

        // When
        viewModel.deleteAvatar()
        testDispatcher.scheduler.advanceUntilIdle()

        // Then
        viewModel.saveMessage.test {
            assertThat(awaitItem()).isEqualTo("Failed to remove avatar")
        }
    }

    // --- Clear/Refresh Tests ---

    @Test
    fun `clearMessage should set saveMessage to null`() = runTest {
        // Given
        viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        // Set a message first
        coEvery { api.updateProfile(any()) } returns Response.success(testUser)
        viewModel.updateProfile("Name", null)
        testDispatcher.scheduler.advanceUntilIdle()

        // When
        viewModel.clearMessage()

        // Then
        viewModel.saveMessage.test {
            assertThat(awaitItem()).isNull()
        }
    }

    @Test
    fun `clearAvatarUpdatedFlag should reset flag`() = runTest {
        // Given
        viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        // When
        viewModel.clearAvatarUpdatedFlag()

        // Then
        viewModel.avatarUpdated.test {
            assertThat(awaitItem()).isFalse()
        }
    }

    @Test
    fun `refresh should reload profile and stats`() = runTest {
        // Given
        viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        // When
        viewModel.refresh()
        testDispatcher.scheduler.advanceUntilIdle()

        // Then - each should be called twice: once in init, once in refresh
        coVerify(atLeast = 2) { api.getProfile() }
        coVerify(atLeast = 2) { api.getProfileStats() }
    }

    // --- Admin Functions Tests ---

    @Test
    fun `should load users for admin`() = runTest {
        // Given
        viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        val users = listOf(
            com.sappho.audiobooks.data.remote.UserInfo(
                id = 1, username = "admin", email = "admin@test.com",
                isAdmin = 1, createdAt = "2024-01-01"
            ),
            com.sappho.audiobooks.data.remote.UserInfo(
                id = 2, username = "user1", email = "user1@test.com",
                isAdmin = 0, createdAt = "2024-01-01"
            )
        )
        coEvery { api.getUsers() } returns Response.success(users)

        // When
        viewModel.loadUsers()
        testDispatcher.scheduler.advanceUntilIdle()

        // Then
        viewModel.users.test {
            assertThat(awaitItem()).hasSize(2)
        }
    }

    @Test
    fun `should handle load users failure gracefully`() = runTest {
        // Given
        viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        coEvery { api.getUsers() } throws RuntimeException("Forbidden")

        // When
        viewModel.loadUsers()
        testDispatcher.scheduler.advanceUntilIdle()

        // Then - should not crash
        viewModel.users.test {
            assertThat(awaitItem()).isEmpty()
        }
    }

    @Test
    fun `should create user and reload users`() = runTest {
        // Given
        viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        coEvery { api.createUser(any()) } returns Response.success(
            com.sappho.audiobooks.data.remote.UserInfo(
                id = 3, username = "newuser", email = null,
                isAdmin = 0, createdAt = "2024-01-01"
            )
        )

        var resultSuccess = false

        // When
        viewModel.createUser("newuser", "password123", null, false) { success, _ ->
            resultSuccess = success
        }
        testDispatcher.scheduler.advanceUntilIdle()

        // Then
        assertThat(resultSuccess).isTrue()
        coVerify { api.getUsers() }
    }

    @Test
    fun `should delete user and reload users`() = runTest {
        // Given
        viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        coEvery { api.deleteUser(2) } returns Response.success(Unit)

        var resultSuccess = false

        // When
        viewModel.deleteUser(2) { success, _ ->
            resultSuccess = success
        }
        testDispatcher.scheduler.advanceUntilIdle()

        // Then
        assertThat(resultSuccess).isTrue()
        coVerify { api.getUsers() }
    }

    @Test
    fun `should handle delete user failure`() = runTest {
        // Given
        viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        coEvery { api.deleteUser(any()) } returns
            Response.error(403, "Forbidden".toResponseBody())

        var resultSuccess = false
        var resultMessage = ""

        // When
        viewModel.deleteUser(2) { success, message ->
            resultSuccess = success
            resultMessage = message
        }
        testDispatcher.scheduler.advanceUntilIdle()

        // Then
        assertThat(resultSuccess).isFalse()
    }

    // --- Scan Library Tests ---

    @Test
    fun `should scan library successfully`() = runTest {
        // Given
        viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        val scanResult = com.sappho.audiobooks.data.remote.ScanResult(
            message = "Scan complete",
            stats = com.sappho.audiobooks.data.remote.ScanStats(
                imported = 5, skipped = 2, errors = 0,
                scanning = false, metadataRefreshed = null,
                metadataErrors = null, totalFiles = null
            )
        )
        coEvery { api.scanLibrary(any()) } returns Response.success(scanResult)

        // When
        viewModel.scanLibrary(forceRescan = false)
        testDispatcher.scheduler.advanceUntilIdle()

        // Then
        viewModel.scanResult.test {
            val result = awaitItem()
            assertThat(result).isNotNull()
            assertThat(result!!.message).isEqualTo("Scan complete")
        }
        viewModel.isScanning.test {
            assertThat(awaitItem()).isFalse()
        }
    }

    @Test
    fun `clearScanResult should reset scan result`() = runTest {
        // Given
        viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        // When
        viewModel.clearScanResult()

        // Then
        viewModel.scanResult.test {
            assertThat(awaitItem()).isNull()
        }
    }

    @Test
    fun `setAvatarUri should update avatar URI state`() = runTest {
        // Given
        viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        // When
        viewModel.setAvatarUri(null)

        // Then
        viewModel.avatarUri.test {
            assertThat(awaitItem()).isNull()
        }
    }
}
