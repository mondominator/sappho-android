package com.sappho.audiobooks.download

import android.content.Context
import com.google.common.truth.Truth.assertThat
import com.sappho.audiobooks.domain.model.Audiobook
import com.sappho.audiobooks.domain.model.Chapter
import com.sappho.audiobooks.domain.model.Progress
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import com.sappho.audiobooks.data.repository.AuthRepository
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class DownloadManagerTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private lateinit var downloadManager: DownloadManager
    private val context = mockk<Context>(relaxed = true)
    private val authRepository = mockk<AuthRepository>(relaxed = true)
    private val testDispatcher = StandardTestDispatcher()
    private var account: String? = "https://server-a#1"

    @Before
    fun setup() {
        every { context.filesDir } returns tmp.root
        every { authRepository.getAccountKeySync() } answers { account }

        downloadManager = DownloadManager(context, authRepository)
    }

    private fun newManager() = DownloadManager(context, authRepository)

    @Test
    fun `should track download state correctly`() = runTest(testDispatcher) {
        // Given
        val audiobookId = 123

        // When
        downloadManager.updateDownloadStateExternal(audiobookId, DownloadState(
            audiobookId = audiobookId,
            progress = 0.5f,
            isDownloading = true,
            isCompleted = false
        ))

        // Then
        val states = downloadManager.downloadStates.value
        assertThat(states[audiobookId]?.progress).isEqualTo(0.5f)
        assertThat(states[audiobookId]?.isDownloading).isTrue()
    }

    @Test
    fun `should clear download error correctly`() = runTest(testDispatcher) {
        // Given
        val audiobookId = 123
        downloadManager.updateDownloadStateExternal(audiobookId, DownloadState(
            audiobookId = audiobookId,
            progress = 0f,
            isDownloading = false,
            isCompleted = false,
            error = "Download failed"
        ))

        // When
        downloadManager.clearDownloadError(audiobookId)

        // Then
        val states = downloadManager.downloadStates.value
        assertThat(states[audiobookId]?.error).isNull()
    }

    @Test
    fun `should clear all download errors correctly`() = runTest(testDispatcher) {
        // Given
        downloadManager.updateDownloadStateExternal(123, DownloadState(
            audiobookId = 123,
            progress = 0f,
            isDownloading = false,
            isCompleted = false,
            error = "Error 1"
        ))
        downloadManager.updateDownloadStateExternal(456, DownloadState(
            audiobookId = 456,
            progress = 0f,
            isDownloading = false,
            isCompleted = false,
            error = "Error 2"
        ))

        // When
        downloadManager.clearAllDownloadErrors()

        // Then
        val states = downloadManager.downloadStates.value
        assertThat(states[123]?.error).isNull()
        assertThat(states[456]?.error).isNull()
    }

    @Test
    fun `should save offline progress correctly`() = runTest(testDispatcher) {
        // Given
        val audiobookId = 123
        val position = 1500

        // When
        downloadManager.saveOfflineProgress(audiobookId, position)

        // Then
        val pendingProgress = downloadManager.pendingProgress.value
        assertThat(pendingProgress[audiobookId]?.position).isEqualTo(position)
        assertThat(downloadManager.getPendingProgressCount()).isEqualTo(1)
    }

    @Test
    fun `should clear pending progress correctly`() = runTest(testDispatcher) {
        // Given
        val audiobookId = 123
        downloadManager.saveOfflineProgress(audiobookId, 1500)

        // When
        downloadManager.clearPendingProgress(audiobookId)

        // Then
        assertThat(downloadManager.getPendingProgressCount()).isEqualTo(0)
    }

    @Test
    fun `should check download status correctly`() {
        // Given
        val audiobook = createSampleAudiobook(123)
        downloadManager.saveDownloadedBook(
            audiobook = audiobook,
            filePath = "/path/to/file.m4b",
            fileSize = 1000000L,
            chapters = emptyList()
        )

        // When & Then
        assertThat(downloadManager.isDownloaded(123)).isTrue()
        assertThat(downloadManager.isDownloaded(456)).isFalse()
        assertThat(downloadManager.getLocalFilePath(123)).isEqualTo("/path/to/file.m4b")
        assertThat(downloadManager.getLocalFilePath(456)).isNull()
    }

    @Test
    fun `should delete download correctly`() = runTest(testDispatcher) {
        // Given
        val audiobook = createSampleAudiobook(123)
        val mockFile = mockk<File> {
            every { exists() } returns true
            every { delete() } returns true
        }
        
        downloadManager.saveDownloadedBook(
            audiobook = audiobook,
            filePath = "/path/to/file.m4b",
            fileSize = 1000000L,
            chapters = emptyList()
        )

        // When
        val result = downloadManager.deleteDownload(123)

        // Then
        assertThat(result).isTrue()
        assertThat(downloadManager.isDownloaded(123)).isFalse()
    }

    // --- Account scoping (queue + downloads tied to server + user) ---

    @Test
    fun `pending progress is tagged with the account and hidden from other accounts`() {
        downloadManager.saveOfflineProgress(7, 1200)
        assertThat(downloadManager.getPendingProgressList().single().accountKey).isEqualTo("https://server-a#1")

        // A different user signs in on the same install
        account = "https://server-a#2"
        downloadManager.onAccountChanged()

        assertThat(downloadManager.getPendingProgressList()).isEmpty()
        assertThat(downloadManager.pendingProgress.value).isEmpty()
    }

    @Test
    fun `pending progress survives a restart and returns when the same account signs back in`() {
        downloadManager.saveOfflineProgress(7, 1200)
        account = null
        downloadManager.onAccountChanged()
        assertThat(downloadManager.getPendingProgressList()).isEmpty()

        account = "https://server-a#1"
        val restarted = newManager()
        assertThat(restarted.getPendingProgressList().map { it.audiobookId to it.position })
            .containsExactly(7 to 1200)
    }

    @Test
    fun `logout clears only the signed-out account's queue`() {
        downloadManager.saveOfflineProgress(7, 1200)
        account = "https://server-b#1"
        downloadManager.onAccountChanged()
        downloadManager.saveOfflineProgress(7, 50)

        downloadManager.clearPendingProgressForAccount("https://server-b#1")
        assertThat(downloadManager.getPendingProgressList()).isEmpty()

        account = "https://server-a#1"
        downloadManager.onAccountChanged()
        assertThat(downloadManager.getPendingProgressList().single().position).isEqualTo(1200)
    }

    @Test
    fun `no account means nothing is queued`() {
        account = null
        downloadManager.onAccountChanged()
        downloadManager.saveOfflineProgress(7, 1200)
        assertThat(downloadManager.getPendingProgressCount()).isEqualTo(0)
    }

    @Test
    fun `clearPendingProgressIfUnchanged keeps a newer position recorded during the sync`() {
        downloadManager.saveOfflineProgress(7, 100)
        val sent = downloadManager.getPendingProgressList().single()
        Thread.sleep(2) // distinct timestamp
        downloadManager.saveOfflineProgress(7, 200) // recorded while the replay was in flight

        downloadManager.clearPendingProgressIfUnchanged(sent)

        assertThat(downloadManager.getPendingProgressList().single().position).isEqualTo(200)
    }

    @Test
    fun `downloads are scoped per account and same book id on two servers does not collide`() {
        val fileA = tmp.newFile("a.m4b")
        downloadManager.saveDownloadedBook(createSampleAudiobook(5), fileA.absolutePath, 1, emptyList())
        assertThat(downloadManager.getLocalFilePath(5)).isEqualTo(fileA.absolutePath)

        account = "https://server-b#1"
        downloadManager.onAccountChanged()
        assertThat(downloadManager.isDownloaded(5)).isFalse()
        assertThat(downloadManager.getLocalFilePath(5)).isNull()

        // Kept on disk, back when account A returns
        account = "https://server-a#1"
        downloadManager.onAccountChanged()
        assertThat(downloadManager.getLocalFilePath(5)).isEqualTo(fileA.absolutePath)
    }

    @Test
    fun `account download dirs differ per account`() {
        val dirA = downloadManager.downloadsDirForCurrentAccount()
        account = "https://server-b#1"
        val dirB = downloadManager.downloadsDirForCurrentAccount()
        assertThat(dirA).isNotEqualTo(dirB)
        assertThat(dirA!!.parentFile!!.name).isEqualTo("audiobooks")
    }

    @Test
    fun `legacy untagged entries are adopted by the signed-in account`() {
        val file = tmp.newFile("legacy.m4b")
        File(tmp.root, "downloads_metadata.json").writeText(
            com.google.gson.Gson().toJson(listOf(mapOf(
                "audiobook" to createSampleAudiobook(9),
                "filePath" to file.absolutePath,
                "fileSize" to 1,
                "downloadedAt" to 1
            )))
        )
        File(tmp.root, "pending_progress.json").writeText(
            """[{"audiobookId":9,"position":321,"timestamp":1}]"""
        )

        val manager = newManager()

        assertThat(manager.getLocalFilePath(9)).isEqualTo(file.absolutePath)
        assertThat(manager.getPendingProgressList().single().accountKey).isEqualTo("https://server-a#1")
    }

    @Test
    fun `stale download is skipped for playback until replaced`() {
        val file = tmp.newFile("b.m4b")
        downloadManager.saveDownloadedBook(createSampleAudiobook(5), file.absolutePath, 1, emptyList())
        downloadManager.markStale(5)
        assertThat(downloadManager.getLocalFilePath(5)).isNull()

        downloadManager.saveDownloadedBook(createSampleAudiobook(5), file.absolutePath, 2, emptyList())
        assertThat(downloadManager.getLocalFilePath(5)).isEqualTo(file.absolutePath)
    }

    @Test
    fun `orphan sweep removes untracked and partial files but keeps tracked ones`() {
        val root = File(tmp.root, "audiobooks/abc").apply { mkdirs() }
        val tracked = File(root, "audiobook_1.m4b").apply { writeText("x") }
        val orphan = File(root, "audiobook_2.m4b").apply { writeText("x") }
        val part = File(root, "audiobook_3.m4b.part").apply { writeText("x") }
        downloadManager.saveDownloadedBook(createSampleAudiobook(1), tracked.absolutePath, 1, emptyList())

        downloadManager.sweepOrphanedFiles()

        assertThat(tracked.exists()).isTrue()
        assertThat(orphan.exists()).isFalse()
        assertThat(part.exists()).isFalse()
    }

    private fun createSampleAudiobook(id: Int): Audiobook {
        return Audiobook(
            id = id,
            title = "Test Audiobook",
            author = "Test Author",
            duration = 3600,
            coverImage = null,
            description = null,
            progress = Progress(position = 0, completed = 0),
            isMultiFile = 0,
            narrator = null,
            series = null,
            seriesPosition = null,
            genre = null,
            tags = null,
            publishYear = null,
            copyrightYear = null,
            publisher = null,
            isbn = null,
            asin = null,
            language = null,
            rating = null,
            subtitle = null,
            abridged = null,
            fileCount = 1,
            createdAt = "2024-01-01"
        )
    }
}