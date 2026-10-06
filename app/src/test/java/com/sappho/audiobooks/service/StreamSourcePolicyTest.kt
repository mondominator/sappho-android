package com.sappho.audiobooks.service

import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test

class StreamSourcePolicyTest {

    private lateinit var policy: StreamSourcePolicy

    @Before
    fun setUp() {
        policy = StreamSourcePolicy(maxMasterReloads = 2)
    }

    private fun chooseFor(
        id: Int = BOOK,
        local: Boolean = false,
        metered: Boolean = false,
        dataSaver: Boolean = false
    ) = policy.choose(id, hasLocalFile = local, isMeteredNetwork = metered, dataSaver = dataSaver)

    // --- choosing a source ---

    @Test
    fun `unmetered network without data saver keeps progressive`() {
        assertThat(chooseFor()).isEqualTo(StreamPlan(StreamMode.PROGRESSIVE))
    }

    @Test
    fun `metered network uses HLS starting with the source variant`() {
        assertThat(chooseFor(metered = true)).isEqualTo(StreamPlan(StreamMode.HLS, preferLow = false))
    }

    @Test
    fun `data saver uses HLS preferring the low variant on any network`() {
        assertThat(chooseFor(dataSaver = true)).isEqualTo(StreamPlan(StreamMode.HLS, preferLow = true))
        assertThat(chooseFor(metered = true, dataSaver = true))
            .isEqualTo(StreamPlan(StreamMode.HLS, preferLow = true))
    }

    @Test
    fun `downloaded book always plays locally`() {
        assertThat(chooseFor(local = true, metered = true, dataSaver = true))
            .isEqualTo(StreamPlan(StreamMode.LOCAL))
    }

    // --- recovering from errors ---

    @Test
    fun `415 falls back to progressive and later plays of that book skip HLS`() {
        val recovery = policy.recoveryFor(BOOK, StreamMode.HLS, HttpFailure(415, "HLS_UNSUPPORTED"))

        assertThat(recovery).isEqualTo(StreamRecovery.FALL_BACK_TO_PROGRESSIVE)
        assertThat(chooseFor(metered = true).mode).isEqualTo(StreamMode.PROGRESSIVE)
        assertThat(chooseFor(id = OTHER_BOOK, metered = true).mode).isEqualTo(StreamMode.HLS)
    }

    @Test
    fun `404 FILE_VERSION_CHANGED reloads the master playlist`() {
        chooseFor(metered = true)

        val recovery = policy.recoveryFor(BOOK, StreamMode.HLS, HttpFailure(404, "FILE_VERSION_CHANGED"))

        assertThat(recovery).isEqualTo(StreamRecovery.RELOAD_HLS)
    }

    @Test
    fun `repeated FILE_VERSION_CHANGED stops reloading and falls back to progressive`() {
        chooseFor(metered = true)
        val changed = HttpFailure(404, "FILE_VERSION_CHANGED")

        val recoveries = List(3) { policy.recoveryFor(BOOK, StreamMode.HLS, changed) }

        assertThat(recoveries).containsExactly(
            StreamRecovery.RELOAD_HLS,
            StreamRecovery.RELOAD_HLS,
            StreamRecovery.FALL_BACK_TO_PROGRESSIVE
        ).inOrder()
    }

    @Test
    fun `a new playback gets a fresh reload budget`() {
        chooseFor(metered = true)
        val changed = HttpFailure(404, "FILE_VERSION_CHANGED")
        repeat(2) { policy.recoveryFor(BOOK, StreamMode.HLS, changed) }

        chooseFor(metered = true)

        assertThat(policy.recoveryFor(BOOK, StreamMode.HLS, changed)).isEqualTo(StreamRecovery.RELOAD_HLS)
    }

    @Test
    fun `plain 404 and other HLS errors fall back to progressive`() {
        assertThat(policy.recoveryFor(BOOK, StreamMode.HLS, HttpFailure(404, "VARIANT_NOT_READY")))
            .isEqualTo(StreamRecovery.FALL_BACK_TO_PROGRESSIVE)
        assertThat(policy.recoveryFor(BOOK, StreamMode.HLS, HttpFailure(500, null)))
            .isEqualTo(StreamRecovery.FALL_BACK_TO_PROGRESSIVE)
        assertThat(policy.recoveryFor(BOOK, StreamMode.HLS, null))
            .isEqualTo(StreamRecovery.FALL_BACK_TO_PROGRESSIVE)
    }

    @Test
    fun `a generic HLS error does not demote the book for later plays`() {
        policy.recoveryFor(BOOK, StreamMode.HLS, HttpFailure(500, null))

        assertThat(chooseFor(metered = true).mode).isEqualTo(StreamMode.HLS)
    }

    @Test
    fun `progressive and local errors are surfaced, not recovered`() {
        assertThat(policy.recoveryFor(BOOK, StreamMode.PROGRESSIVE, HttpFailure(415, null)))
            .isEqualTo(StreamRecovery.NONE)
        assertThat(policy.recoveryFor(BOOK, StreamMode.PROGRESSIVE, HttpFailure(404, "FILE_VERSION_CHANGED")))
            .isEqualTo(StreamRecovery.NONE)
        assertThat(policy.recoveryFor(BOOK, StreamMode.LOCAL, null)).isEqualTo(StreamRecovery.NONE)
    }

    // --- URLs ---

    @Test
    fun `stream URLs match the server routes`() {
        val server = "https://sappho.example.com/sub"

        assertThat(StreamUrls.progressive(server, 42))
            .isEqualTo("https://sappho.example.com/sub/api/audiobooks/42/stream")
        assertThat(StreamUrls.hlsMaster(server, 42, preferLow = false))
            .isEqualTo("https://sappho.example.com/sub/api/audiobooks/42/hls/master.m3u8")
        assertThat(StreamUrls.hlsMaster(server, 42, preferLow = true))
            .isEqualTo("https://sappho.example.com/sub/api/audiobooks/42/hls/master.m3u8?prefer=low")
    }

    private companion object {
        const val BOOK = 7
        const val OTHER_BOOK = 8
    }
}
