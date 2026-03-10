# Enhanced Reading List Management Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Transform the reading list from a cover grid into a numbered, drag-and-drop reorderable list with swipe-to-remove and sort options.

**Architecture:** Rewrite `ReadingListScreen.kt` from a `LazyVerticalGrid` to a `LazyColumn` with `sh.calvin.reorderable` for drag-and-drop. Extend `ReadingListViewModel` with sort state, reorder sync, and remove functionality. Add 3 new endpoints to `SapphoApi.kt` (sort param on getFavorites, reorder, remove).

**Tech Stack:** Jetpack Compose, sh.calvin.reorderable:3.0.0, Hilt, Retrofit, StateFlow

---

### Task 1: Add reorderable library dependency

**Files:**
- Modify: `app/build.gradle.kts:176` (after accompanist deps)

**Step 1: Add dependency**

After line 176 (`accompanist-swiperefresh`), add:

```kotlin
    // Reorderable (Drag-and-drop for LazyColumn)
    implementation("sh.calvin.reorderable:reorderable:3.0.0")
```

**Step 2: Sync and verify**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL

**Step 3: Commit**

```bash
git add app/build.gradle.kts
git commit -m "Add sh.calvin.reorderable library for reading list drag-and-drop (#156)"
```

---

### Task 2: Add API endpoints for sort, reorder, and remove

**Files:**
- Modify: `app/src/main/java/com/sappho/audiobooks/data/remote/SapphoApi.kt:73-78`

**Step 1: Write tests for the new API endpoints**

Create: `app/src/test/java/com/sappho/audiobooks/data/remote/ReadingListApiTest.kt`

```kotlin
package com.sappho.audiobooks.data.remote

import com.google.common.truth.Truth.assertThat
import com.sappho.audiobooks.domain.model.Audiobook
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class ReadingListApiTest {

    private lateinit var mockWebServer: MockWebServer
    private lateinit var api: SapphoApi

    @Before
    fun setUp() {
        mockWebServer = MockWebServer()
        mockWebServer.start()
        api = Retrofit.Builder()
            .baseUrl(mockWebServer.url("/"))
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(SapphoApi::class.java)
    }

    @After
    fun tearDown() {
        mockWebServer.shutdown()
    }

    @Test
    fun `getFavorites sends sort query parameter`() = runTest {
        mockWebServer.enqueue(MockResponse().setBody("[]").setResponseCode(200))

        api.getFavorites(sort = "title")

        val request = mockWebServer.takeRequest()
        assertThat(request.path).isEqualTo("/api/audiobooks/favorites?sort=title")
    }

    @Test
    fun `getFavorites defaults to custom sort`() = runTest {
        mockWebServer.enqueue(MockResponse().setBody("[]").setResponseCode(200))

        api.getFavorites()

        val request = mockWebServer.takeRequest()
        assertThat(request.path).isEqualTo("/api/audiobooks/favorites?sort=custom")
    }

    @Test
    fun `reorderFavorites sends correct body`() = runTest {
        mockWebServer.enqueue(MockResponse().setBody("{}").setResponseCode(200))

        api.reorderFavorites(ReorderFavoritesRequest(order = listOf(3, 1, 2)))

        val request = mockWebServer.takeRequest()
        assertThat(request.path).isEqualTo("/api/audiobooks/favorites/reorder")
        assertThat(request.method).isEqualTo("PUT")
        assertThat(request.body.readUtf8()).contains("[3,1,2]")
    }

    @Test
    fun `removeFavorite sends DELETE request`() = runTest {
        mockWebServer.enqueue(MockResponse().setBody("{}").setResponseCode(200))

        api.removeFavorite(42)

        val request = mockWebServer.takeRequest()
        assertThat(request.path).isEqualTo("/api/audiobooks/42/favorite")
        assertThat(request.method).isEqualTo("DELETE")
    }
}
```

**Step 2: Run tests to verify they fail**

Run: `./gradlew test --tests "*ReadingListApiTest*"`
Expected: FAIL — methods don't exist yet

**Step 3: Update SapphoApi.kt**

Replace the existing `getFavorites` (lines 73-75) with sorted version, and add reorder + remove endpoints:

```kotlin
    // Favorites / Reading List
    @GET("api/audiobooks/favorites")
    suspend fun getFavorites(@Query("sort") sort: String = "custom"): Response<List<Audiobook>>

    @PUT("api/audiobooks/favorites/reorder")
    suspend fun reorderFavorites(@Body request: ReorderFavoritesRequest): Response<Unit>

    @DELETE("api/audiobooks/{id}/favorite")
    suspend fun removeFavorite(@Path("id") audiobookId: Int): Response<Unit>
```

Add the request data class near the other request classes (after `BatchActionRequest`):

```kotlin
data class ReorderFavoritesRequest(
    val order: List<Int>
)
```

**Step 4: Run tests to verify they pass**

Run: `./gradlew test --tests "*ReadingListApiTest*"`
Expected: PASS

**Step 5: Commit**

```bash
git add app/src/main/java/com/sappho/audiobooks/data/remote/SapphoApi.kt app/src/test/java/com/sappho/audiobooks/data/remote/ReadingListApiTest.kt
git commit -m "Add API endpoints for reading list sort, reorder, and remove (#156)"
```

---

### Task 3: Extend ReadingListViewModel with sort, reorder, and remove logic

**Files:**
- Modify: `app/src/main/java/com/sappho/audiobooks/presentation/readinglist/ReadingListViewModel.kt`
- Create: `app/src/test/java/com/sappho/audiobooks/presentation/readinglist/ReadingListViewModelTest.kt`

**Step 1: Write tests**

```kotlin
package com.sappho.audiobooks.presentation.readinglist

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.sappho.audiobooks.data.remote.ReorderFavoritesRequest
import com.sappho.audiobooks.data.remote.SapphoApi
import com.sappho.audiobooks.data.repository.AuthRepository
import com.sappho.audiobooks.domain.model.Audiobook
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Before
import org.junit.Test
import retrofit2.Response

@OptIn(ExperimentalCoroutinesApi::class)
class ReadingListViewModelTest {

    private lateinit var api: SapphoApi
    private lateinit var authRepository: AuthRepository
    private val testDispatcher = StandardTestDispatcher()

    private fun createBook(id: Int, title: String, author: String? = null, duration: Int? = null) = Audiobook(
        id = id, title = title, author = author, duration = duration
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        api = mockk()
        authRepository = mockk()
        every { authRepository.getServerUrlSync() } returns "https://test.com"
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(): ReadingListViewModel {
        coEvery { api.getFavorites(any()) } returns Response.success(emptyList())
        return ReadingListViewModel(api, authRepository)
    }

    @Test
    fun `loadReadingList calls getFavorites with current sort option`() = runTest {
        val books = listOf(createBook(1, "Book A"), createBook(2, "Book B"))
        coEvery { api.getFavorites("custom") } returns Response.success(books)

        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.books.test {
            assertThat(awaitItem()).hasSize(2)
        }
    }

    @Test
    fun `setSortOption reloads list with new sort`() = runTest {
        coEvery { api.getFavorites("custom") } returns Response.success(emptyList())
        coEvery { api.getFavorites("title") } returns Response.success(
            listOf(createBook(1, "Alpha"), createBook(2, "Beta"))
        )

        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.setSortOption("title")
        advanceUntilIdle()

        viewModel.books.test {
            assertThat(awaitItem()).hasSize(2)
        }
        coVerify { api.getFavorites("title") }
    }

    @Test
    fun `reorderBooks updates local list and syncs to server`() = runTest {
        val books = listOf(createBook(1, "First"), createBook(2, "Second"), createBook(3, "Third"))
        coEvery { api.getFavorites("custom") } returns Response.success(books)
        coEvery { api.reorderFavorites(any()) } returns Response.success(Unit)

        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.reorderBooks(0, 2) // move first to third
        advanceUntilIdle()

        viewModel.books.test {
            val reordered = awaitItem()
            assertThat(reordered[0].id).isEqualTo(2)
            assertThat(reordered[1].id).isEqualTo(3)
            assertThat(reordered[2].id).isEqualTo(1)
        }
        coVerify { api.reorderFavorites(ReorderFavoritesRequest(listOf(2, 3, 1))) }
    }

    @Test
    fun `removeBook removes from local list and calls server`() = runTest {
        val books = listOf(createBook(1, "First"), createBook(2, "Second"))
        coEvery { api.getFavorites("custom") } returns Response.success(books)
        coEvery { api.removeFavorite(1) } returns Response.success(Unit)

        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.removeBook(1)
        advanceUntilIdle()

        viewModel.books.test {
            val remaining = awaitItem()
            assertThat(remaining).hasSize(1)
            assertThat(remaining[0].id).isEqualTo(2)
        }
        coVerify { api.removeFavorite(1) }
    }
}
```

**Step 2: Run tests to verify they fail**

Run: `./gradlew test --tests "*ReadingListViewModelTest*"`
Expected: FAIL — new methods don't exist yet

**Step 3: Implement ViewModel changes**

Rewrite `ReadingListViewModel.kt`:

```kotlin
package com.sappho.audiobooks.presentation.readinglist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sappho.audiobooks.data.remote.ReorderFavoritesRequest
import com.sappho.audiobooks.data.remote.SapphoApi
import com.sappho.audiobooks.data.repository.AuthRepository
import com.sappho.audiobooks.domain.model.Audiobook
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ReadingListViewModel @Inject constructor(
    private val api: SapphoApi,
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _books = MutableStateFlow<List<Audiobook>>(emptyList())
    val books: StateFlow<List<Audiobook>> = _books

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _serverUrl = MutableStateFlow<String?>(null)
    val serverUrl: StateFlow<String?> = _serverUrl

    private val _sortOption = MutableStateFlow("custom")
    val sortOption: StateFlow<String> = _sortOption

    init {
        _serverUrl.value = authRepository.getServerUrlSync()
        loadReadingList()
    }

    fun loadReadingList() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val response = api.getFavorites(sort = _sortOption.value)
                if (response.isSuccessful) {
                    _books.value = response.body() ?: emptyList()
                }
            } catch (_: Exception) {
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun setSortOption(sort: String) {
        _sortOption.value = sort
        loadReadingList()
    }

    fun reorderBooks(fromIndex: Int, toIndex: Int) {
        val current = _books.value.toMutableList()
        val item = current.removeAt(fromIndex)
        current.add(toIndex, item)
        _books.value = current

        viewModelScope.launch {
            try {
                api.reorderFavorites(ReorderFavoritesRequest(current.map { it.id }))
            } catch (_: Exception) {
            }
        }
    }

    fun removeBook(audiobookId: Int) {
        _books.value = _books.value.filter { it.id != audiobookId }

        viewModelScope.launch {
            try {
                api.removeFavorite(audiobookId)
            } catch (_: Exception) {
                // Reload on failure to restore correct state
                loadReadingList()
            }
        }
    }

    fun refresh() {
        loadReadingList()
    }
}
```

**Step 4: Run tests to verify they pass**

Run: `./gradlew test --tests "*ReadingListViewModelTest*"`
Expected: PASS

**Step 5: Commit**

```bash
git add app/src/main/java/com/sappho/audiobooks/presentation/readinglist/ReadingListViewModel.kt app/src/test/java/com/sappho/audiobooks/presentation/readinglist/ReadingListViewModelTest.kt
git commit -m "Add sort, reorder, and remove logic to ReadingListViewModel (#156)"
```

---

### Task 4: Rewrite ReadingListScreen as numbered reorderable list

**Files:**
- Modify: `app/src/main/java/com/sappho/audiobooks/presentation/readinglist/ReadingListScreen.kt`

**Step 1: Rewrite the screen**

Replace the entire contents of `ReadingListScreen.kt`. Key changes:
- `LazyVerticalGrid` → `LazyColumn` with `rememberReorderableLazyListState`
- Grid items → `ReadingListRow` composable (number, cover thumb, title, author, duration, drag handle)
- Add sort dropdown in header (Custom, Title, Author, Date Added)
- Add `SwipeToDismissBox` on each row for swipe-to-remove
- Keep existing empty state, loading state, back handler, and `ReadingListRibbon`

```kotlin
package com.sappho.audiobooks.presentation.readinglist

import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.BookmarkAdded
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.foundation.Canvas
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.sappho.audiobooks.domain.model.Audiobook
import com.sappho.audiobooks.presentation.theme.*
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReadingListScreen(
    onAudiobookClick: (Int) -> Unit = {},
    onBackClick: () -> Unit = {},
    viewModel: ReadingListViewModel = hiltViewModel()
) {
    val books by viewModel.books.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val serverUrl by viewModel.serverUrl.collectAsState()
    val sortOption by viewModel.sortOption.collectAsState()

    BackHandler { onBackClick() }

    LaunchedEffect(Unit) {
        viewModel.refresh()
    }

    val lazyListState = rememberLazyListState()
    val reorderableLazyListState = rememberReorderableLazyListState(lazyListState) { from, to ->
        viewModel.reorderBooks(from.index, to.index)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(SapphoBackground)
    ) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBackClick) {
                Icon(
                    imageVector = Icons.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = Color.White
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Reading List",
                    style = MaterialTheme.typography.headlineLarge,
                    color = Color.White
                )
                Text(
                    text = "${books.size} books",
                    style = MaterialTheme.typography.bodySmall,
                    color = SapphoIconDefault
                )
            }

            // Sort dropdown
            SortDropdown(
                currentSort = sortOption,
                onSortSelected = { viewModel.setSortOption(it) }
            )
        }

        if (isLoading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = SapphoInfo)
            }
        } else if (books.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.BookmarkAdded,
                        contentDescription = "Empty reading list",
                        tint = SapphoInfo,
                        modifier = Modifier.size(48.dp)
                    )
                    Text(
                        text = "Your reading list is empty",
                        style = MaterialTheme.typography.titleLarge,
                        color = Color.White
                    )
                    Text(
                        text = "Add books to your reading list from the book detail page",
                        style = MaterialTheme.typography.bodyMedium,
                        color = SapphoIconDefault
                    )
                }
            }
        } else {
            LazyColumn(
                state = lazyListState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                contentPadding = PaddingValues(bottom = 16.dp)
            ) {
                itemsIndexed(books, key = { _, book -> book.id }) { index, book ->
                    ReorderableItem(reorderableLazyListState, key = book.id) { isDragging ->
                        val dismissState = rememberSwipeToDismissBoxState(
                            confirmValueChange = { value ->
                                if (value == SwipeToDismissBoxValue.EndToStart) {
                                    viewModel.removeBook(book.id)
                                    true
                                } else false
                            }
                        )

                        SwipeToDismissBox(
                            state = dismissState,
                            backgroundContent = {
                                val color by animateColorAsState(
                                    targetValue = if (dismissState.targetValue == SwipeToDismissBoxValue.EndToStart)
                                        SapphoError else Color.Transparent,
                                    label = "swipe-bg"
                                )
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(color)
                                        .padding(end = 20.dp),
                                    contentAlignment = Alignment.CenterEnd
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = "Remove",
                                        tint = Color.White
                                    )
                                }
                            },
                            enableDismissFromStartToEnd = false
                        ) {
                            ReadingListRow(
                                book = book,
                                index = index,
                                serverUrl = serverUrl,
                                isDragging = isDragging,
                                onClick = { onAudiobookClick(book.id) },
                                dragModifier = Modifier.draggableHandle()
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ReadingListRow(
    book: Audiobook,
    index: Int,
    serverUrl: String?,
    isDragging: Boolean,
    onClick: () -> Unit,
    dragModifier: Modifier = Modifier
) {
    val elevation = if (isDragging) 8.dp else 0.dp

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        color = if (isDragging) SapphoSurfaceElevated else SapphoSurface,
        shadowElevation = elevation
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Number
            Text(
                text = "#${index + 1}",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = SapphoInfo,
                modifier = Modifier.width(32.dp)
            )

            // Cover thumbnail
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(6.dp))
            ) {
                if (book.coverImage != null && serverUrl != null) {
                    AsyncImage(
                        model = com.sappho.audiobooks.util.buildCoverUrl(serverUrl, book.id, com.sappho.audiobooks.util.COVER_WIDTH_THUMBNAIL),
                        contentDescription = book.title,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.linearGradient(
                                    colors = listOf(SapphoProgressTrack, SapphoSurfaceDark)
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = book.title.take(2).uppercase(),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }

                // Progress bar
                val progress = book.progress
                if (progress != null && book.duration != null && book.duration > 0) {
                    val progressPercent = if (progress.completed == 1) 1f
                    else (progress.position.toFloat() / book.duration.toFloat()).coerceIn(0f, 1f)

                    if (progressPercent > 0) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .fillMaxWidth()
                                .height(3.dp)
                                .background(Color.Black.copy(alpha = 0.7f))
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .fillMaxWidth(progressPercent)
                                    .background(
                                        if (progress.completed == 1) SapphoSuccess else SapphoInfo
                                    )
                            )
                        }
                    }
                }
            }

            // Title + Author + Duration
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = book.title,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = SapphoText,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                val subtitle = buildList {
                    book.author?.let { add(it) }
                    book.duration?.let { add(formatDuration(it)) }
                }.joinToString(" \u00B7 ")

                if (subtitle.isNotEmpty()) {
                    Text(
                        text = subtitle,
                        fontSize = 12.sp,
                        color = SapphoIconDefault,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // Drag handle
            Icon(
                imageVector = Icons.Default.DragHandle,
                contentDescription = "Reorder",
                tint = SapphoIconDefault,
                modifier = dragModifier.size(24.dp)
            )
        }
    }
}

@Composable
private fun SortDropdown(
    currentSort: String,
    onSortSelected: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    val sortLabels = mapOf(
        "custom" to "Custom",
        "title" to "Title",
        "date" to "Date Added"
    )

    Box {
        TextButton(onClick = { expanded = true }) {
            Text(
                text = sortLabels[currentSort] ?: "Custom",
                color = SapphoInfo,
                fontSize = 14.sp
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            sortLabels.forEach { (key, label) ->
                DropdownMenuItem(
                    text = { Text(label, color = Color.White) },
                    onClick = {
                        onSortSelected(key)
                        expanded = false
                    },
                    colors = MenuDefaults.itemColors(
                        textColor = Color.White
                    )
                )
            }
        }
    }
}

private fun formatDuration(totalSeconds: Int): String {
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    return if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m"
}

/**
 * Reading list corner ribbon - blue folded ribbon on top-right corner
 */
@Composable
fun ReadingListRibbon(
    modifier: Modifier = Modifier,
    size: Float = 32f
) {
    Canvas(
        modifier = modifier.size(size.dp)
    ) {
        val ribbonColor = SapphoInfo
        val shadowColor = LegacyBlueDark

        val trianglePath = Path().apply {
            moveTo(0f, 0f)
            lineTo(this@Canvas.size.width, 0f)
            lineTo(this@Canvas.size.width, this@Canvas.size.height)
            close()
        }
        drawPath(trianglePath, ribbonColor, style = Fill)

        val foldPath = Path().apply {
            moveTo(0f, 0f)
            lineTo(this@Canvas.size.width * 0.3f, this@Canvas.size.height * 0.3f)
            lineTo(this@Canvas.size.width * 0.4f, this@Canvas.size.height * 0.25f)
            lineTo(this@Canvas.size.width * 0.1f, 0f)
            close()
        }
        drawPath(foldPath, shadowColor.copy(alpha = 0.4f), style = Fill)
    }
}
```

**Step 2: Verify build**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL

**Step 3: Commit**

```bash
git add app/src/main/java/com/sappho/audiobooks/presentation/readinglist/ReadingListScreen.kt
git commit -m "Rewrite ReadingListScreen as numbered reorderable list with swipe-to-remove (#156)"
```

---

### Task 5: Verify all tests pass and install on device

**Step 1: Run all tests**

Run: `./gradlew test`
Expected: BUILD SUCCESSFUL

**Step 2: Install on device**

Run: `./gradlew installDebug`
Expected: BUILD SUCCESSFUL, app installed

**Step 3: Version bump**

In `app/build.gradle.kts`, bump:
- `versionCode` to 66
- `versionName` to "0.9.48"

**Step 4: Final commit**

```bash
git add app/build.gradle.kts
git commit -m "Bump version to 0.9.48 (66) for reading list management release (#156)"
```

---

### Notes for implementer

- **`ReadingListRibbon`** is used by other files (e.g., `LibraryScreen.kt`). Keep it public — it's referenced externally.
- **`SapphoSurface`** and **`SapphoSurfaceElevated`** are theme colors from `Color.kt`. If `SapphoSurfaceElevated` doesn't exist, use `SapphoSurfaceDark` for the dragging state.
- The `sh.calvin.reorderable` library uses `ReorderableItem` + `Modifier.draggableHandle()`. See [README](https://github.com/Calvin-LL/Reorderable) for API reference.
- The server's `GET /api/audiobooks/favorites?sort=custom` returns books ordered by `list_order`. The `sort=title` sorts by title server-side. There is no `author` sort on the server — omit it from sort options.
- Swipe-to-remove is optimistic: remove from local list immediately, call API in background. On API failure, reload the list.
- Drag reorder is also optimistic: update local list on drag, sync to server after drop.
