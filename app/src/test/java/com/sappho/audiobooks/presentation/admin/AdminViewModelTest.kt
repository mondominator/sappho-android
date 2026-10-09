package com.sappho.audiobooks.presentation.admin

import com.google.common.truth.Truth.assertThat
import com.sappho.audiobooks.data.remote.CreateUserRequest
import com.sappho.audiobooks.data.remote.SapphoApi
import com.sappho.audiobooks.data.remote.ScanResult
import com.sappho.audiobooks.data.remote.ScanStats
import com.sappho.audiobooks.data.remote.UserInfo
import io.mockk.coEvery
import io.mockk.coVerify
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
class AdminViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var api: SapphoApi

    private val alice = UserInfo(id = 1, username = "alice", email = null, isAdmin = 1, createdAt = null)
    private val bob = UserInfo(id = 2, username = "bob", email = "bob@example.com", isAdmin = 0, createdAt = null)

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        api = mockk(relaxed = true)
        coEvery { api.getUsers() } returns Response.success(listOf(alice, bob))
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel() = AdminViewModel(api)

    @Test
    fun `loads users on init`() = runTest {
        val vm = createViewModel()
        assertThat(vm.isLoadingUsers.value).isTrue()
        testDispatcher.scheduler.advanceUntilIdle()

        assertThat(vm.users.value).containsExactly(alice, bob).inOrder()
        assertThat(vm.isLoadingUsers.value).isFalse()
    }

    @Test
    fun `create user sends username password and admin flag then reloads`() = runTest {
        coEvery { api.createUser(any()) } returns Response.success(bob)
        val vm = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        var result: String? = "unset"
        vm.createUser("bob", "secret", isAdmin = true) { result = it }
        testDispatcher.scheduler.advanceUntilIdle()

        assertThat(result).isNull()
        coVerify { api.createUser(CreateUserRequest("bob", "secret", true)) }
        coVerify(exactly = 2) { api.getUsers() }
    }

    @Test
    fun `create user failure reports an error for the dialog`() = runTest {
        coEvery { api.createUser(any()) } returns Response.error(409, "taken".toResponseBody())
        val vm = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        var result: String? = null
        vm.createUser("bob", "secret", isAdmin = false) { result = it }
        testDispatcher.scheduler.advanceUntilIdle()

        assertThat(result).contains("409")
    }

    @Test
    fun `delete user calls api and reloads`() = runTest {
        coEvery { api.deleteUser(2) } returns Response.success(Unit)
        val vm = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        vm.deleteUser(2)
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify { api.deleteUser(2) }
        coVerify(exactly = 2) { api.getUsers() }
        assertThat(vm.message.value).isNull()
    }

    @Test
    fun `scan reports imported and skipped counts`() = runTest {
        coEvery { api.scanLibraryMaintenance() } returns Response.success(
            ScanResult(message = null, stats = ScanStats(3, 7, 0, null, null, null, null))
        )
        val vm = createViewModel()
        vm.scanLibrary()
        testDispatcher.scheduler.advanceUntilIdle()

        assertThat(vm.scanMessage.value).isEqualTo("Scan complete: 3 imported, 7 skipped")
        assertThat(vm.isScanning.value).isFalse()
    }

    @Test
    fun `force rescan failure is shown inline`() = runTest {
        coEvery { api.forceRescan() } throws RuntimeException("timeout")
        val vm = createViewModel()
        vm.forceRescan()
        testDispatcher.scheduler.advanceUntilIdle()

        assertThat(vm.scanMessage.value).isEqualTo("Rescan failed: timeout")
        assertThat(vm.isScanning.value).isFalse()
    }
}
