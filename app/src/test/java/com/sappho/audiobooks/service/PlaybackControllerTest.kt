package com.sappho.audiobooks.service

import com.google.common.truth.Truth.assertThat
import com.sappho.audiobooks.cast.CastManager
import com.sappho.audiobooks.data.repository.UserPreferencesRepository
import com.sappho.audiobooks.domain.model.ListeningSession
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PlaybackControllerTest {

    private val castManager = mockk<CastManager>(relaxed = true)
    private val prefs = mockk<UserPreferencesRepository>(relaxed = true)
    private val local = mockk<LocalPlayback>(relaxed = true)
    private lateinit var controller: PlaybackController

    @Before
    fun setup() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        every { prefs.skipForwardSeconds } returns MutableStateFlow(30)
        every { prefs.skipBackwardSeconds } returns MutableStateFlow(15)
        every { castManager.isPlaying } returns MutableStateFlow(false)
        controller = PlaybackController(castManager, prefs).also { it.localPlayback = { local } }
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `history entry seeks to its start position in seconds`() {
        every { castManager.isCasting() } returns false
        val session = ListeningSession(
            id = 1, startedAt = "2026-10-01T10:00:00", stoppedAt = null,
            startPosition = 3_600, endPosition = 3_900, deviceName = null
        )

        controller.seekToHistorySession(session)

        // 1:00:00 is 3600 s — not 3,600,000 (which ran past the end and marked the book finished)
        verify { local.seekTo(3_600L) }
    }

    @Test
    fun `controls drive the local player when not casting`() {
        every { castManager.isCasting() } returns false

        controller.skipForward()
        controller.skipBackward()
        controller.seekTo(100)
        controller.togglePlayPause()

        verify { local.skipForward() }
        verify { local.skipBackward() }
        verify { local.seekTo(100) }
        verify { local.togglePlayPause() }
    }

    @Test
    fun `while casting every control targets the receiver and never the local player`() {
        every { castManager.isCasting() } returns true

        controller.skipForward()
        controller.skipBackward()
        controller.seekTo(100)
        controller.seekToAndPlay(200)
        val handled = controller.togglePlayPause()

        assertThat(handled).isTrue()
        coVerify { castManager.skipBy(30) }
        coVerify { castManager.skipBy(-15) }
        coVerify { castManager.seek(100) }
        coVerify { castManager.seek(200) }
        coVerify { castManager.play() }
        verify(exactly = 0) { local.skipForward() }
        verify(exactly = 0) { local.skipBackward() }
        verify(exactly = 0) { local.seekTo(any()) }
        verify(exactly = 0) { local.seekToAndPlay(any()) }
        verify(exactly = 0) { local.togglePlayPause() }
    }

    @Test
    fun `toggle reports unhandled when there is no player at all`() {
        every { castManager.isCasting() } returns false
        controller.localPlayback = { null }
        assertThat(controller.togglePlayPause()).isFalse()
    }
}
