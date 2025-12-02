package com.sappho.audiobooks.presentation.library

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage

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

    // Determine initial view based on parameters
    val initialView = when {
        initialSeries != null -> LibraryView.SERIES_BOOKS
        initialAuthor != null -> LibraryView.AUTHOR_BOOKS
        else -> LibraryView.CATEGORIES
    }

    var currentView by remember { mutableStateOf(initialView) }
    var selectedSeries by remember { mutableStateOf(initialSeries) }
    var selectedAuthor by remember { mutableStateOf(initialAuthor) }
    var selectedGenre by remember { mutableStateOf<String?>(null) }

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
                    onSeriesClick = { currentView = LibraryView.SERIES },
                    onAuthorsClick = { currentView = LibraryView.AUTHORS },
                    onGenresClick = { currentView = LibraryView.GENRES },
                    onAllBooksClick = { currentView = LibraryView.ALL_BOOKS }
                )
            }
            LibraryView.SERIES -> {
                SeriesListView(
                    series = series,
                    allBooks = allBooks,
                    serverUrl = viewModel.serverUrl.collectAsState().value,
                    onBackClick = { currentView = LibraryView.CATEGORIES },
                    onSeriesClick = { seriesName ->
                        selectedSeries = seriesName
                        currentView = LibraryView.SERIES_BOOKS
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
                        onBackClick = { currentView = LibraryView.SERIES },
                        onBookClick = onAudiobookClick
                    )
                }
            }
            LibraryView.AUTHORS -> {
                AuthorsListView(
                    authors = authors,
                    allBooks = allBooks,
                    serverUrl = viewModel.serverUrl.collectAsState().value,
                    onBackClick = { currentView = LibraryView.CATEGORIES },
                    onAuthorClick = { author ->
                        selectedAuthor = author
                        currentView = LibraryView.AUTHOR_BOOKS
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
                        onBackClick = { currentView = LibraryView.AUTHORS },
                        onBookClick = onAudiobookClick
                    )
                }
            }
            LibraryView.GENRES -> {
                GenresListView(
                    genres = genres.map { it.genre },
                    allBooks = allBooks,
                    serverUrl = viewModel.serverUrl.collectAsState().value,
                    onBackClick = { currentView = LibraryView.CATEGORIES },
                    onGenreClick = { genre ->
                        selectedGenre = genre
                        currentView = LibraryView.GENRE_BOOKS
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
                        onBackClick = { currentView = LibraryView.GENRES },
                        onBookClick = onAudiobookClick
                    )
                }
            }
            LibraryView.ALL_BOOKS -> {
                AllBooksView(
                    viewModel = viewModel,
                    onBackClick = { currentView = LibraryView.CATEGORIES },
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
    onBackClick: () -> Unit,
    onBookClick: (Int) -> Unit
) {
    val totalProgress = books.sumOf { it.progress?.position ?: 0 }
    val completedBooks = books.count { it.progress?.completed == 1 }
    val overallProgress = if (totalDuration > 0) {
        (totalProgress.toFloat() / totalDuration.toFloat()).coerceIn(0f, 1f)
    } else 0f

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

    var sortBy by remember { mutableStateOf("title") }
    var progressFilter by remember { mutableStateOf("all") }

    // Filter audiobooks
    val filteredBooks = remember(allBooks, progressFilter) {
        allBooks.filter { book ->
            val isFinished = book.progress?.completed == 1
            val hasProgress = book.progress != null && book.progress.position > 0

            when (progressFilter) {
                "hide-finished" -> !isFinished
                "in-progress" -> hasProgress && !isFinished
                "not-started" -> !hasProgress && !isFinished
                "finished" -> isFinished
                else -> true
            }
        }
    }

    // Sort audiobooks
    val sortedBooks = remember(filteredBooks, sortBy) {
        filteredBooks.sortedWith(
            when (sortBy) {
                "title" -> compareBy { it.title }
                "author" -> compareBy { it.author ?: "" }
                "series" -> compareBy<com.sappho.audiobooks.domain.model.Audiobook> { it.series ?: "\uFFFF" }
                    .thenBy { it.seriesPosition ?: 0f }
                "genre" -> compareBy { it.genre ?: "" }
                "recent" -> compareByDescending { it.createdAt }
                else -> compareBy { it.title }
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBackClick) {
                Icon(
                    imageVector = Icons.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = Color.White
                )
            }
            Text(
                text = "${sortedBooks.size} ${if (sortedBooks.size == 1) "Book" else "Books"}",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        }

        // Filters
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Progress Filter
            FilterDropdown(
                label = "Show",
                value = when (progressFilter) {
                    "all" -> "All Books"
                    "hide-finished" -> "Hide Finished"
                    "in-progress" -> "In Progress"
                    "not-started" -> "Not Started"
                    "finished" -> "Finished"
                    else -> "All Books"
                },
                options = listOf(
                    "all" to "All Books",
                    "hide-finished" to "Hide Finished",
                    "in-progress" to "In Progress",
                    "not-started" to "Not Started",
                    "finished" to "Finished"
                ),
                onSelect = { progressFilter = it },
                modifier = Modifier.weight(1f)
            )

            // Sort Filter
            FilterDropdown(
                label = "Sort",
                value = when (sortBy) {
                    "title" -> "Title"
                    "author" -> "Author"
                    "series" -> "Series"
                    "genre" -> "Genre"
                    "recent" -> "Recent"
                    else -> "Title"
                },
                options = listOf(
                    "title" to "Title",
                    "author" to "Author",
                    "series" to "Series",
                    "genre" to "Genre",
                    "recent" to "Recent"
                ),
                onSelect = { sortBy = it },
                modifier = Modifier.weight(1f)
            )
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
                BookGridItem(
                    book = book,
                    serverUrl = serverUrl,
                    showSeriesPosition = sortBy == "series",
                    onClick = { onAudiobookClick(book.id) }
                )
            }
        }
    }
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
