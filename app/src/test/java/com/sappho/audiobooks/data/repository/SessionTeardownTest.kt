package com.sappho.audiobooks.data.repository

import android.content.Context
import com.sappho.audiobooks.data.remote.LogoutRequest
import com.sappho.audiobooks.data.remote.MessageResponse
import com.sappho.audiobooks.data.remote.SapphoApi
import com.sappho.audiobooks.download.DownloadManager
import com.sappho.audiobooks.service.PlaybackController
import com.sappho.audiobooks.service.PlayerState
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import retrofit2.Response
import java.io.IOException

class SessionTeardownTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val context = mockk<Context>(relaxed = true)
    private val api = mockk<SapphoApi>()
    private val auth = mockk<AuthRepository>(relaxed = true)
    private val downloads = mockk<DownloadManager>(relaxed = true)
    private val playerState = mockk<PlayerState>(relaxed = true)
    private val playback = mockk<PlaybackController>(relaxed = true)
    private lateinit var teardown: SessionTeardown

    @Before
    fun setup() {
        every { context.cacheDir } returns tmp.root
        every { auth.getAccountKeySync() } returns "https://s#1"
        every { auth.getRefreshTokenSync() } returns "refresh-123"
        teardown = SessionTeardown(context, api, auth, downloads, playerState, playback)
    }

    @Test
    fun `logout stops playback, revokes server-side, then clears queue and tokens`() = runTest {
        coEvery { api.logout(any()) } returns Response.success(MessageResponse("ok", null))

        teardown.logout()

        coVerifyOrder {
            playback.stopAllForLogout(true)
            api.logout(LogoutRequest("refresh-123"))
            downloads.clearPendingProgressForAccount("https://s#1")
            auth.clearToken()
        }
        verify { auth.clearUserInfo() }
        verify { downloads.onAccountChanged() }
        // Downloads are kept (scoped to the account), never deleted at logout
        verify(exactly = 0) { downloads.deleteDownload(any()) }
    }

    @Test
    fun `logout still completes locally when the server is unreachable`() = runTest {
        coEvery { api.logout(any()) } throws IOException("offline")

        teardown.logout()

        verify { downloads.clearPendingProgressForAccount("https://s#1") }
        verify { auth.clearToken() }
    }

    @Test
    fun `session expiry keeps the account-scoped queue and does not call the server`() = runTest {
        teardown.onSessionExpired()

        coVerify { playback.stopAllForLogout(false) }
        coVerify(exactly = 0) { api.logout(any()) }
        verify(exactly = 0) { downloads.clearPendingProgressForAccount(any()) }
        verify { auth.clearToken() }
    }
}
