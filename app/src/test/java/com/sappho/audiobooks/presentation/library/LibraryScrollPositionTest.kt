package com.sappho.audiobooks.presentation.library

import android.app.Application
import android.content.ComponentName
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertWithMessage
import com.sappho.audiobooks.data.remote.AiStatusResponse
import com.sappho.audiobooks.domain.model.AudiobooksResponse
import com.sappho.audiobooks.domain.model.GenreMappingsResponse
import com.sappho.audiobooks.domain.model.GenreMetadata
import com.sappho.audiobooks.data.remote.SapphoApi
import com.sappho.audiobooks.data.repository.AuthRepository
import com.sappho.audiobooks.data.repository.UserPreferencesRepository
import com.sappho.audiobooks.domain.model.Audiobook
import com.sappho.audiobooks.domain.model.AuthorInfo
import io.mockk.coEvery
import io.mockk.mockk
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.rules.TestWatcher
import org.junit.runner.Description
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import retrofit2.Response

/**
 * Going back from an author's books to the Authors list lands where the list
 * was left, not at the top. The list used to own its scroll state, which was
 * thrown away when the screen switched to the author's books.
 */
@RunWith(RobolectricTestRunner::class)
class LibraryScrollPositionTest {

    val rule = createComposeRule()

    @get:Rule
    val rules: RuleChain = RuleChain
        .outerRule(object : TestWatcher() {
            override fun starting(description: Description) {
                val app = ApplicationProvider.getApplicationContext<Application>()
                Shadows.shadowOf(app.packageManager).addActivityIfNotPresent(
                    ComponentName(app.packageName, ComponentActivity::class.java.name)
                )
            }
        })
        .around(rule)

    private val authorNames = (1..60).map { "Author %02d".format(it) }
    private lateinit var viewModel: LibraryViewModel

    @Before
    fun setup() {
        val api = mockk<SapphoApi>(relaxed = true)
        coEvery { api.getGenreMappings() } returns Response.success(
            GenreMappingsResponse(genres = emptyMap(), defaults = GenreMetadata(colors = listOf("#10b981"), icon = "category"))
        )
        coEvery { api.getGenres() } returns Response.success(emptyList())
        coEvery { api.getSeries() } returns Response.success(emptyList())
        coEvery { api.getAuthors() } returns Response.success(authorNames.map { AuthorInfo(author = it, bookCount = 1) })
        val books = authorNames.mapIndexed { i, name -> Audiobook(
                id = i + 1, title = "Book by $name", subtitle = null, author = name, narrator = null,
                series = null, seriesPosition = null, duration = 3600, genre = null, tags = null,
                publishYear = null, copyrightYear = null, publisher = null, isbn = null, asin = null,
                language = null, rating = null, userRating = null, averageRating = null, abridged = null,
                description = null, coverImage = null, fileCount = 1, isMultiFile = null,
                createdAt = "2024-01-01", progress = null, chapters = null, isFavorite = false
            ) }
        coEvery { api.getAudiobooks(any(), any(), any(), any(), any(), any()) } answers {
            val offset = arg<Int?>(5) ?: 0
            Response.success(AudiobooksResponse(if (offset == 0) books else emptyList(), total = books.size))
        }
        coEvery { api.getCollections() } returns Response.success(emptyList())
        coEvery { api.getFavorites(any()) } returns Response.success(emptyList())
        coEvery { api.getAiStatus() } returns Response.success(AiStatusResponse(configured = false, provider = null))
        coEvery { api.getLibraryStats(any()) } returns Response.error(404, "".toResponseBody())
        viewModel = LibraryViewModel(api, mockk<AuthRepository>(relaxed = true), mockk<UserPreferencesRepository>(relaxed = true))
    }

    @Test
    fun `back from an author returns to the same place in the Authors list`() {
        rule.setContent { LibraryScreen(viewModel = viewModel) }

        rule.waitUntil(5_000) { rule.onAllNodesWithText("60").fetchSemanticsNodes().isNotEmpty() }
        rule.onAllNodesWithText("Authors").onFirst().performClick()

        rule.onNode(hasScrollAction()).performScrollToNode(hasText("Author 30"))
        rule.onNodeWithText("Author 30").performClick()
        rule.onAllNodesWithText("Book by Author 30").onFirst().assertIsDisplayed()

        rule.onAllNodes(hasContentDescription("Back")).onFirst().performClick()

        rule.onNodeWithText("Author 30").assertIsDisplayed()
        assertWithMessage("Authors list jumped back to the top")
            .that(rule.onAllNodesWithText("Author 01").fetchSemanticsNodes()).isEmpty()
    }

    @Test
    fun `opening Authors again from the categories starts at the top`() {
        rule.setContent { LibraryScreen(viewModel = viewModel) }

        rule.waitUntil(5_000) { rule.onAllNodesWithText("60").fetchSemanticsNodes().isNotEmpty() }
        rule.onAllNodesWithText("Authors").onFirst().performClick()
        rule.onNode(hasScrollAction()).performScrollToNode(hasText("Author 30"))

        rule.onAllNodes(hasContentDescription("Back")).onFirst().performClick()
        rule.waitUntil(5_000) { rule.onAllNodesWithText("60").fetchSemanticsNodes().isNotEmpty() }
        rule.onAllNodesWithText("Authors").onFirst().performClick()

        rule.onNodeWithText("Author 01").assertIsDisplayed()
    }
}
