package com.sappho.audiobooks.presentation.library

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.foundation.Canvas
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.sappho.audiobooks.presentation.theme.*
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.ui.draw.scale
import com.sappho.audiobooks.util.HapticPatterns
import kotlinx.coroutines.launch

/**
 * Parse a hex color string (e.g., "#06b6d4") to a Compose Color
 */
private fun parseHexColor(hex: String): Color {
    return try {
        val colorString = hex.removePrefix("#")
        Color(android.graphics.Color.parseColor("#$colorString"))
    } catch (e: Exception) {
        SapphoSuccess // Default green if parsing fails
    }
}

/**
 * Get colors for a genre from server data
 */
private fun getGenreColorsFromServer(genre: String): List<Color> {
    val hexColors = LibraryViewModel.getGenreColors(genre)
    return hexColors.map { parseHexColor(it) }
}

/**
 * Reading list corner ribbon - blue folded ribbon on top-right corner
 */
@Composable
private fun ReadingListRibbon(
    modifier: Modifier = Modifier,
    size: Float = 32f
) {
    Canvas(
        modifier = modifier.size(size.dp)
    ) {
        val ribbonColor = SapphoInfo
        val shadowColor = LegacyBlueDark

        // Main triangle (folded corner)
        val trianglePath = Path().apply {
            moveTo(0f, 0f)
            lineTo(this@Canvas.size.width, 0f)
            lineTo(this@Canvas.size.width, this@Canvas.size.height)
            close()
        }
        drawPath(trianglePath, ribbonColor, style = Fill)

        // Shadow fold line (darker edge to create 3D folded effect)
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

/**
 * Map server icon name to Material Icon
 */
private fun getGenreIconFromServer(genre: String): ImageVector {
    return when (LibraryViewModel.getGenreIcon(genre)) {
        "search" -> Icons.Default.Search
        "rocket" -> Icons.Default.Rocket
        "auto_awesome" -> Icons.Default.AutoAwesome
        "favorite" -> Icons.Default.Favorite
        "visibility" -> Icons.Default.Visibility
        "castle" -> Icons.Default.Castle
        "person" -> Icons.Default.Person
        "psychology" -> Icons.Default.Psychology
        "trending_up" -> Icons.Default.TrendingUp
        "history_edu" -> Icons.Default.HistoryEdu
        "science" -> Icons.Default.Science
        "favorite_border" -> Icons.Default.FavoriteBorder
        "self_improvement" -> Icons.Default.SelfImprovement
        "gavel" -> Icons.Default.Gavel
        "sentiment_very_satisfied" -> Icons.Default.SentimentVerySatisfied
        "face" -> Icons.Default.Face
        "child_care" -> Icons.Default.ChildCare
        "menu_book" -> Icons.Default.MenuBook
        "edit_note" -> Icons.Default.EditNote
        "theater_comedy" -> Icons.Default.TheaterComedy
        "explore" -> Icons.Default.Explore
        "landscape" -> Icons.Default.Landscape
        "sports_esports" -> Icons.Default.SportsEsports
        "local_fire_department" -> Icons.Default.LocalFireDepartment
        "category" -> Icons.Default.Category
        else -> Icons.Default.Category
    }
}

enum class LibraryView {
    CATEGORIES, SERIES, SERIES_BOOKS, AUTHORS, AUTHOR_BOOKS, GENRES, GENRE_BOOKS, ALL_BOOKS
}

@Composable
fun LibraryScreen(
    onAudiobookClick: (Int) -> Unit = {},
    onCollectionsClick: () -> Unit = {},
    onReadingListClick: () -> Unit = {},
    onUploadClick: () -> Unit = {},
    isAdmin: Boolean = false,
    initialAuthor: String? = null,
    initialSeries: String? = null,
    viewModel: LibraryViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val series by viewModel.series.collectAsState()
    val authors by viewModel.authors.collectAsState()
    val genres by viewModel.genres.collectAsState()
    val allBooks by viewModel.allAudiobooks.collectAsState()
    val collections by viewModel.collections.collectAsState()
    val readingList by viewModel.readingList.collectAsState()

    // Refresh data when screen is loaded to get latest progress
    LaunchedEffect(Unit) {
        viewModel.refresh()
    }

    // Determine initial view based on parameters
    val initialView = when {
        initialSeries != null -> LibraryView.SERIES_BOOKS
        initialAuthor != null -> LibraryView.AUTHOR_BOOKS
        else -> LibraryView.CATEGORIES
    }

    // Use rememberSaveable to persist state across navigation (e.g., when going to book detail and back)
    var currentViewName by rememberSaveable { mutableStateOf(initialView.name) }
    val currentView = LibraryView.valueOf(currentViewName)

    var selectedSeries by rememberSaveable { mutableStateOf(initialSeries) }
    var selectedAuthor by rememberSaveable { mutableStateOf(initialAuthor) }
    var selectedGenre by rememberSaveable { mutableStateOf<String?>(null) }

    // Handle system back button to navigate within Library views
    BackHandler(enabled = currentView != LibraryView.CATEGORIES) {
        currentViewName = when (currentView) {
            LibraryView.SERIES -> LibraryView.CATEGORIES.name
            LibraryView.SERIES_BOOKS -> LibraryView.SERIES.name
            LibraryView.AUTHORS -> LibraryView.CATEGORIES.name
            LibraryView.AUTHOR_BOOKS -> LibraryView.AUTHORS.name
            LibraryView.GENRES -> LibraryView.CATEGORIES.name
            LibraryView.GENRE_BOOKS -> LibraryView.GENRES.name
            LibraryView.ALL_BOOKS -> LibraryView.CATEGORIES.name
            LibraryView.CATEGORIES -> currentViewName // Should not happen due to enabled check
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(SapphoBackground)
    ) {
        when (currentView) {
            LibraryView.CATEGORIES -> {
                CategoriesView(
                    totalBooks = allBooks.size,
                    seriesCount = series.size,
                    authorsCount = authors.size,
                    genresCount = genres.size,
                    collectionsCount = collections.size,
                    readingListCount = readingList.size,
                    onSeriesClick = { currentViewName = LibraryView.SERIES.name },
                    onAuthorsClick = { currentViewName = LibraryView.AUTHORS.name },
                    onGenresClick = { currentViewName = LibraryView.GENRES.name },
                    onAllBooksClick = { currentViewName = LibraryView.ALL_BOOKS.name },
                    onCollectionsClick = onCollectionsClick,
                    onReadingListClick = onReadingListClick,
                    onUploadClick = onUploadClick,
                    isAdmin = isAdmin
                )
            }
            LibraryView.SERIES -> {
                SeriesListView(
                    series = series,
                    allBooks = allBooks,
                    serverUrl = viewModel.serverUrl.collectAsState().value,
                    onBackClick = { currentViewName = LibraryView.CATEGORIES.name },
                    onSeriesClick = { seriesName ->
                        selectedSeries = seriesName
                        currentViewName = LibraryView.SERIES_BOOKS.name
                    }
                )
            }
            LibraryView.SERIES_BOOKS -> {
                selectedSeries?.let { seriesName ->
                    val seriesBooks = allBooks.filter { it.series == seriesName }
                        .sortedBy { it.seriesPosition ?: 0f }
                    val totalDuration = seriesBooks.sumOf { it.duration ?: 0 }
                    val bookAuthors = seriesBooks.mapNotNull { it.author }.distinct()
                    SeriesBooksView(
                        seriesName = seriesName,
                        books = seriesBooks,
                        totalDuration = totalDuration,
                        authors = bookAuthors,
                        serverUrl = viewModel.serverUrl.collectAsState().value,
                        aiConfigured = viewModel.aiConfigured.collectAsState().value,
                        viewModel = viewModel,
                        onBackClick = { currentViewName = LibraryView.SERIES.name },
                        onBookClick = onAudiobookClick
                    )
                }
            }
            LibraryView.AUTHORS -> {
                AuthorsListView(
                    authors = authors,
                    allBooks = allBooks,
                    serverUrl = viewModel.serverUrl.collectAsState().value,
                    onBackClick = { currentViewName = LibraryView.CATEGORIES.name },
                    onAuthorClick = { author ->
                        selectedAuthor = author
                        currentViewName = LibraryView.AUTHOR_BOOKS.name
                    }
                )
            }
            LibraryView.AUTHOR_BOOKS -> {
                selectedAuthor?.let { authorName ->
                    val authorBooks = allBooks.filter { it.author == authorName }
                        .sortedBy { it.title }
                    AuthorBooksView(
                        authorName = authorName,
                        books = authorBooks,
                        serverUrl = viewModel.serverUrl.collectAsState().value,
                        onBackClick = { currentViewName = LibraryView.AUTHORS.name },
                        onBookClick = onAudiobookClick
                    )
                }
            }
            LibraryView.GENRES -> {
                GenresListView(
                    genres = genres.map { it.genre },
                    allBooks = allBooks,
                    serverUrl = viewModel.serverUrl.collectAsState().value,
                    onBackClick = { currentViewName = LibraryView.CATEGORIES.name },
                    onGenreClick = { genre ->
                        selectedGenre = genre
                        currentViewName = LibraryView.GENRE_BOOKS.name
                    }
                )
            }
            LibraryView.GENRE_BOOKS -> {
                selectedGenre?.let { genreName ->
                    val genreBooks = allBooks.filter { book ->
                        book.genre?.let { LibraryViewModel.getAllNormalizedGenres(it).contains(genreName) } ?: false
                    }.sortedBy { it.title }
                    GenreBooksView(
                        genreName = genreName,
                        books = genreBooks,
                        serverUrl = viewModel.serverUrl.collectAsState().value,
                        onBackClick = { currentViewName = LibraryView.GENRES.name },
                        onBookClick = onAudiobookClick
                    )
                }
            }
            LibraryView.ALL_BOOKS -> {
                AllBooksView(
                    viewModel = viewModel,
                    onBackClick = { currentViewName = LibraryView.CATEGORIES.name },
                    onAudiobookClick = onAudiobookClick,
                    isAdmin = isAdmin
                )
            }
        }
    }
}

@Composable
fun CategoriesView(
    totalBooks: Int,
    seriesCount: Int,
    authorsCount: Int,
    genresCount: Int,
    collectionsCount: Int,
    readingListCount: Int,
    onSeriesClick: () -> Unit,
    onAuthorsClick: () -> Unit,
    onGenresClick: () -> Unit,
    onAllBooksClick: () -> Unit,
    onCollectionsClick: () -> Unit,
    onReadingListClick: () -> Unit,
    onUploadClick: () -> Unit = {},
    isAdmin: Boolean = false
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item { Spacer(modifier = Modifier.height(8.dp)) }

        // Header with total count
        item {
            Column(modifier = Modifier.padding(bottom = 8.dp)) {
                Text(
                    text = "Library",
                    style = MaterialTheme.typography.displaySmall,
                    color = Color.White
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "$totalBooks audiobooks in your collection",
                    style = MaterialTheme.typography.bodyMedium,
                    color = SapphoIconDefault
                )
            }
        }

        // Series Card - Full width featured card
        item {
            CategoryCardLarge(
                icon = Icons.Filled.MenuBook,
                title = "Series",
                count = seriesCount,
                label = "",
                gradientColors = listOf(CategoryColors.contentLight, CategoryColors.contentDark),
                onClick = onSeriesClick
            )
        }

        // Two column cards - Authors & Genres
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                CategoryCardMedium(
                    icon = Icons.Default.Person,
                    title = "Authors",
                    count = authorsCount,
                    label = "authors",
                    gradientColors = listOf(CategoryColors.contentLight, CategoryColors.contentDark),
                    onClick = onAuthorsClick,
                    modifier = Modifier.weight(1f)
                )
                CategoryCardMedium(
                    icon = Icons.Default.Category,
                    title = "Genres",
                    count = genresCount,
                    label = "genres",
                    gradientColors = listOf(CategoryColors.contentLight, CategoryColors.contentDark),
                    onClick = onGenresClick,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // Two column cards - Collections & Reading List
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                CategoryCardMedium(
                    icon = Icons.Default.LibraryBooks,
                    title = "Collections",
                    count = collectionsCount,
                    label = "collections",
                    gradientColors = listOf(CategoryColors.personalLight, CategoryColors.personalDark),
                    onClick = onCollectionsClick,
                    modifier = Modifier.weight(1f)
                )
                CategoryCardMedium(
                    icon = Icons.Default.BookmarkAdded,
                    title = "Reading List",
                    count = readingListCount,
                    label = "books",
                    gradientColors = listOf(CategoryColors.personalLight, CategoryColors.personalDark),
                    onClick = onReadingListClick,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // All Books Card
        item {
            CategoryCardWide(
                icon = Icons.Default.GridView,
                title = "All Books",
                subtitle = "Browse your complete collection",
                gradientColors = listOf(CategoryColors.neutralLight, CategoryColors.neutralDark),
                onClick = onAllBooksClick
            )
        }

        // Upload Card - Admin only
        if (isAdmin) {
            item {
                CategoryCardWide(
                    icon = Icons.Default.Upload,
                    title = "Upload",
                    subtitle = "Add new audiobooks to your library",
                    gradientColors = listOf(SapphoSuccess, SapphoSuccess.copy(alpha = 0.7f)),
                    onClick = onUploadClick
                )
            }
        }

        item { Spacer(modifier = Modifier.height(16.dp)) }
    }
}

@Composable
fun CategoryCardLarge(
    icon: ImageVector,
    title: String,
    count: Int,
    label: String,
    gradientColors: List<Color>,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(140.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Brush.linearGradient(gradientColors))
            .clickable(onClick = onClick)
            .padding(20.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineLarge,
                    color = Color.White
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = "$count",
                        style = MaterialTheme.typography.displayMedium,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = label,
                        fontSize = 16.sp,
                        color = Color.White.copy(alpha = 0.8f),
                        modifier = Modifier.padding(bottom = 6.dp)
                    )
                }
            }
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .background(Color.White.copy(alpha = 0.2f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = Color.White,
                    modifier = Modifier.size(40.dp)
                )
            }
        }
    }
}

@Composable
fun CategoryCardMedium(
    icon: ImageVector,
    title: String,
    count: Int,
    label: String,
    gradientColors: List<Color>,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .height(160.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Brush.linearGradient(gradientColors))
            .clickable(onClick = onClick)
            .padding(16.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(Color.White.copy(alpha = 0.2f), RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = Color.White,
                    modifier = Modifier.size(28.dp)
                )
            }
            Column {
                Text(
                    text = "$count",
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Text(
                    text = title,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color.White.copy(alpha = 0.9f)
                )
            }
        }
    }
}

@Composable
fun CategoryCardWide(
    icon: ImageVector,
    title: String,
    subtitle: String,
    gradientColors: List<Color>,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(80.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Brush.linearGradient(gradientColors))
            .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .background(Color.White.copy(alpha = 0.1f), RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = title,
                        tint = Color.White,
                        modifier = Modifier.size(28.dp)
                    )
                }
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleLarge,
                        color = Color.White
                    )
                    Text(
                        text = subtitle,
                        fontSize = 13.sp,
                        color = Color.White.copy(alpha = 0.7f)
                    )
                }
            }
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.5f),
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

@Composable
fun SeriesListView(
    series: List<com.sappho.audiobooks.domain.model.SeriesInfo>,
    allBooks: List<com.sappho.audiobooks.domain.model.Audiobook>,
    serverUrl: String?,
    onBackClick: () -> Unit,
    onSeriesClick: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(SapphoBackground)
    ) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.M, vertical = Spacing.M),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBackClick) {
                Icon(
                    imageVector = Icons.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = SapphoText
                )
            }
            Column {
                Text(
                    text = "Series",
                    style = MaterialTheme.typography.headlineLarge,
                    color = SapphoText
                )
                Text(
                    text = "${series.size} series in your library",
                    fontSize = 13.sp,
                    color = SapphoIconDefault
                )
            }
        }

        LazyColumn(
            modifier = Modifier.padding(horizontal = Spacing.M),
            verticalArrangement = Arrangement.spacedBy(Spacing.S)
        ) {
            items(series) { seriesItem ->
                val seriesBooks = allBooks.filter { it.series == seriesItem.series }
                    .sortedBy { it.seriesPosition ?: 0f }
                val totalDuration = seriesBooks.sumOf { it.duration ?: 0 }
                val authors = seriesBooks.mapNotNull { it.author }.distinct()
                val completedCount = seriesBooks.count { it.progress?.completed == 1 }
                val gradientColors = LibraryGradients.forString(seriesItem.series)

                SeriesListCard(
                    seriesName = seriesItem.series,
                    bookCount = seriesItem.bookCount,
                    totalDuration = totalDuration,
                    authors = authors,
                    completedCount = completedCount,
                    books = seriesBooks.take(4),
                    serverUrl = serverUrl,
                    gradientColors = gradientColors,
                    onClick = { onSeriesClick(seriesItem.series) }
                )
            }
            item { Spacer(modifier = Modifier.height(100.dp)) }
        }
    }
}

@Composable
fun SeriesListCard(
    seriesName: String,
    bookCount: Int,
    totalDuration: Int,
    authors: List<String>,
    completedCount: Int,
    books: List<com.sappho.audiobooks.domain.model.Audiobook>,
    serverUrl: String?,
    gradientColors: List<Color>,
    onClick: () -> Unit
) {
    // Darken the gradient colors for better text readability
    val darkenedColors = gradientColors.map { it.copy(alpha = 0.35f) }

    // Bouncy scale animation
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.97f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessHigh
        ),
        label = "series_card_scale"
    )
    val cardTapHaptic = HapticPatterns.cardTap()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .scale(scale)
            .clip(RoundedCornerShape(16.dp))
            .background(SapphoSurfaceLight)
            .background(Brush.horizontalGradient(darkenedColors))
            .clickable(
                interactionSource = interactionSource,
                indication = ripple(),
                onClick = { cardTapHaptic(); onClick() }
            )
            .padding(Spacing.M),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Stacked covers
        Box(
            modifier = Modifier
                .width(80.dp)
                .height(100.dp)
        ) {
            books.take(3).reversed().forEachIndexed { index, book ->
                val offset = (2 - index) * 8
                Box(
                    modifier = Modifier
                        .offset(x = offset.dp, y = offset.dp)
                        .size(60.dp, 80.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(SapphoProgressTrack)
                        .border(1.dp, LegacyGrayDark, RoundedCornerShape(6.dp))
                ) {
                    if (book.coverImage != null && serverUrl != null) {
                        AsyncImage(
                            model = com.sappho.audiobooks.util.buildCoverUrl(serverUrl, book.id, com.sappho.audiobooks.util.COVER_WIDTH_THUMBNAIL),
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Fit
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.width(16.dp))

        // Series info
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = seriesName,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = SapphoText,
                maxLines = 1,
                modifier = Modifier.basicMarquee(
                    iterations = Int.MAX_VALUE,
                    initialDelayMillis = 2000,
                    velocity = 12.dp
                )
            )

            Spacer(modifier = Modifier.height(4.dp))

            if (authors.isNotEmpty()) {
                Text(
                    text = authors.first() + if (authors.size > 1) " +${authors.size - 1}" else "",
                    fontSize = 13.sp,
                    color = SapphoIconDefault,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.MenuBook,
                        contentDescription = null,
                        tint = SapphoInfo,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "$bookCount",
                        fontSize = 12.sp,
                        color = SapphoIconDefault
                    )
                }
                if (completedCount > 0) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = SapphoSuccess,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "$completedCount/$bookCount",
                            fontSize = 12.sp,
                            color = SapphoSuccess
                        )
                    }
                }
                // Average rating from books in the series
                val ratedBooks = books.mapNotNull { it.userRating ?: it.averageRating }
                if (ratedBooks.isNotEmpty()) {
                    val avgRating = ratedBooks.average().toFloat()
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Star,
                            contentDescription = null,
                            tint = SapphoWarning,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = String.format(java.util.Locale.US, "%.1f", avgRating),
                            fontSize = 12.sp,
                            color = SapphoIconDefault
                        )
                    }
                }
            }
        }

        Icon(
            imageVector = Icons.Default.ChevronRight,
            contentDescription = null,
            tint = SapphoTextMuted,
            modifier = Modifier.size(24.dp)
        )
    }
}

@Composable
fun AuthorsListView(
    authors: List<com.sappho.audiobooks.domain.model.AuthorInfo>,
    allBooks: List<com.sappho.audiobooks.domain.model.Audiobook>,
    serverUrl: String?,
    onBackClick: () -> Unit,
    onAuthorClick: (String) -> Unit
) {
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
            Column {
                Text(
                    text = "Authors",
                    style = MaterialTheme.typography.headlineLarge,
                    color = Color.White
                )
                Text(
                    text = "${authors.size} authors in your library",
                    fontSize = 13.sp,
                    color = SapphoIconDefault
                )
            }
        }

        LazyColumn(
            modifier = Modifier.padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(authors) { author ->
                val authorBooks = allBooks.filter { it.author == author.author }
                val totalDuration = authorBooks.sumOf { it.duration ?: 0 }
                val seriesCount = authorBooks.mapNotNull { it.series }.distinct().size
                val genres = authorBooks.mapNotNull { it.genre }.distinct()
                val initials = author.author.split(" ")
                    .take(2)
                    .mapNotNull { it.firstOrNull()?.uppercaseChar() }
                    .joinToString("")
                val gradientColors = LibraryGradients.forString(author.author)

                AuthorListCard(
                    authorName = author.author,
                    initials = initials,
                    bookCount = author.bookCount,
                    seriesCount = seriesCount,
                    totalDuration = totalDuration,
                    genres = genres,
                    gradientColors = gradientColors,
                    recentBooks = authorBooks.take(3),
                    serverUrl = serverUrl,
                    onClick = { onAuthorClick(author.author) }
                )
            }
            item { Spacer(modifier = Modifier.height(100.dp)) }
        }
    }
}

@Composable
fun AuthorListCard(
    authorName: String,
    initials: String,
    bookCount: Int,
    seriesCount: Int,
    totalDuration: Int,
    genres: List<String>,
    gradientColors: List<Color>,
    recentBooks: List<com.sappho.audiobooks.domain.model.Audiobook>,
    serverUrl: String?,
    onClick: () -> Unit
) {
    // Darken the gradient colors for better text readability
    val darkenedColors = gradientColors.map { it.copy(alpha = 0.35f) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(SapphoSurfaceLight)
            .background(Brush.horizontalGradient(darkenedColors))
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Avatar
        Box(
            modifier = Modifier
                .size(56.dp)
                .background(
                    Color.White.copy(alpha = 0.2f),
                    CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = initials,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        }

        Spacer(modifier = Modifier.width(16.dp))

        // Author info
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = authorName,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.White,
                maxLines = 1,
                modifier = Modifier.basicMarquee(
                    iterations = Int.MAX_VALUE,
                    initialDelayMillis = 2000,
                    velocity = 12.dp
                )
            )

            Spacer(modifier = Modifier.height(2.dp))

            if (genres.isNotEmpty()) {
                Text(
                    text = genres.take(2).joinToString(" • "),
                    fontSize = 12.sp,
                    color = SapphoIconDefault,
                    maxLines = 1,
                    modifier = Modifier.basicMarquee(
                        iterations = Int.MAX_VALUE,
                        initialDelayMillis = 2000,
                        velocity = 12.dp
                    )
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

        }

        Icon(
            imageVector = Icons.Default.ChevronRight,
            contentDescription = null,
            tint = SapphoTextMuted,
            modifier = Modifier.size(24.dp)
        )
    }
}

@Composable
fun GenresListView(
    genres: List<String>,
    allBooks: List<com.sappho.audiobooks.domain.model.Audiobook>,
    serverUrl: String?,
    onBackClick: () -> Unit,
    onGenreClick: (String) -> Unit
) {
    // Colors and icons are now fetched from server via LibraryViewModel

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
            Column {
                Text(
                    text = "Genres",
                    style = MaterialTheme.typography.headlineLarge,
                    color = Color.White
                )
                Text(
                    text = "${genres.size} genres in your library",
                    fontSize = 13.sp,
                    color = SapphoIconDefault
                )
            }
        }

        LazyColumn(
            modifier = Modifier.padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(genres.size) { index ->
                val genre = genres[index]
                val genreBooks = allBooks.filter { book ->
                    book.genre?.let { LibraryViewModel.getAllNormalizedGenres(it).contains(genre) } ?: false
                }
                val bookCount = genreBooks.size
                val totalDuration = genreBooks.sumOf { it.duration ?: 0 }
                // Get colors and icon from server data
                val colors = getGenreColorsFromServer(genre)
                val genreIcon = getGenreIconFromServer(genre)

                GenreListCard(
                    genreName = genre,
                    bookCount = bookCount,
                    totalDuration = totalDuration,
                    gradientColors = colors,
                    icon = genreIcon,
                    recentBooks = genreBooks.take(4),
                    serverUrl = serverUrl,
                    onClick = { onGenreClick(genre) }
                )
            }
            item { Spacer(modifier = Modifier.height(100.dp)) }
        }
    }
}

@Composable
fun GenreListCard(
    genreName: String,
    bookCount: Int,
    totalDuration: Int,
    gradientColors: List<Color>,
    icon: ImageVector,
    recentBooks: List<com.sappho.audiobooks.domain.model.Audiobook>,
    serverUrl: String?,
    onClick: () -> Unit
) {
    // Darken the gradient colors for better text readability
    val darkenedColors = gradientColors.map { it.copy(alpha = 0.35f) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(SapphoSurfaceLight)
            .background(Brush.horizontalGradient(darkenedColors))
            .clickable(onClick = onClick)
            .padding(16.dp)
    ) {
        // Top row: Icon, title, stats, chevron
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Icon
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(Color.White.copy(alpha = 0.2f), RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(22.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Genre info
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = genreName,
                    style = MaterialTheme.typography.titleLarge,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "$bookCount books",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color.White.copy(alpha = 0.9f)
                    )
                    Text(
                        text = "•",
                        fontSize = 13.sp,
                        color = Color.White.copy(alpha = 0.7f)
                    )
                    Text(
                        text = "${totalDuration / 3600}h ${(totalDuration % 3600) / 60}m",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color.White.copy(alpha = 0.9f)
                    )
                }
            }

            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.7f),
                modifier = Modifier.size(24.dp)
            )
        }

        // Book covers below
        if (recentBooks.isNotEmpty()) {
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                recentBooks.take(5).forEach { book ->
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color.Black.copy(alpha = 0.3f))
                            .border(1.dp, Color.White.copy(alpha = 0.2f), RoundedCornerShape(8.dp))
                    ) {
                        if (book.coverImage != null && serverUrl != null) {
                            AsyncImage(
                                model = com.sappho.audiobooks.util.buildCoverUrl(serverUrl, book.id, com.sappho.audiobooks.util.COVER_WIDTH_THUMBNAIL),
                                contentDescription = null,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Fit
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ListItemCard(
    title: String,
    subtitle: String?,
    icon: ImageVector,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(SapphoSurfaceLight)
            .clickable(onClick = onClick)
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .background(SapphoProgressTrack, RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = SapphoIconDefault,
                    modifier = Modifier.size(24.dp)
                )
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (subtitle != null) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = subtitle,
                        fontSize = 13.sp,
                        color = SapphoIconDefault
                    )
                }
            }
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = SapphoTextMuted,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
fun SeriesBooksView(
    seriesName: String,
    books: List<com.sappho.audiobooks.domain.model.Audiobook>,
    totalDuration: Int,
    authors: List<String>,
    serverUrl: String?,
    aiConfigured: Boolean = false,
    viewModel: LibraryViewModel? = null,
    onBackClick: () -> Unit,
    onBookClick: (Int) -> Unit
) {
    val completedBooks = books.count { it.progress?.completed == 1 }
    // For completed books, count their full duration; for in-progress, count position
    val totalProgress = books.sumOf { book ->
        if (book.progress?.completed == 1) {
            book.duration ?: 0
        } else {
            book.progress?.position ?: 0
        }
    }
    val overallProgress = if (totalDuration > 0) {
        (totalProgress.toFloat() / totalDuration.toFloat()).coerceIn(0f, 1f)
    } else 0f

    // Catch Me Up state
    val hasProgress = books.any { (it.progress?.position ?: 0) > 0 || it.progress?.completed == 1 }
    var showRecap by remember { mutableStateOf(false) }
    var recapLoading by remember { mutableStateOf(false) }
    var recapError by remember { mutableStateOf<String?>(null) }
    var recapData by remember { mutableStateOf<com.sappho.audiobooks.data.remote.SeriesRecapResponse?>(null) }
    var recapExpanded by remember { mutableStateOf(true) }
    val coroutineScope = rememberCoroutineScope()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(SapphoBackground)
    ) {
        // Header with back button + Catch Me Up icon
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                SapphoSurfaceLight,
                                SapphoBackground
                            )
                        )
                    )
                    .padding(8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val buttonPressHaptic = HapticPatterns.buttonPress()
                IconButton(
                    onClick = { buttonPressHaptic(); onBackClick() },
                    modifier = Modifier
                        .background(Color.Black.copy(alpha = 0.3f), CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = SapphoText
                    )
                }

                // Catch Me Up icon button (top-right corner)
                if (aiConfigured && hasProgress && viewModel != null &&
                    !showRecap && !recapLoading && recapError == null && recapData == null
                ) {
                    IconButton(
                        onClick = {
                            buttonPressHaptic()
                            showRecap = true
                            recapLoading = true
                            recapError = null
                            coroutineScope.launch {
                                val result = viewModel.getSeriesRecap(seriesName)
                                result.onSuccess { data ->
                                    recapData = data
                                    recapLoading = false
                                }.onFailure { error ->
                                    recapError = error.message ?: "Failed to generate recap"
                                    recapLoading = false
                                }
                            }
                        },
                        modifier = Modifier
                            .background(SapphoAccent.copy(alpha = 0.3f), CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Default.AutoStories,
                            contentDescription = "Catch Me Up",
                            tint = SapphoAccentLight,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }

        // Series Info Section — title, author, rating, catch me up
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(top = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = seriesName,
                    style = MaterialTheme.typography.headlineLarge,
                    color = SapphoText,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(8.dp))

                if (authors.isNotEmpty()) {
                    Text(
                        text = "by ${authors.joinToString(", ")}",
                        fontSize = 14.sp,
                        color = SapphoIconDefault,
                        textAlign = TextAlign.Center
                    )
                }

                // Rating inline under title/author
                val ratedBooks = books.mapNotNull { it.userRating ?: it.averageRating }
                val seriesAvgRating = if (ratedBooks.isNotEmpty()) ratedBooks.average().toFloat() else null

                if (seriesAvgRating != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Star,
                            contentDescription = null,
                            tint = SapphoWarning,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = String.format(java.util.Locale.US, "%.1f", seriesAvgRating),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = SapphoText
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "(${ratedBooks.size} rated)",
                            fontSize = 12.sp,
                            color = SapphoTextMuted
                        )
                    }
                }

            }
        }

        // Catch Me Up expanded content (loading, error, recap)
        if (aiConfigured && hasProgress && viewModel != null && (recapLoading || recapError != null || recapData != null)) {
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .padding(top = 12.dp)
                ) {
                    // Loading State
                    if (recapLoading) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(SapphoAccent.copy(alpha = 0.1f))
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = SapphoAccentLight,
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = "Generating your personalized recap...",
                                color = SapphoAccentLight,
                                fontSize = 14.sp
                            )
                        }
                    }

                    // Error State
                    if (recapError != null) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(SapphoError.copy(alpha = 0.1f))
                                .padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = null,
                                tint = LegacyRedLight,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = recapError!!,
                                color = SapphoErrorLight,
                                fontSize = 14.sp,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            TextButton(
                                onClick = {
                                    recapError = null
                                    recapLoading = true
                                    coroutineScope.launch {
                                        val result = viewModel.getSeriesRecap(seriesName)
                                        result.onSuccess { data ->
                                            recapData = data
                                            recapLoading = false
                                        }.onFailure { error ->
                                            recapError = error.message ?: "Failed to generate recap"
                                            recapLoading = false
                                        }
                                    }
                                },
                                colors = ButtonDefaults.textButtonColors(
                                    contentColor = SapphoErrorLight
                                )
                            ) {
                                Text("Try Again")
                            }
                        }
                    }

                    // Recap Content
                    if (recapData != null) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .background(SapphoSurfaceLight.copy(alpha = 0.8f))
                        ) {
                            // Header
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { recapExpanded = !recapExpanded }
                                    .padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.AutoStories,
                                    contentDescription = null,
                                    tint = SapphoAccentLight,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    text = "Series Recap",
                                    color = SapphoText,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.weight(1f)
                                )
                                if (recapData!!.cached) {
                                    Text(
                                        text = "Cached",
                                        fontSize = 10.sp,
                                        color = SapphoAccentLight,
                                        modifier = Modifier
                                            .background(
                                                SapphoAccent.copy(alpha = 0.2f),
                                                RoundedCornerShape(4.dp)
                                            )
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                }
                                Icon(
                                    imageVector = if (recapExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                    contentDescription = null,
                                    tint = SapphoIconDefault,
                                    modifier = Modifier.size(24.dp)
                                )
                            }

                            // Expanded content
                            if (recapExpanded) {
                                Divider(color = Color.White.copy(alpha = 0.05f))
                                Column(
                                    modifier = Modifier.padding(16.dp)
                                ) {
                                    // Books included
                                    Text(
                                        text = "Based on: ${recapData!!.booksIncluded.joinToString(", ") {
                                            (if (it.position != null) "#${formatSeriesPosition(it.position)} " else "") + it.title
                                        }}",
                                        fontSize = 12.sp,
                                        color = SapphoIconDefault,
                                        lineHeight = 16.sp
                                    )

                                    Spacer(modifier = Modifier.height(12.dp))

                                    // Recap text
                                    Text(
                                        text = recapData!!.recap,
                                        fontSize = 14.sp,
                                        color = SapphoTextLight,
                                        lineHeight = 22.sp
                                    )

                                    Spacer(modifier = Modifier.height(16.dp))

                                    // Regenerate button
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.End
                                    ) {
                                        TextButton(
                                            onClick = {
                                                recapLoading = true
                                                recapData = null
                                                coroutineScope.launch {
                                                    viewModel.clearSeriesRecap(seriesName)
                                                    val result = viewModel.getSeriesRecap(seriesName)
                                                    result.onSuccess { data ->
                                                        recapData = data
                                                        recapLoading = false
                                                    }.onFailure { error ->
                                                        recapError = error.message ?: "Failed to generate recap"
                                                        recapLoading = false
                                                    }
                                                }
                                            },
                                            colors = ButtonDefaults.textButtonColors(
                                                contentColor = SapphoIconDefault
                                            )
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Refresh,
                                                contentDescription = null,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("Regenerate", fontSize = 13.sp)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Stats + Progress section
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(top = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Stats cards
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    StatCard(
                        value = "${books.size}",
                        label = if (books.size == 1) "Book" else "Books",
                        icon = Icons.Default.MenuBook,
                        modifier = Modifier.weight(1f)
                    )
                    StatCard(
                        value = "${totalDuration / 3600}h",
                        label = "Total",
                        icon = Icons.Default.Schedule,
                        modifier = Modifier.weight(1f)
                    )
                    StatCard(
                        value = "$completedBooks/${books.size}",
                        label = "Complete",
                        icon = Icons.Default.CheckCircle,
                        modifier = Modifier.weight(1f)
                    )
                }

                // Overall progress bar with animation
                if (overallProgress > 0 || completedBooks > 0) {
                    val animatedOverallProgress by animateFloatAsState(
                        targetValue = overallProgress,
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioMediumBouncy,
                            stiffness = Spring.StiffnessLow
                        ),
                        label = "series_progress"
                    )

                    Spacer(modifier = Modifier.height(Spacing.M))
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(SapphoSurfaceLight)
                            .padding(Spacing.M)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Series Progress",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                color = SapphoText
                            )
                            Text(
                                text = "${(overallProgress * 100).toInt()}%",
                                fontSize = 13.sp,
                                color = SapphoInfo
                            )
                        }
                        Spacer(modifier = Modifier.height(Spacing.XS))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp))
                                .background(SapphoProgressTrack)
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .fillMaxWidth(animatedOverallProgress)
                                    .background(
                                        Brush.horizontalGradient(
                                            listOf(SapphoInfo, LegacyBlueLight)
                                        )
                                    )
                            )
                        }
                    }
                }
            }
        }

        // Books Section Header
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.M)
                    .padding(top = Spacing.L, bottom = Spacing.S),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Books in Series",
                    style = MaterialTheme.typography.titleLarge,
                    color = SapphoText
                )
            }
        }

        // Book cover grid - display as rows of 3
        val chunkedBooks = books.chunked(3)
        items(chunkedBooks) { rowBooks ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.M)
                    .padding(bottom = Spacing.S),
                horizontalArrangement = Arrangement.spacedBy(Spacing.XS)
            ) {
                rowBooks.forEach { book ->
                    Box(modifier = Modifier.weight(1f)) {
                        SeriesBookGridItem(
                            book = book,
                            serverUrl = serverUrl,
                            onClick = { onBookClick(book.id) }
                        )
                    }
                }
                // Fill remaining space if row is incomplete
                repeat(3 - rowBooks.size) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }

        item { Spacer(modifier = Modifier.height(100.dp)) }
    }
}

@Composable
fun StatCard(
    value: String,
    label: String,
    icon: ImageVector,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(SapphoSurfaceLight)
            .padding(Spacing.S),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = SapphoInfo,
            modifier = Modifier.size(IconSize.Medium)
        )
        Spacer(modifier = Modifier.height(Spacing.XXS))
        Text(
            text = value,
            style = MaterialTheme.typography.titleLarge,
            color = SapphoText
        )
        Text(
            text = label,
            fontSize = 11.sp,
            color = SapphoIconDefault
        )
    }
}

@Composable
fun SeriesBookListItem(
    book: com.sappho.audiobooks.domain.model.Audiobook,
    serverUrl: String?,
    onClick: () -> Unit
) {
    val progress = book.progress
    val isCompleted = progress?.completed == 1
    val progressPercent = if (isCompleted) 1f
        else if (progress != null && book.duration != null && book.duration > 0) {
            (progress.position.toFloat() / book.duration.toFloat()).coerceIn(0f, 1f)
        } else 0f

    // Bouncy scale animation
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val itemScale by animateFloatAsState(
        targetValue = if (isPressed) 0.97f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessHigh
        ),
        label = "book_item_scale"
    )
    val cardTapHaptic = HapticPatterns.cardTap()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.M, vertical = 6.dp)
            .scale(itemScale)
            .clip(RoundedCornerShape(12.dp))
            .background(SapphoSurfaceLight)
            .clickable(
                interactionSource = interactionSource,
                indication = ripple(),
                onClick = { cardTapHaptic(); onClick() }
            )
            .padding(Spacing.S),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Book number badge - subtle style
        Box(
            modifier = Modifier
                .size(28.dp)
                .background(
                    if (isCompleted) SapphoSuccess.copy(alpha = 0.2f) else Color.White.copy(alpha = 0.08f),
                    RoundedCornerShape(6.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            if (isCompleted) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = "Completed",
                    tint = SapphoSuccess,
                    modifier = Modifier.size(16.dp)
                )
            } else {
                Text(
                    text = formatSeriesPosition(book.seriesPosition) ?: "?",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = SapphoIconDefault
                )
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        // Book info
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = book.title,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                color = SapphoText,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(Spacing.XXS))
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (book.duration != null) {
                    Text(
                        text = "${book.duration / 3600}h ${(book.duration % 3600) / 60}m",
                        fontSize = 12.sp,
                        color = SapphoIconDefault
                    )
                }
                if (progressPercent > 0 && !isCompleted) {
                    Text(
                        text = " • ${(progressPercent * 100).toInt()}%",
                        fontSize = 12.sp,
                        color = SapphoInfo
                    )
                }
                if (book.isFavorite) {
                    Text(text = " • ", fontSize = 12.sp, color = SapphoIconDefault)
                    Icon(
                        imageVector = Icons.Filled.BookmarkAdded,
                        contentDescription = "On reading list",
                        tint = SapphoInfo,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }

            // Animated progress bar
            if (progressPercent > 0) {
                val animatedBookProgress by animateFloatAsState(
                    targetValue = progressPercent,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessLow
                    ),
                    label = "book_progress"
                )

                Spacer(modifier = Modifier.height(6.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(SapphoProgressTrack)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(animatedBookProgress)
                            .background(
                                if (isCompleted) SapphoSuccess else SapphoInfo
                            )
                    )
                }
            }
        }

        Spacer(modifier = Modifier.width(8.dp))

        Icon(
            imageVector = Icons.Default.ChevronRight,
            contentDescription = null,
            tint = SapphoTextMuted,
            modifier = Modifier.size(20.dp)
        )
    }
}

@Composable
fun SeriesBookGridItem(
    book: com.sappho.audiobooks.domain.model.Audiobook,
    serverUrl: String?,
    onClick: () -> Unit
) {
    val progress = book.progress
    val isCompleted = progress?.completed == 1
    val progressPercent = if (isCompleted) 1f
        else if (progress != null && book.duration != null && book.duration > 0) {
            (progress.position.toFloat() / book.duration.toFloat()).coerceIn(0f, 1f)
        } else 0f

    // Bouncy scale animation
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val itemScale by animateFloatAsState(
        targetValue = if (isPressed) 0.95f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessHigh
        ),
        label = "series_grid_item_scale"
    )
    val cardTapHaptic = HapticPatterns.cardTap()

    Column(
        modifier = Modifier
            .scale(itemScale)
            .clickable(
                interactionSource = interactionSource,
                indication = ripple(),
                onClick = { cardTapHaptic(); onClick() }
            )
    ) {
        Box(
            modifier = Modifier
                .aspectRatio(1f)
                .clip(RoundedCornerShape(8.dp))
                .background(SapphoProgressTrack)
        ) {
            if (book.coverImage != null && serverUrl != null) {
                AsyncImage(
                    model = com.sappho.audiobooks.util.buildCoverUrl(serverUrl, book.id, com.sappho.audiobooks.util.COVER_WIDTH_THUMBNAIL),
                    contentDescription = book.title,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit
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
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = SapphoText
                    )
                }
            }

            // Series position badge (top-left)
            if (book.seriesPosition != null) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(6.dp)
                        .background(SapphoInfo, RoundedCornerShape(4.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "#${formatSeriesPosition(book.seriesPosition)}",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }

            // Completed badge (top-right)
            if (isCompleted) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(4.dp)
                        .size(20.dp)
                        .background(SapphoSuccess, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "Completed",
                        tint = Color.White,
                        modifier = Modifier.size(12.dp)
                    )
                }
            }

            // Reading list ribbon
            if (book.isFavorite && !isCompleted) {
                ReadingListRibbon(
                    modifier = Modifier.align(Alignment.TopEnd),
                    size = 28f
                )
            }

            // Animated progress bar (bottom)
            if (progressPercent > 0 && !isCompleted) {
                val animatedProgress by animateFloatAsState(
                    targetValue = progressPercent,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessLow
                    ),
                    label = "series_grid_progress"
                )

                Box(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth()
                        .height(4.dp)
                        .background(Color.Black.copy(alpha = 0.7f))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(animatedProgress)
                            .background(
                                Brush.horizontalGradient(listOf(SapphoInfo, LegacyBlueLight))
                            )
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Title (marquee for long names)
        Text(
            text = book.title,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            color = SapphoText,
            maxLines = 1,
            lineHeight = 14.sp,
            modifier = Modifier.basicMarquee(
                iterations = Int.MAX_VALUE,
                initialDelayMillis = 2000,
                velocity = 12.dp
            )
        )

        // Rating - prefer user rating, fall back to average
        val displayRating = book.userRating ?: book.averageRating
        if (displayRating != null && displayRating > 0) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(top = 2.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Star,
                    contentDescription = null,
                    tint = SapphoStarFilled,
                    modifier = Modifier.size(12.dp)
                )
                Spacer(modifier = Modifier.width(2.dp))
                Text(
                    text = String.format(java.util.Locale.US, "%.1f", displayRating),
                    fontSize = 11.sp,
                    color = SapphoStarFilled
                )
            }
        }
    }
}

@Composable
fun AuthorBooksView(
    authorName: String,
    books: List<com.sappho.audiobooks.domain.model.Audiobook>,
    serverUrl: String?,
    onBackClick: () -> Unit,
    onBookClick: (Int) -> Unit
) {
    val totalDuration = books.sumOf { it.duration ?: 0 }
    val seriesList = books.mapNotNull { it.series }.distinct()
    val genres = books.mapNotNull { it.genre }.distinct()
    val completedBooks = books.count { it.progress?.completed == 1 }

    // Group books by series
    val booksBySeries = books
        .filter { it.series != null }
        .groupBy { it.series!! }
        .mapValues { entry -> entry.value.sortedBy { it.seriesPosition ?: 0f } }
    val standaloneBooks = books.filter { it.series == null }

    // Generate initials and color from author name
    val initials = authorName.split(" ")
        .take(2)
        .mapNotNull { it.firstOrNull()?.uppercaseChar() }
        .joinToString("")
    val avatarColors = LibraryGradients.avatars
    val colorIndex = authorName.hashCode().let { kotlin.math.abs(it) % avatarColors.size }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(SapphoBackground)
    ) {
        // Hero Section with avatar on left, name on right
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                avatarColors[colorIndex][0].copy(alpha = 0.4f),
                                SapphoBackground
                            )
                        )
                    )
            ) {
                // Back button row
                IconButton(
                    onClick = onBackClick,
                    modifier = Modifier
                        .padding(8.dp)
                        .background(Color.Black.copy(alpha = 0.3f), CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = Color.White
                    )
                }

                // Avatar and name row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .padding(bottom = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Avatar on left
                    Box(
                        modifier = Modifier
                            .size(80.dp)
                            .background(
                                Brush.linearGradient(avatarColors[colorIndex]),
                                CircleShape
                            )
                            .border(3.dp, Color.White.copy(alpha = 0.2f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = initials,
                            fontSize = 28.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }

                    Spacer(modifier = Modifier.width(16.dp))

                    // Name on right
                    Text(
                        text = authorName,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }
        }

        // Stats row
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                AuthorStatCard(
                    value = "${books.size}",
                    label = if (books.size == 1) "Book" else "Books",
                    modifier = Modifier.weight(1f)
                )
                AuthorStatCard(
                    value = "${seriesList.size}",
                    label = if (seriesList.size == 1) "Series" else "Series",
                    modifier = Modifier.weight(1f)
                )
                AuthorStatCard(
                    value = "${totalDuration / 3600}h",
                    label = "Total Time",
                    modifier = Modifier.weight(1f)
                )
                AuthorStatCard(
                    value = "$completedBooks",
                    label = "Finished",
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // Series section header
        if (booksBySeries.isNotEmpty()) {
            item {
                Text(
                    text = "Series",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = SapphoText,
                    modifier = Modifier
                        .padding(horizontal = 16.dp)
                        .padding(top = 24.dp)
                )
            }
        }

        // Series sections (cover grid)
        booksBySeries.forEach { (seriesName, seriesBooks) ->
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .padding(top = 24.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = seriesName,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = SapphoText
                            )
                            Text(
                                text = "${seriesBooks.size} ${if (seriesBooks.size == 1) "book" else "books"} in series",
                                fontSize = 12.sp,
                                color = SapphoIconDefault
                            )
                        }
                    }
                }
            }

            val chunkedSeriesBooks = seriesBooks.chunked(3)
            items(chunkedSeriesBooks) { rowBooks ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .padding(top = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    rowBooks.forEach { book ->
                        Box(modifier = Modifier.weight(1f)) {
                            SeriesBookGridItem(
                                book = book,
                                serverUrl = serverUrl,
                                onClick = { onBookClick(book.id) }
                            )
                        }
                    }
                    repeat(3 - rowBooks.size) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }

        // Standalone books section (cover grid)
        if (standaloneBooks.isNotEmpty()) {
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .padding(top = 24.dp)
                ) {
                    Text(
                        text = "Standalone Books",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = SapphoText
                    )
                    Text(
                        text = "${standaloneBooks.size} ${if (standaloneBooks.size == 1) "book" else "books"}",
                        fontSize = 12.sp,
                        color = SapphoIconDefault
                    )
                }
            }

            val chunkedStandaloneBooks = standaloneBooks.sortedBy { it.title }.chunked(3)
            items(chunkedStandaloneBooks) { rowBooks ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .padding(top = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    rowBooks.forEach { book ->
                        Box(modifier = Modifier.weight(1f)) {
                            SeriesBookGridItem(
                                book = book,
                                serverUrl = serverUrl,
                                onClick = { onBookClick(book.id) }
                            )
                        }
                    }
                    repeat(3 - rowBooks.size) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }

        item { Spacer(modifier = Modifier.height(100.dp)) }
    }
}

@Composable
fun AuthorStatCard(
    value: String,
    label: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(SapphoSurfaceLight)
            .padding(vertical = 12.dp, horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = value,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = SapphoText
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = label,
            fontSize = 10.sp,
            color = SapphoIconDefault,
            maxLines = 1
        )
    }
}

@Composable
fun AuthorBookCard(
    book: com.sappho.audiobooks.domain.model.Audiobook,
    serverUrl: String?,
    showSeriesPosition: Boolean,
    onClick: () -> Unit
) {
    val progress = book.progress
    val isCompleted = progress?.completed == 1
    val progressPercent = if (isCompleted) 1f
        else if (progress != null && book.duration != null && book.duration > 0) {
            (progress.position.toFloat() / book.duration.toFloat()).coerceIn(0f, 1f)
        } else 0f

    Column(
        modifier = Modifier
            .width(120.dp)
            .clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .size(120.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(SapphoProgressTrack)
        ) {
            if (book.coverImage != null && serverUrl != null) {
                AsyncImage(
                    model = com.sappho.audiobooks.util.buildCoverUrl(serverUrl, book.id, com.sappho.audiobooks.util.COVER_WIDTH_THUMBNAIL),
                    contentDescription = book.title,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit
                )
            } else {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = book.title.take(2).uppercase(),
                        style = MaterialTheme.typography.headlineLarge,
                        color = Color.White
                    )
                }
            }

            // Series position badge
            if (showSeriesPosition && book.seriesPosition != null) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(6.dp)
                        .background(SapphoInfo, RoundedCornerShape(4.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "#${formatSeriesPosition(book.seriesPosition)}",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }

            // Progress indicator
            if (progressPercent > 0) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth()
                        .height(4.dp)
                        .background(Color.Black.copy(alpha = 0.7f))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(progressPercent)
                            .background(
                                if (isCompleted) SapphoSuccess else SapphoInfo
                            )
                    )
                }
            }
        }

    }
}

@Composable
fun GenreBooksView(
    genreName: String,
    books: List<com.sappho.audiobooks.domain.model.Audiobook>,
    serverUrl: String?,
    onBackClick: () -> Unit,
    onBookClick: (Int) -> Unit
) {
    val totalDuration = books.sumOf { it.duration ?: 0 }
    val authors = books.mapNotNull { it.author }.distinct()
    val seriesCount = books.mapNotNull { it.series }.distinct().size
    val completedBooks = books.count { it.progress?.completed == 1 }

    // Get colors and icon from server data
    val colors = getGenreColorsFromServer(genreName)
    val genreIcon = getGenreIconFromServer(genreName)

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(SapphoBackground)
    ) {
        // Hero Section
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
            ) {
                // Gradient background
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    colors[0],
                                    colors[1],
                                    SapphoBackground
                                ),
                                startY = 0f,
                                endY = Float.POSITIVE_INFINITY
                            )
                        )
                )

                // Back button
                IconButton(
                    onClick = onBackClick,
                    modifier = Modifier
                        .padding(8.dp)
                        .background(Color.Black.copy(alpha = 0.3f), CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = Color.White
                    )
                }

                // Genre icon and name
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 50.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Icon container
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .background(Color.White.copy(alpha = 0.2f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = genreIcon,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(36.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = genreName,
                        fontSize = 26.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        textAlign = TextAlign.Center
                    )

                    Text(
                        text = "${books.size} ${if (books.size == 1) "audiobook" else "audiobooks"}",
                        fontSize = 14.sp,
                        color = Color.White.copy(alpha = 0.8f)
                    )
                }
            }
        }

        // Stats Cards
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(top = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                GenreStatCard(
                    value = "${authors.size}",
                    label = "Authors",
                    icon = Icons.Default.Person,
                    accentColor = colors[0],
                    modifier = Modifier.weight(1f)
                )
                GenreStatCard(
                    value = "$seriesCount",
                    label = "Series",
                    icon = Icons.Default.MenuBook,
                    accentColor = colors[0],
                    modifier = Modifier.weight(1f)
                )
                GenreStatCard(
                    value = "${totalDuration / 3600}h",
                    label = "Total",
                    icon = Icons.Default.Schedule,
                    accentColor = colors[0],
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // Featured Authors
        if (authors.isNotEmpty()) {
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .padding(top = 24.dp)
                ) {
                    Text(
                        text = "Featured Authors",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        authors.take(4).forEach { author ->
                            val initials = author.split(" ")
                                .take(2)
                                .mapNotNull { it.firstOrNull()?.uppercaseChar() }
                                .joinToString("")
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .background(colors[0].copy(alpha = 0.3f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = initials,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color.White
                                )
                            }
                        }
                        if (authors.size > 4) {
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .background(SapphoProgressTrack, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "+${authors.size - 4}",
                                    fontSize = 12.sp,
                                    color = SapphoIconDefault
                                )
                            }
                        }
                    }
                }
            }
        }

        // All Books Section
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(top = 24.dp, bottom = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "All Books",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White
                )
                Text(
                    text = "${completedBooks} finished",
                    fontSize = 12.sp,
                    color = SapphoSuccess
                )
            }
        }

        // Book Grid
        val rows = books.chunked(3)
        items(rows) { rowBooks ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                rowBooks.forEach { book ->
                    Box(modifier = Modifier.weight(1f)) {
                        GenreBookGridItem(
                            book = book,
                            serverUrl = serverUrl,
                            onClick = { onBookClick(book.id) }
                        )
                    }
                }
                // Fill remaining space if row is incomplete
                repeat(3 - rowBooks.size) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }

        item { Spacer(modifier = Modifier.height(100.dp)) }
    }
}

@Composable
fun GenreStatCard(
    value: String,
    label: String,
    icon: ImageVector,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(SapphoSurfaceLight)
            .padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = accentColor,
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.titleLarge,
            color = Color.White
        )
        Text(
            text = label,
            fontSize = 10.sp,
            color = SapphoIconDefault
        )
    }
}

@Composable
fun GenreBookGridItem(
    book: com.sappho.audiobooks.domain.model.Audiobook,
    serverUrl: String?,
    onClick: () -> Unit
) {
    val progress = book.progress
    val isCompleted = progress?.completed == 1
    val progressPercent = if (isCompleted) 1f
        else if (progress != null && book.duration != null && book.duration > 0) {
            (progress.position.toFloat() / book.duration.toFloat()).coerceIn(0f, 1f)
        } else 0f

    Column(
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .aspectRatio(1f)
                .clip(RoundedCornerShape(8.dp))
                .background(SapphoProgressTrack)
        ) {
            if (book.coverImage != null && serverUrl != null) {
                AsyncImage(
                    model = com.sappho.audiobooks.util.buildCoverUrl(serverUrl, book.id, com.sappho.audiobooks.util.COVER_WIDTH_THUMBNAIL),
                    contentDescription = book.title,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit
                )
            } else {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = book.title.take(2).uppercase(),
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }

            // Completed badge
            if (isCompleted) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(4.dp)
                        .size(20.dp)
                        .background(SapphoSuccess, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "Completed",
                        tint = Color.White,
                        modifier = Modifier.size(12.dp)
                    )
                }
            }

            // Reading list ribbon (top-right corner)
            if (book.isFavorite) {
                ReadingListRibbon(
                    modifier = Modifier.align(Alignment.TopEnd),
                    size = 28f
                )
            }

            // Progress bar
            if (progressPercent > 0 && !isCompleted) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth()
                        .height(4.dp)
                        .background(Color.Black.copy(alpha = 0.7f))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(progressPercent)
                            .background(SapphoInfo)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = book.title,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            color = SapphoText,
            maxLines = 1,
            lineHeight = 14.sp,
            modifier = Modifier.basicMarquee(
                iterations = Int.MAX_VALUE,
                initialDelayMillis = 2000,
                velocity = 12.dp
            )
        )

        book.author?.let {
            Text(
                text = it,
                fontSize = 10.sp,
                color = SapphoIconDefault,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        // Rating
        val displayRating = book.userRating ?: book.averageRating
        if (displayRating != null && displayRating > 0) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(top = 2.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Star,
                    contentDescription = null,
                    tint = SapphoStarFilled,
                    modifier = Modifier.size(12.dp)
                )
                Spacer(modifier = Modifier.width(2.dp))
                Text(
                    text = String.format(java.util.Locale.US, "%.1f", displayRating),
                    fontSize = 11.sp,
                    color = SapphoStarFilled
                )
            }
        }
    }
}

@Composable
fun BookGridItem(
    book: com.sappho.audiobooks.domain.model.Audiobook,
    serverUrl: String?,
    showSeriesPosition: Boolean = false,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .aspectRatio(1f)
                .clip(RoundedCornerShape(8.dp))
        ) {
            // Cover Image
            if (book.coverImage != null && serverUrl != null) {
                AsyncImage(
                    model = com.sappho.audiobooks.util.buildCoverUrl(serverUrl, book.id, com.sappho.audiobooks.util.COVER_WIDTH_THUMBNAIL),
                    contentDescription = book.title,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit
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
                        style = MaterialTheme.typography.headlineLarge,
                        color = Color.White
                    )
                }
            }

            // Series position badge
            if (showSeriesPosition && book.seriesPosition != null) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(6.dp)
                        .background(SapphoInfo, RoundedCornerShape(4.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "#${formatSeriesPosition(book.seriesPosition)}",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }

            // Reading list ribbon (top-right corner)
            if (book.isFavorite) {
                ReadingListRibbon(
                    modifier = Modifier.align(Alignment.TopEnd),
                    size = 32f
                )
            }

            // Progress bar
            val progress = book.progress
            if (progress != null && (progress.position > 0 || progress.completed == 1) && book.duration != null) {
                val progressPercent = if (progress.completed == 1) 1f
                    else (progress.position.toFloat() / book.duration.toFloat()).coerceIn(0f, 1f)

                Box(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth()
                        .height(5.dp)
                        .background(Color.Black.copy(alpha = 0.7f))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(progressPercent)
                            .background(
                                if (progress.completed == 1)
                                    Brush.horizontalGradient(listOf(SapphoSuccess, LegacyGreenLight))
                                else
                                    Brush.horizontalGradient(listOf(SapphoInfo, LegacyBlueLight))
                            )
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Title
        Text(
            text = book.title,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            color = SapphoText,
            maxLines = 1,
            lineHeight = 14.sp,
            modifier = Modifier.basicMarquee(
                iterations = Int.MAX_VALUE,
                initialDelayMillis = 2000,
                velocity = 12.dp
            )
        )

        // Author
        book.author?.let {
            Text(
                text = it,
                fontSize = 10.sp,
                color = SapphoIconDefault,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        // Rating
        val displayRating = book.userRating ?: book.averageRating
        if (displayRating != null && displayRating > 0) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(top = 2.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Star,
                    contentDescription = null,
                    tint = SapphoStarFilled,
                    modifier = Modifier.size(12.dp)
                )
                Spacer(modifier = Modifier.width(2.dp))
                Text(
                    text = String.format(java.util.Locale.US, "%.1f", displayRating),
                    fontSize = 11.sp,
                    color = SapphoStarFilled
                )
            }
        }
    }
}

@Composable
fun AllBooksView(
    viewModel: LibraryViewModel,
    onBackClick: () -> Unit,
    onAudiobookClick: (Int) -> Unit = {},
    isAdmin: Boolean = false
) {
    val allBooks by viewModel.allAudiobooks.collectAsState()
    val serverUrl by viewModel.serverUrl.collectAsState()
    val sortOption by viewModel.userPreferences.librarySortOption.collectAsState()
    val sortAscending by viewModel.userPreferences.librarySortAscending.collectAsState()
    val filterOption by viewModel.userPreferences.libraryFilterOption.collectAsState()
    val isSelectionMode by viewModel.isSelectionMode.collectAsState()
    val selectedBookIds by viewModel.selectedBookIds.collectAsState()
    val collections by viewModel.collections.collectAsState()

    var showBatchCollectionDialog by remember { mutableStateOf(false) }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    // Filter audiobooks using the persisted filter option
    val filteredBooks = remember(allBooks, filterOption) {
        allBooks.filter { book ->
            val isFinished = book.progress?.completed == 1
            val hasProgress = book.progress != null && book.progress.position > 0

            when (filterOption) {
                com.sappho.audiobooks.data.repository.LibraryFilterOption.HIDE_FINISHED -> !isFinished
                com.sappho.audiobooks.data.repository.LibraryFilterOption.IN_PROGRESS -> hasProgress && !isFinished
                com.sappho.audiobooks.data.repository.LibraryFilterOption.NOT_STARTED -> !hasProgress && !isFinished
                com.sappho.audiobooks.data.repository.LibraryFilterOption.FINISHED -> isFinished
                com.sappho.audiobooks.data.repository.LibraryFilterOption.ALL -> true
            }
        }
    }

    // Sort audiobooks using the persisted sort option
    val sortedBooks = remember(filteredBooks, sortOption, sortAscending) {
        val comparator: Comparator<com.sappho.audiobooks.domain.model.Audiobook> = when (sortOption) {
            com.sappho.audiobooks.data.repository.LibrarySortOption.TITLE -> compareBy { it.title.lowercase() }
            com.sappho.audiobooks.data.repository.LibrarySortOption.AUTHOR -> compareBy { it.author?.lowercase() ?: "" }
            com.sappho.audiobooks.data.repository.LibrarySortOption.RECENTLY_ADDED -> compareByDescending { it.createdAt }
            com.sappho.audiobooks.data.repository.LibrarySortOption.RECENTLY_LISTENED -> compareByDescending { it.progress?.lastListened ?: "" }
            com.sappho.audiobooks.data.repository.LibrarySortOption.DURATION -> compareBy { it.duration ?: 0 }
            com.sappho.audiobooks.data.repository.LibrarySortOption.PROGRESS -> compareByDescending {
                val pos = it.progress?.position ?: 0
                val dur = it.duration ?: 1
                if (dur > 0) pos.toFloat() / dur else 0f
            }
            com.sappho.audiobooks.data.repository.LibrarySortOption.SERIES_POSITION -> compareBy<com.sappho.audiobooks.domain.model.Audiobook> { it.series ?: "\uFFFF" }
                .thenBy { it.seriesPosition ?: 0f }
            com.sappho.audiobooks.data.repository.LibrarySortOption.RATING -> compareByDescending { it.userRating ?: it.averageRating ?: 0f }
        }
        val sorted = filteredBooks.sortedWith(comparator)
        // Options with descending comparators need reverse logic
        val isNaturallyDescending = sortOption in listOf(
            com.sappho.audiobooks.data.repository.LibrarySortOption.RECENTLY_ADDED,
            com.sappho.audiobooks.data.repository.LibrarySortOption.RECENTLY_LISTENED,
            com.sappho.audiobooks.data.repository.LibrarySortOption.PROGRESS,
            com.sappho.audiobooks.data.repository.LibrarySortOption.RATING
        )
        val needsReverse = if (isNaturallyDescending) sortAscending else !sortAscending
        if (needsReverse) sorted.reversed() else sorted
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            if (isSelectionMode && selectedBookIds.isNotEmpty()) {
                BatchActionBar(
                    selectedCount = selectedBookIds.size,
                    onMarkFinished = {
                        viewModel.batchMarkFinished { _, message ->
                            scope.launch { snackbarHostState.showSnackbar(message) }
                        }
                    },
                    onClearProgress = {
                        viewModel.batchClearProgress { _, message ->
                            scope.launch { snackbarHostState.showSnackbar(message) }
                        }
                    },
                    onAddToReadingList = {
                        viewModel.batchAddToReadingList { _, message ->
                            scope.launch { snackbarHostState.showSnackbar(message) }
                        }
                    },
                    onAddToCollection = { showBatchCollectionDialog = true },
                    onDelete = if (isAdmin) {{ showDeleteConfirmDialog = true }} else null,
                    onCancel = { viewModel.exitSelectionMode() }
                )
            }
        },
        containerColor = Color.Transparent
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp)
        ) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = {
                    if (isSelectionMode) viewModel.exitSelectionMode() else onBackClick()
                }) {
                    Icon(
                        imageVector = if (isSelectionMode) Icons.Default.Close else Icons.Filled.ArrowBack,
                        contentDescription = if (isSelectionMode) "Cancel selection" else "Back",
                        tint = Color.White
                    )
                }
                Text(
                    text = if (isSelectionMode) "${selectedBookIds.size} selected" else "${sortedBooks.size} ${if (sortedBooks.size == 1) "Book" else "Books"}",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    modifier = Modifier.weight(1f)
                )
                if (isSelectionMode) {
                    TextButton(onClick = {
                        if (selectedBookIds.size == sortedBooks.size) {
                            viewModel.deselectAllBooks()
                        } else {
                            viewModel.selectAllBooks(sortedBooks.map { it.id })
                        }
                    }) {
                        Text(
                            text = if (selectedBookIds.size == sortedBooks.size) "Deselect All" else "Select All",
                            color = SapphoInfo
                        )
                    }
                }
            }

            // Filters (hide in selection mode)
            if (!isSelectionMode) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Progress Filter
                    FilterOptionDropdown(
                        currentOption = filterOption,
                        onOptionSelect = { viewModel.userPreferences.setLibraryFilterOption(it) },
                        modifier = Modifier.weight(1f)
                    )

                    // Sort Filter
                    SortOptionDropdown(
                        currentOption = sortOption,
                        onOptionSelect = { viewModel.userPreferences.setLibrarySortOption(it) },
                        isAscending = sortAscending,
                        onAscendingToggle = { viewModel.userPreferences.setLibrarySortAscending(it) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // Books Grid (adaptive for tablets)
            LazyVerticalGrid(
                columns = AdaptiveGrid.libraryGrid,
                horizontalArrangement = Arrangement.spacedBy(AdaptiveSpacing.gridSpacing),
                verticalArrangement = Arrangement.spacedBy(AdaptiveSpacing.gridSpacing),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(sortedBooks.size) { index ->
                    val book = sortedBooks[index]
                    val isSelected = selectedBookIds.contains(book.id)
                    SelectableBookGridItem(
                        book = book,
                        serverUrl = serverUrl,
                        showSeriesPosition = sortOption == com.sappho.audiobooks.data.repository.LibrarySortOption.SERIES_POSITION,
                        isSelectionMode = isSelectionMode,
                        isSelected = isSelected,
                        onClick = {
                            if (isSelectionMode) {
                                viewModel.toggleBookSelection(book.id)
                            } else {
                                onAudiobookClick(book.id)
                            }
                        },
                        onLongClick = {
                            if (!isSelectionMode) {
                                viewModel.toggleSelectionMode()
                                viewModel.toggleBookSelection(book.id)
                            }
                        }
                    )
                }
            }
        }
    }

    // Batch add to collection dialog
    if (showBatchCollectionDialog) {
        SelectCollectionDialog(
            collections = collections,
            onDismiss = { showBatchCollectionDialog = false },
            onSelect = { collectionId ->
                viewModel.batchAddToCollection(collectionId) { _, message ->
                    scope.launch { snackbarHostState.showSnackbar(message) }
                }
                showBatchCollectionDialog = false
            }
        )
    }

    if (showDeleteConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmDialog = false },
            title = { Text("Delete ${selectedBookIds.size} book${if (selectedBookIds.size != 1) "s" else ""}?", color = Color.White) },
            text = { Text("This will permanently delete the selected books and their files. This cannot be undone.", color = SapphoIconDefault) },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteConfirmDialog = false
                    viewModel.batchDelete { _, message ->
                        scope.launch { snackbarHostState.showSnackbar(message) }
                    }
                }) {
                    Text("Delete", color = SapphoError)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmDialog = false }) {
                    Text("Cancel", color = SapphoIconDefault)
                }
            },
            containerColor = SapphoSurfaceDark
        )
    }
}

@Composable
fun FilterOptionDropdown(
    currentOption: com.sappho.audiobooks.data.repository.LibraryFilterOption,
    onOptionSelect: (com.sappho.audiobooks.data.repository.LibraryFilterOption) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }

    Column(modifier = modifier) {
        Text(
            text = "Show",
            fontSize = 12.sp,
            color = SapphoIconDefault,
            modifier = Modifier.padding(bottom = 4.dp)
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(SapphoSurfaceDark)
                .clickable { expanded = true }
                .padding(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = currentOption.displayName,
                    fontSize = 14.sp,
                    color = Color.White
                )
                Icon(
                    imageVector = Icons.Default.KeyboardArrowDown,
                    contentDescription = null,
                    tint = SapphoIconDefault,
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.background(SapphoSurfaceDark)
        ) {
            com.sappho.audiobooks.data.repository.LibraryFilterOption.entries.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option.displayName, color = Color.White) },
                    onClick = {
                        onOptionSelect(option)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
fun SortOptionDropdown(
    currentOption: com.sappho.audiobooks.data.repository.LibrarySortOption,
    onOptionSelect: (com.sappho.audiobooks.data.repository.LibrarySortOption) -> Unit,
    isAscending: Boolean = true,
    onAscendingToggle: (Boolean) -> Unit = {},
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }

    Column(modifier = modifier) {
        Text(
            text = "Sort",
            fontSize = 12.sp,
            color = SapphoIconDefault,
            modifier = Modifier.padding(bottom = 4.dp)
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Sort option dropdown
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(8.dp))
                    .background(SapphoSurfaceDark)
                    .clickable { expanded = true }
                    .padding(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = currentOption.displayName,
                        fontSize = 14.sp,
                        color = Color.White
                    )
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowDown,
                        contentDescription = null,
                        tint = SapphoIconDefault,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            // Ascending/Descending toggle button
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(SapphoSurfaceDark)
                    .clickable { onAscendingToggle(!isAscending) },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isAscending) Icons.Default.ArrowUpward else Icons.Default.ArrowDownward,
                    contentDescription = if (isAscending) "Ascending" else "Descending",
                    tint = SapphoIconDefault,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.background(SapphoSurfaceDark)
        ) {
            com.sappho.audiobooks.data.repository.LibrarySortOption.entries.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option.displayName, color = Color.White) },
                    onClick = {
                        onOptionSelect(option)
                        expanded = false
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SelectableBookGridItem(
    book: com.sappho.audiobooks.domain.model.Audiobook,
    serverUrl: String?,
    showSeriesPosition: Boolean = false,
    isSelectionMode: Boolean = false,
    isSelected: Boolean = false,
    onClick: () -> Unit,
    onLongClick: () -> Unit = {}
) {
    Column(
        modifier = Modifier
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
    ) {
        Box(
            modifier = Modifier
                .aspectRatio(1f)
                .clip(RoundedCornerShape(8.dp))
        ) {
            // Cover Image
            if (book.coverImage != null && serverUrl != null) {
                AsyncImage(
                    model = com.sappho.audiobooks.util.buildCoverUrl(serverUrl, book.id, com.sappho.audiobooks.util.COVER_WIDTH_THUMBNAIL),
                    contentDescription = book.title,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit
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
                        style = MaterialTheme.typography.headlineLarge,
                        color = Color.White
                    )
                }
            }

            // Selection overlay
            if (isSelectionMode) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(if (isSelected) Color.Black.copy(alpha = 0.4f) else Color.Black.copy(alpha = 0.2f))
                )
                // Checkbox
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(if (isSelected) SapphoInfo else SapphoProgressTrack)
                        .border(2.dp, Color.White, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    if (isSelected) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = "Selected",
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            } else {
                // Series position badge (only show when not in selection mode)
                if (showSeriesPosition && book.seriesPosition != null) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(6.dp)
                            .background(SapphoInfo, RoundedCornerShape(4.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "#${formatSeriesPosition(book.seriesPosition)}",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }

                // Reading list ribbon (top-right corner)
                if (book.isFavorite) {
                    ReadingListRibbon(
                        modifier = Modifier.align(Alignment.TopEnd),
                        size = 32f
                    )
                }
            }

            // Rating badge (bottom-right, on cover)
            val displayRating = book.userRating ?: book.averageRating
            if (displayRating != null && displayRating > 0) {
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(4.dp)
                        .background(Color.Black.copy(alpha = 0.75f), RoundedCornerShape(4.dp))
                        .padding(horizontal = 5.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Star,
                        contentDescription = null,
                        tint = SapphoStarFilled,
                        modifier = Modifier.size(10.dp)
                    )
                    Spacer(modifier = Modifier.width(2.dp))
                    Text(
                        text = String.format(java.util.Locale.US, "%.1f", displayRating),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White
                    )
                }
            }

            // Progress bar
            val progress = book.progress
            if (progress != null && (progress.position > 0 || progress.completed == 1) && book.duration != null) {
                val progressPercent = if (progress.completed == 1) 1f
                    else (progress.position.toFloat() / book.duration.toFloat()).coerceIn(0f, 1f)

                Box(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth()
                        .height(5.dp)
                        .background(Color.Black.copy(alpha = 0.7f))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(progressPercent)
                            .background(
                                if (progress.completed == 1)
                                    Brush.horizontalGradient(listOf(SapphoSuccess, LegacyGreenLight))
                                else
                                    Brush.horizontalGradient(listOf(SapphoInfo, LegacyBlueLight))
                            )
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Title
        Text(
            text = book.title,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            color = SapphoText,
            maxLines = 1,
            lineHeight = 14.sp,
            modifier = Modifier.basicMarquee(
                iterations = Int.MAX_VALUE,
                initialDelayMillis = 2000,
                velocity = 12.dp
            )
        )

        // Author
        book.author?.let {
            Text(
                text = it,
                fontSize = 10.sp,
                color = SapphoIconDefault,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

    }
}

@Composable
fun BatchActionBar(
    selectedCount: Int,
    onMarkFinished: () -> Unit,
    onClearProgress: () -> Unit,
    onAddToReadingList: () -> Unit,
    onAddToCollection: () -> Unit,
    onDelete: (() -> Unit)? = null,
    onCancel: () -> Unit
) {
    Surface(
        color = SapphoSurfaceDark,
        shadowElevation = 8.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Mark Finished
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .clickable(onClick = onMarkFinished)
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Icon(Icons.Default.CheckCircle, contentDescription = "Mark Finished", tint = SapphoSuccess)
                Text("Finished", fontSize = 10.sp, color = SapphoIconDefault)
            }
            // Clear Progress
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .clickable(onClick = onClearProgress)
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Icon(Icons.Default.Refresh, contentDescription = "Clear Progress", tint = SapphoWarning)
                Text("Clear", fontSize = 10.sp, color = SapphoIconDefault)
            }
            // Add to Reading List
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .clickable(onClick = onAddToReadingList)
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Icon(Icons.Default.BookmarkAdd, contentDescription = "Add to Reading List", tint = SapphoInfo)
                Text("Reading List", fontSize = 10.sp, color = SapphoIconDefault)
            }
            // Add to Collection
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .clickable(onClick = onAddToCollection)
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Icon(Icons.Default.FolderSpecial, contentDescription = "Add to Collection", tint = SapphoInfo)
                Text("Collection", fontSize = 10.sp, color = SapphoIconDefault)
            }
            // Delete (admin only)
            if (onDelete != null) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .clickable(onClick = onDelete)
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete", tint = SapphoError)
                    Text("Delete", fontSize = 10.sp, color = SapphoIconDefault)
                }
            }
        }
    }
}

@Composable
fun SelectCollectionDialog(
    collections: List<com.sappho.audiobooks.data.remote.Collection>,
    onDismiss: () -> Unit,
    onSelect: (Int) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add to Collection", color = Color.White) },
        text = {
            if (collections.isEmpty()) {
                Text("No collections yet. Create one first.", color = SapphoIconDefault)
            } else {
                LazyColumn {
                    items(collections) { collection ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSelect(collection.id) }
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.FolderSpecial,
                                contentDescription = null,
                                tint = SapphoWarning,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(collection.name, color = Color.White)
                                Text(
                                    "${collection.bookCount ?: 0} books",
                                    fontSize = 12.sp,
                                    color = SapphoTextMuted
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = SapphoIconDefault)
            }
        },
        containerColor = SapphoSurfaceDark
    )
}

@Composable
fun FilterDropdown(
    label: String,
    value: String,
    options: List<Pair<String, String>>,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }

    Column(modifier = modifier) {
        Text(
            text = label,
            fontSize = 12.sp,
            color = SapphoIconDefault,
            modifier = Modifier.padding(bottom = 4.dp)
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(SapphoSurfaceDark)
                .clickable { expanded = true }
                .padding(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = value,
                    fontSize = 14.sp,
                    color = Color.White
                )
                Icon(
                    imageVector = Icons.Default.KeyboardArrowDown,
                    contentDescription = null,
                    tint = SapphoIconDefault,
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.background(SapphoSurfaceDark)
        ) {
            options.forEach { (key, label) ->
                DropdownMenuItem(
                    text = { Text(label, color = Color.White) },
                    onClick = {
                        onSelect(key)
                        expanded = false
                    }
                )
            }
        }
    }
}

// Helper function to format series position - shows whole numbers unless it has a decimal
private fun formatSeriesPosition(position: Float?): String? {
    if (position == null) return null
    // Check if it's a whole number
    return if (position == position.toLong().toFloat()) {
        position.toLong().toString()
    } else {
        position.toString()
    }
}
