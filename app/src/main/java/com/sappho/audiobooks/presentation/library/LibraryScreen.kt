package com.sappho.audiobooks.presentation.library

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import kotlinx.coroutines.launch

/**
 * Parse a hex color string (e.g., "#06b6d4") to a Compose Color
 */
private fun parseHexColor(hex: String): Color {
    return try {
        val colorString = hex.removePrefix("#")
        Color(android.graphics.Color.parseColor("#$colorString"))
    } catch (e: Exception) {
        Color(0xFF10b981) // Default green if parsing fails
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
        val ribbonColor = Color(0xFF3b82f6)
        val shadowColor = Color(0xFF1d4ed8)

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
    initialAuthor: String? = null,
    initialSeries: String? = null,
    viewModel: LibraryViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val series by viewModel.series.collectAsState()
    val authors by viewModel.authors.collectAsState()
    val genres by viewModel.genres.collectAsState()
    val allBooks by viewModel.allAudiobooks.collectAsState()

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
            .background(Color(0xFF0A0E1A))
    ) {
        when (currentView) {
            LibraryView.CATEGORIES -> {
                CategoriesView(
                    totalBooks = allBooks.size,
                    seriesCount = series.size,
                    authorsCount = authors.size,
                    genresCount = genres.size,
                    onSeriesClick = { currentViewName = LibraryView.SERIES.name },
                    onAuthorsClick = { currentViewName = LibraryView.AUTHORS.name },
                    onGenresClick = { currentViewName = LibraryView.GENRES.name },
                    onAllBooksClick = { currentViewName = LibraryView.ALL_BOOKS.name }
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
                    onAudiobookClick = onAudiobookClick
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
    onSeriesClick: () -> Unit,
    onAuthorsClick: () -> Unit,
    onGenresClick: () -> Unit,
    onAllBooksClick: () -> Unit
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
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "$totalBooks audiobooks in your collection",
                    fontSize = 14.sp,
                    color = Color(0xFF9ca3af)
                )
            }
        }

        // Series Card - Full width featured card
        item {
            CategoryCardLarge(
                icon = Icons.Filled.MenuBook,
                title = "Series",
                count = seriesCount,
                label = "series",
                gradientColors = listOf(Color(0xFF3b82f6), Color(0xFF1d4ed8)),
                onClick = onSeriesClick
            )
        }

        // Two column cards
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
                    gradientColors = listOf(Color(0xFF8b5cf6), Color(0xFF6d28d9)),
                    onClick = onAuthorsClick,
                    modifier = Modifier.weight(1f)
                )
                CategoryCardMedium(
                    icon = Icons.Default.Category,
                    title = "Genres",
                    count = genresCount,
                    label = "genres",
                    gradientColors = listOf(Color(0xFF10b981), Color(0xFF059669)),
                    onClick = onGenresClick,
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
                gradientColors = listOf(Color(0xFF374151), Color(0xFF1f2937)),
                onClick = onAllBooksClick
            )
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
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = "$count",
                        fontSize = 40.sp,
                        fontWeight = FontWeight.Bold,
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
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold,
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
            .background(Color(0xFF0A0E1A))
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
                    text = "Series",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Text(
                    text = "${series.size} series in your library",
                    fontSize = 13.sp,
                    color = Color(0xFF9ca3af)
                )
            }
        }

        LazyColumn(
            modifier = Modifier.padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(series) { seriesItem ->
                val seriesBooks = allBooks.filter { it.series == seriesItem.series }
                    .sortedBy { it.seriesPosition ?: 0f }
                val totalDuration = seriesBooks.sumOf { it.duration ?: 0 }
                val authors = seriesBooks.mapNotNull { it.author }.distinct()
                val completedCount = seriesBooks.count { it.progress?.completed == 1 }

                SeriesListCard(
                    seriesName = seriesItem.series,
                    bookCount = seriesItem.bookCount,
                    totalDuration = totalDuration,
                    authors = authors,
                    completedCount = completedCount,
                    books = seriesBooks.take(4),
                    serverUrl = serverUrl,
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
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFF1e293b))
            .clickable(onClick = onClick)
            .padding(16.dp),
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
                        .background(Color(0xFF374151))
                        .border(1.dp, Color(0xFF4b5563), RoundedCornerShape(6.dp))
                ) {
                    if (book.coverImage != null && serverUrl != null) {
                        AsyncImage(
                            model = "$serverUrl/api/audiobooks/${book.id}/cover",
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
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
                color = Color.White,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(4.dp))

            if (authors.isNotEmpty()) {
                Text(
                    text = authors.first() + if (authors.size > 1) " +${authors.size - 1}" else "",
                    fontSize = 13.sp,
                    color = Color(0xFF9ca3af),
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
                        tint = Color(0xFF3b82f6),
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "$bookCount",
                        fontSize = 12.sp,
                        color = Color(0xFF9ca3af)
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Schedule,
                        contentDescription = null,
                        tint = Color(0xFF3b82f6),
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "${totalDuration / 3600}h",
                        fontSize = 12.sp,
                        color = Color(0xFF9ca3af)
                    )
                }
                if (completedCount > 0) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = Color(0xFF10b981),
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "$completedCount/$bookCount",
                            fontSize = 12.sp,
                            color = Color(0xFF10b981)
                        )
                    }
                }
            }
        }

        Icon(
            imageVector = Icons.Default.ChevronRight,
            contentDescription = null,
            tint = Color(0xFF6b7280),
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
    val avatarColors = listOf(
        listOf(Color(0xFF8b5cf6), Color(0xFF6d28d9)),
        listOf(Color(0xFF3b82f6), Color(0xFF1d4ed8)),
        listOf(Color(0xFF10b981), Color(0xFF059669)),
        listOf(Color(0xFFf59e0b), Color(0xFFd97706)),
        listOf(Color(0xFFef4444), Color(0xFFdc2626)),
        listOf(Color(0xFF06b6d4), Color(0xFF0891b2)),
        listOf(Color(0xFFec4899), Color(0xFFdb2777))
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0A0E1A))
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
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Text(
                    text = "${authors.size} authors in your library",
                    fontSize = 13.sp,
                    color = Color(0xFF9ca3af)
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
                val colorIndex = author.author.hashCode().let { kotlin.math.abs(it) % avatarColors.size }

                AuthorListCard(
                    authorName = author.author,
                    initials = initials,
                    bookCount = author.bookCount,
                    seriesCount = seriesCount,
                    totalDuration = totalDuration,
                    genres = genres,
                    gradientColors = avatarColors[colorIndex],
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
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFF1e293b))
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Avatar
        Box(
            modifier = Modifier
                .size(56.dp)
                .background(
                    Brush.linearGradient(gradientColors),
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
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(2.dp))

            if (genres.isNotEmpty()) {
                Text(
                    text = genres.take(2).joinToString(" • "),
                    fontSize = 12.sp,
                    color = Color(0xFF9ca3af),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            @OptIn(ExperimentalLayoutApi::class)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.MenuBook,
                        contentDescription = null,
                        tint = gradientColors[0],
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "$bookCount",
                        fontSize = 12.sp,
                        color = Color(0xFF9ca3af),
                        maxLines = 1
                    )
                }
                if (seriesCount > 0) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Layers,
                            contentDescription = null,
                            tint = gradientColors[0],
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "$seriesCount",
                            fontSize = 12.sp,
                            color = Color(0xFF9ca3af),
                            maxLines = 1
                        )
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Schedule,
                        contentDescription = null,
                        tint = gradientColors[0],
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "${totalDuration / 3600}h",
                        fontSize = 12.sp,
                        color = Color(0xFF9ca3af),
                        maxLines = 1
                    )
                }
            }
        }

        // Mini book covers
        if (recentBooks.isNotEmpty()) {
            Row(
                modifier = Modifier.padding(start = 8.dp),
                horizontalArrangement = Arrangement.spacedBy((-12).dp)
            ) {
                recentBooks.take(3).forEach { book ->
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color(0xFF374151))
                            .border(1.dp, Color(0xFF1e293b), RoundedCornerShape(4.dp))
                    ) {
                        if (book.coverImage != null && serverUrl != null) {
                            AsyncImage(
                                model = "$serverUrl/api/audiobooks/${book.id}/cover",
                                contentDescription = null,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.width(8.dp))

        Icon(
            imageVector = Icons.Default.ChevronRight,
            contentDescription = null,
            tint = Color(0xFF6b7280),
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
            .background(Color(0xFF0A0E1A))
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
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Text(
                    text = "${genres.size} genres in your library",
                    fontSize = 13.sp,
                    color = Color(0xFF9ca3af)
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
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(
                Brush.horizontalGradient(
                    colors = gradientColors
                )
            )
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
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
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
                                model = "$serverUrl/api/audiobooks/${book.id}/cover",
                                contentDescription = null,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
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
            .background(Color(0xFF1e293b))
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
                    .background(Color(0xFF374151), RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = Color(0xFF9ca3af),
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
                        color = Color(0xFF9ca3af)
                    )
                }
            }
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = Color(0xFF6b7280),
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
            .background(Color(0xFF0A0E1A))
    ) {
        // Hero Section with stacked covers
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(280.dp)
            ) {
                // Gradient background
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color(0xFF1e3a5f),
                                    Color(0xFF0A0E1A)
                                )
                            )
                        )
                )

                // Stacked book covers
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 60.dp),
                    contentAlignment = Alignment.Center
                ) {
                    val displayBooks = books.take(5).reversed()
                    displayBooks.forEachIndexed { index, book ->
                        val offset = (displayBooks.size - 1 - index) * 20
                        val scale = 1f - (displayBooks.size - 1 - index) * 0.05f
                        Box(
                            modifier = Modifier
                                .offset(x = offset.dp)
                                .size(width = (110 * scale).dp, height = (160 * scale).dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFF374151))
                        ) {
                            if (book.coverImage != null && serverUrl != null) {
                                AsyncImage(
                                    model = "$serverUrl/api/audiobooks/${book.id}/cover",
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
                                                colors = listOf(Color(0xFF3b82f6), Color(0xFF1d4ed8))
                                            )
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "#${formatSeriesPosition(book.seriesPosition) ?: (index + 1).toString()}",
                                        fontSize = 24.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                }
                            }
                        }
                    }
                }

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
            }
        }

        // Series Info Section
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
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(8.dp))

                if (authors.isNotEmpty()) {
                    Text(
                        text = "by ${authors.joinToString(", ")}",
                        fontSize = 14.sp,
                        color = Color(0xFF9ca3af),
                        textAlign = TextAlign.Center
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

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

                // Overall progress bar
                if (overallProgress > 0 || completedBooks > 0) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFF1e293b))
                            .padding(16.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Series Progress",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                color = Color.White
                            )
                            Text(
                                text = "${(overallProgress * 100).toInt()}%",
                                fontSize = 13.sp,
                                color = Color(0xFF3b82f6)
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp))
                                .background(Color(0xFF374151))
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .fillMaxWidth(overallProgress)
                                    .background(
                                        Brush.horizontalGradient(
                                            listOf(Color(0xFF3b82f6), Color(0xFF60a5fa))
                                        )
                                    )
                            )
                        }
                    }
                }
            }
        }

        // Catch Me Up Section
        if (aiConfigured && hasProgress && viewModel != null) {
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .padding(top = 20.dp)
                ) {
                    if (!showRecap && !recapLoading && recapError == null && recapData == null) {
                        // Show Catch Me Up Button
                        Button(
                            onClick = {
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
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF6366f1)
                            ),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.AutoStories,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Catch Me Up",
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    // Loading State
                    if (recapLoading) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0xFF6366f1).copy(alpha = 0.1f))
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = Color(0xFFa5b4fc),
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = "Generating your personalized recap...",
                                color = Color(0xFFa5b4fc),
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
                                .background(Color(0xFFef4444).copy(alpha = 0.1f))
                                .padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = null,
                                tint = Color(0xFFf87171),
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = recapError!!,
                                color = Color(0xFFfca5a5),
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
                                    contentColor = Color(0xFFfca5a5)
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
                                .background(Color(0xFF1e293b).copy(alpha = 0.8f))
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
                                    tint = Color(0xFFa5b4fc),
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    text = "Series Recap",
                                    color = Color.White,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.weight(1f)
                                )
                                if (recapData!!.cached) {
                                    Text(
                                        text = "Cached",
                                        fontSize = 10.sp,
                                        color = Color(0xFFa5b4fc),
                                        modifier = Modifier
                                            .background(
                                                Color(0xFF6366f1).copy(alpha = 0.2f),
                                                RoundedCornerShape(4.dp)
                                            )
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                }
                                Icon(
                                    imageVector = if (recapExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                    contentDescription = null,
                                    tint = Color(0xFF9ca3af),
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
                                        color = Color(0xFF9ca3af),
                                        lineHeight = 16.sp
                                    )

                                    Spacer(modifier = Modifier.height(12.dp))

                                    // Recap text
                                    Text(
                                        text = recapData!!.recap,
                                        fontSize = 14.sp,
                                        color = Color(0xFFd1d5db),
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
                                                contentColor = Color(0xFF9ca3af)
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

        // Books Section Header
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
                    text = "Books in Series",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White
                )
            }
        }

        // Book list items
        items(books) { book ->
            SeriesBookListItem(
                book = book,
                serverUrl = serverUrl,
                onClick = { onBookClick(book.id) }
            )
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
            .background(Color(0xFF1e293b))
            .padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = Color(0xFF3b82f6),
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = value,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )
        Text(
            text = label,
            fontSize = 11.sp,
            color = Color(0xFF9ca3af)
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

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF1e293b))
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Book number badge - subtle style
        Box(
            modifier = Modifier
                .size(28.dp)
                .background(
                    if (isCompleted) Color(0xFF10b981).copy(alpha = 0.2f) else Color.White.copy(alpha = 0.08f),
                    RoundedCornerShape(6.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            if (isCompleted) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = "Completed",
                    tint = Color(0xFF10b981),
                    modifier = Modifier.size(16.dp)
                )
            } else {
                Text(
                    text = formatSeriesPosition(book.seriesPosition) ?: "?",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color(0xFF9ca3af)
                )
            }
        }

        Spacer(modifier = Modifier.width(10.dp))

        // Cover thumbnail
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(Color(0xFF374151))
        ) {
            if (book.coverImage != null && serverUrl != null) {
                AsyncImage(
                    model = "$serverUrl/api/audiobooks/${book.id}/cover",
                    contentDescription = book.title,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
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
                color = Color.White,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (book.duration != null) {
                    Text(
                        text = "${book.duration / 3600}h ${(book.duration % 3600) / 60}m",
                        fontSize = 12.sp,
                        color = Color(0xFF9ca3af)
                    )
                }
                if (progressPercent > 0 && !isCompleted) {
                    Text(
                        text = " • ${(progressPercent * 100).toInt()}%",
                        fontSize = 12.sp,
                        color = Color(0xFF3b82f6)
                    )
                }
                if (book.isFavorite) {
                    Text(text = " • ", fontSize = 12.sp, color = Color(0xFF9ca3af))
                    Icon(
                        imageVector = Icons.Filled.BookmarkAdded,
                        contentDescription = "On reading list",
                        tint = Color(0xFF3b82f6),
                        modifier = Modifier.size(14.dp)
                    )
                }
            }

            // Progress bar
            if (progressPercent > 0) {
                Spacer(modifier = Modifier.height(6.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(Color(0xFF374151))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(progressPercent)
                            .background(
                                if (isCompleted) Color(0xFF10b981) else Color(0xFF3b82f6)
                            )
                    )
                }
            }
        }

        Spacer(modifier = Modifier.width(8.dp))

        Icon(
            imageVector = Icons.Default.ChevronRight,
            contentDescription = null,
            tint = Color(0xFF6b7280),
            modifier = Modifier.size(20.dp)
        )
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
    val avatarColors = listOf(
        listOf(Color(0xFF8b5cf6), Color(0xFF6d28d9)),
        listOf(Color(0xFF3b82f6), Color(0xFF1d4ed8)),
        listOf(Color(0xFF10b981), Color(0xFF059669)),
        listOf(Color(0xFFf59e0b), Color(0xFFd97706)),
        listOf(Color(0xFFef4444), Color(0xFFdc2626))
    )
    val colorIndex = authorName.hashCode().let { kotlin.math.abs(it) % avatarColors.size }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0A0E1A))
    ) {
        // Hero Section with avatar
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(240.dp)
            ) {
                // Gradient background
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    avatarColors[colorIndex][0].copy(alpha = 0.4f),
                                    Color(0xFF0A0E1A)
                                )
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

                // Avatar and name
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 60.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Large avatar
                    Box(
                        modifier = Modifier
                            .size(100.dp)
                            .background(
                                Brush.linearGradient(avatarColors[colorIndex]),
                                CircleShape
                            )
                            .border(3.dp, Color.White.copy(alpha = 0.2f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = initials,
                            fontSize = 36.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = authorName,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        textAlign = TextAlign.Center
                    )

                    if (genres.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = genres.take(3).joinToString(" • "),
                            fontSize = 13.sp,
                            color = Color(0xFF9ca3af),
                            textAlign = TextAlign.Center
                        )
                    }
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

        // Series sections
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
                                color = Color.White
                            )
                            Text(
                                text = "${seriesBooks.size} ${if (seriesBooks.size == 1) "book" else "books"} in series",
                                fontSize = 12.sp,
                                color = Color(0xFF9ca3af)
                            )
                        }
                        Box(
                            modifier = Modifier
                                .background(Color(0xFF3b82f6).copy(alpha = 0.2f), RoundedCornerShape(6.dp))
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = "Series",
                                fontSize = 11.sp,
                                color = Color(0xFF3b82f6)
                            )
                        }
                    }
                }
            }

            item {
                LazyRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp)
                ) {
                    items(seriesBooks) { book ->
                        AuthorBookCard(
                            book = book,
                            serverUrl = serverUrl,
                            showSeriesPosition = true,
                            onClick = { onBookClick(book.id) }
                        )
                    }
                }
            }
        }

        // Standalone books section
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
                        color = Color.White
                    )
                    Text(
                        text = "${standaloneBooks.size} ${if (standaloneBooks.size == 1) "book" else "books"}",
                        fontSize = 12.sp,
                        color = Color(0xFF9ca3af)
                    )
                }
            }

            item {
                LazyRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp)
                ) {
                    items(standaloneBooks.sortedBy { it.title }) { book ->
                        AuthorBookCard(
                            book = book,
                            serverUrl = serverUrl,
                            showSeriesPosition = false,
                            onClick = { onBookClick(book.id) }
                        )
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
            .background(Color(0xFF1e293b))
            .padding(vertical = 12.dp, horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = value,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = label,
            fontSize = 10.sp,
            color = Color(0xFF9ca3af),
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
                .background(Color(0xFF374151))
        ) {
            if (book.coverImage != null && serverUrl != null) {
                AsyncImage(
                    model = "$serverUrl/api/audiobooks/${book.id}/cover",
                    contentDescription = book.title,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = book.title.take(2).uppercase(),
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
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
                        .background(Color(0xFF3b82f6), RoundedCornerShape(4.dp))
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
                                if (isCompleted) Color(0xFF10b981) else Color(0xFF3b82f6)
                            )
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = book.title,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            color = Color.White,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            lineHeight = 16.sp
        )

        if (book.duration != null) {
            Text(
                text = "${book.duration / 3600}h ${(book.duration % 3600) / 60}m",
                fontSize = 11.sp,
                color = Color(0xFF9ca3af)
            )
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
            .background(Color(0xFF0A0E1A))
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
                                    Color(0xFF0A0E1A)
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
                                    .background(Color(0xFF374151), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "+${authors.size - 4}",
                                    fontSize = 12.sp,
                                    color = Color(0xFF9ca3af)
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
                    color = Color(0xFF10b981)
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
            .background(Color(0xFF1e293b))
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
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )
        Text(
            text = label,
            fontSize = 10.sp,
            color = Color(0xFF9ca3af)
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
                .background(Color(0xFF374151))
        ) {
            if (book.coverImage != null && serverUrl != null) {
                AsyncImage(
                    model = "$serverUrl/api/audiobooks/${book.id}/cover",
                    contentDescription = book.title,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
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
                        .background(Color(0xFF10b981), CircleShape),
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
                            .background(Color(0xFF3b82f6))
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = book.title,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            color = Color.White,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            lineHeight = 14.sp
        )

        book.author?.let {
            Text(
                text = it,
                fontSize = 10.sp,
                color = Color(0xFF9ca3af),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
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
    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
    ) {
        // Cover Image
        if (book.coverImage != null && serverUrl != null) {
            AsyncImage(
                model = "$serverUrl/api/audiobooks/${book.id}/cover",
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
                            colors = listOf(Color(0xFF374151), Color(0xFF1f2937))
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = book.title.take(2).uppercase(),
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
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
                    .background(Color(0xFF3b82f6), RoundedCornerShape(4.dp))
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
                                Brush.horizontalGradient(listOf(Color(0xFF10b981), Color(0xFF34d399)))
                            else
                                Brush.horizontalGradient(listOf(Color(0xFF3b82f6), Color(0xFF60a5fa)))
                        )
                )
            }
        }
    }
}

@Composable
fun AllBooksView(
    viewModel: LibraryViewModel,
    onBackClick: () -> Unit,
    onAudiobookClick: (Int) -> Unit = {}
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
        }
        val sorted = filteredBooks.sortedWith(comparator)
        if (sortAscending || sortOption == com.sappho.audiobooks.data.repository.LibrarySortOption.RECENTLY_ADDED ||
            sortOption == com.sappho.audiobooks.data.repository.LibrarySortOption.RECENTLY_LISTENED ||
            sortOption == com.sappho.audiobooks.data.repository.LibrarySortOption.PROGRESS) {
            sorted
        } else {
            sorted.reversed()
        }
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
                            color = Color(0xFF3B82F6)
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
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // Books Grid
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
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
            color = Color(0xFF9ca3af),
            modifier = Modifier.padding(bottom = 4.dp)
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFF1f2937))
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
                    tint = Color(0xFF9ca3af),
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.background(Color(0xFF1f2937))
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
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }

    Column(modifier = modifier) {
        Text(
            text = "Sort",
            fontSize = 12.sp,
            color = Color(0xFF9ca3af),
            modifier = Modifier.padding(bottom = 4.dp)
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFF1f2937))
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
                    tint = Color(0xFF9ca3af),
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.background(Color(0xFF1f2937))
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
    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(8.dp))
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
    ) {
        // Cover Image
        if (book.coverImage != null && serverUrl != null) {
            AsyncImage(
                model = "$serverUrl/api/audiobooks/${book.id}/cover",
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
                            colors = listOf(Color(0xFF374151), Color(0xFF1f2937))
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = book.title.take(2).uppercase(),
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
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
                    .background(if (isSelected) Color(0xFF3B82F6) else Color(0xFF374151))
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
                        .background(Color(0xFF3b82f6), RoundedCornerShape(4.dp))
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
                                Brush.horizontalGradient(listOf(Color(0xFF10b981), Color(0xFF34d399)))
                            else
                                Brush.horizontalGradient(listOf(Color(0xFF3b82f6), Color(0xFF60a5fa)))
                        )
                )
            }
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
    onCancel: () -> Unit
) {
    Surface(
        color = Color(0xFF1f2937),
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
                Icon(Icons.Default.CheckCircle, contentDescription = "Mark Finished", tint = Color(0xFF10b981))
                Text("Finished", fontSize = 10.sp, color = Color(0xFF9ca3af))
            }
            // Clear Progress
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .clickable(onClick = onClearProgress)
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Icon(Icons.Default.Refresh, contentDescription = "Clear Progress", tint = Color(0xFFf59e0b))
                Text("Clear", fontSize = 10.sp, color = Color(0xFF9ca3af))
            }
            // Add to Reading List
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .clickable(onClick = onAddToReadingList)
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Icon(Icons.Default.BookmarkAdd, contentDescription = "Add to Reading List", tint = Color(0xFF3b82f6))
                Text("Reading List", fontSize = 10.sp, color = Color(0xFF9ca3af))
            }
            // Add to Collection
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .clickable(onClick = onAddToCollection)
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Icon(Icons.Default.FolderSpecial, contentDescription = "Add to Collection", tint = Color(0xFF3B82F6))
                Text("Collection", fontSize = 10.sp, color = Color(0xFF9ca3af))
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
                Text("No collections yet. Create one first.", color = Color(0xFF9ca3af))
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
                                tint = Color(0xFFf59e0b),
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(collection.name, color = Color.White)
                                Text(
                                    "${collection.bookCount ?: 0} books",
                                    fontSize = 12.sp,
                                    color = Color(0xFF6b7280)
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
                Text("Cancel", color = Color(0xFF9ca3af))
            }
        },
        containerColor = Color(0xFF1f2937)
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
            color = Color(0xFF9ca3af),
            modifier = Modifier.padding(bottom = 4.dp)
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFF1f2937))
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
                    tint = Color(0xFF9ca3af),
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.background(Color(0xFF1f2937))
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
