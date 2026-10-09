package com.sappho.audiobooks.presentation.library

import android.app.Application
import android.content.ComponentName
import androidx.activity.ComponentActivity
import androidx.test.core.app.ApplicationProvider
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import com.sappho.audiobooks.data.repository.LibraryFilterOption
import com.sappho.audiobooks.data.repository.LibrarySortOption
import com.sappho.audiobooks.domain.model.LinkedSource
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.rules.TestWatcher
import org.junit.runner.Description
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.GraphicsMode

/** The All Books filter row on a narrow phone: labels stay on one line. */
@RunWith(RobolectricTestRunner::class)
// Real text measurement; the default (legacy) graphics mode doesn't lay text out faithfully.
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AllBooksFilterRowTest {

    val rule = createComposeRule()

    // The compose rule hosts content in a ComponentActivity that only the debug
    // manifest declares (ui-test-manifest is debugImplementation). Register it
    // with Robolectric first so testReleaseUnitTest can launch it too.
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

    private val robert = LinkedSource(id = 4, name = "Robert", available = true)

    private fun SemanticsNodeInteraction.lineCount(): Int {
        val results = mutableListOf<TextLayoutResult>()
        fetchSemanticsNode().config[SemanticsActions.GetTextLayoutResult].action?.invoke(results)
        return results.single().lineCount
    }

    /** A 320dp-wide phone (the narrowest common width), optionally with larger text. */
    private fun onNarrowPhone(fontScale: Float = 1f, content: @Composable () -> Unit) {
        rule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                Box(Modifier.width(320.dp)) { content() }
            }
        }
    }

    @Composable
    private fun FilterRow(
        filter: LibraryFilterOption = LibraryFilterOption.ALL,
        sort: LibrarySortOption = LibrarySortOption.TITLE,
        sources: List<LinkedSource> = listOf(robert),
        sourceFilter: String = LibraryViewModel.SOURCE_ALL
    ) = AllBooksFilterRow(
        filterOption = filter,
        onFilterSelect = {},
        sortOption = sort,
        onSortSelect = {},
        sortAscending = true,
        onAscendingToggle = {},
        linkedSources = sources,
        sourceFilter = sourceFilter,
        onSourceSelect = {}
    )

    @Test
    fun `every sort option fits on one line next to show and source`() {
        onNarrowPhone {
            Column { LibrarySortOption.entries.forEach { FilterRow(filter = LibraryFilterOption.NOT_STARTED, sort = it) } }
        }

        LibrarySortOption.entries.forEach { option ->
            assertWithMessage(option.displayName)
                .that(rule.onNodeWithText(option.displayName).lineCount()).isEqualTo(1)
        }
    }

    @Test
    fun `every show option fits on one line next to sort and source`() {
        onNarrowPhone {
            Column { LibraryFilterOption.entries.forEach { FilterRow(filter = it, sort = LibrarySortOption.RECENTLY_LISTENED) } }
        }

        LibraryFilterOption.entries.forEach { option ->
            assertWithMessage(option.displayName)
                .that(rule.onNodeWithText(option.displayName).lineCount()).isEqualTo(1)
        }
    }

    @Test
    fun `labels stay on one line with large text`() {
        onNarrowPhone(fontScale = 1.5f) {
            FilterRow(filter = LibraryFilterOption.HIDE_FINISHED, sort = LibrarySortOption.RECENTLY_LISTENED)
        }

        assertThat(rule.onNodeWithText("Hide Finished").lineCount()).isEqualTo(1)
        assertThat(rule.onNodeWithText("Recently Listened").lineCount()).isEqualTo(1)
        assertThat(rule.onNodeWithText("All").lineCount()).isEqualTo(1)
    }

    @Test
    fun `a long linked-server name stays on one line`() {
        val longName = "Robert and Margaret's Shared Audiobook Library"
        onNarrowPhone {
            FilterRow(sources = listOf(LinkedSource(id = 4, name = longName, available = false)), sourceFilter = "4")
        }

        assertThat(rule.onNodeWithText("$longName (offline)").lineCount()).isEqualTo(1)
    }

    @Test
    fun `source appears only with linked servers`() {
        onNarrowPhone { FilterRow(sources = emptyList()) }

        rule.onAllNodesWithText("Source").assertCountEquals(0)
        rule.onNodeWithText("Show").assertExists()
        rule.onNodeWithText("Sort").assertExists()
    }
}
