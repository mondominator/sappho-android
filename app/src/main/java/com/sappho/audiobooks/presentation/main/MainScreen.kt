package com.sappho.audiobooks.presentation.main

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.BookmarkAdded
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LibraryBooks
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Cast
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material.icons.outlined.AudioFile
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Error
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.text.style.TextOverflow
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.sappho.audiobooks.R
import com.sappho.audiobooks.BuildConfig
import com.sappho.audiobooks.presentation.home.HomeScreen
import com.sappho.audiobooks.domain.model.User
import com.sappho.audiobooks.presentation.library.LibraryScreen
import com.sappho.audiobooks.presentation.profile.ProfileScreen
import com.sappho.audiobooks.presentation.search.SearchScreen
import com.sappho.audiobooks.presentation.detail.AudiobookDetailScreen
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavType
import androidx.navigation.navArgument
import com.sappho.audiobooks.presentation.player.MinimizedPlayerBar
import com.sappho.audiobooks.service.AudioPlaybackService
import com.sappho.audiobooks.service.PlayerState
import com.sappho.audiobooks.cast.CastHelper
import javax.inject.Inject

sealed class Screen(val route: String, val title: String) {
    data object Home : Screen("home", "Home")
    data object Library : Screen("library?author={author}&series={series}", "Library") {
        const val baseRoute = "library"
        fun createRoute(author: String? = null, series: String? = null): String {
            val encodedAuthor = author?.let { java.net.URLEncoder.encode(it, "UTF-8") }
            val encodedSeries = series?.let { java.net.URLEncoder.encode(it, "UTF-8") }
            return buildString {
                append("library")
                val params = mutableListOf<String>()
                encodedAuthor?.let { params.add("author=$it") }
                encodedSeries?.let { params.add("series=$it") }
                if (params.isNotEmpty()) {
                    append("?")
                    append(params.joinToString("&"))
                }
            }
        }
    }
    data object Search : Screen("search", "Search")
    data object ReadingList : Screen("reading-list", "Reading List")
    data object Profile : Screen("profile", "Profile")
    data object Settings : Screen("settings", "Settings")
    data object Admin : Screen("admin", "Admin")
    data object AudiobookDetail : Screen("audiobook/{id}", "Audiobook Detail") {
        fun createRoute(id: Int) = "audiobook/$id"
    }
    data object Collections : Screen("collections", "Collections")
    data object CollectionDetail : Screen("collection/{id}", "Collection Detail") {
        fun createRoute(id: Int) = "collection/$id"
    }
}

@Composable
fun MainScreen(
    onLogout: () -> Unit,
    initialAuthor: String? = null,
    initialSeries: String? = null,
    viewModel: MainViewModel = hiltViewModel()
) {
    val navController = rememberNavController()

    // Handle initial navigation from intent (e.g., from PlayerActivity)
    LaunchedEffect(initialAuthor, initialSeries) {
        if (initialAuthor != null || initialSeries != null) {
            navController.navigate(Screen.Library.createRoute(author = initialAuthor, series = initialSeries)) {
                launchSingleTop = true
            }
        }
    }
    var showUserMenu by remember { mutableStateOf(false) }
    val user by viewModel.user.collectAsState()
    val serverUrl by viewModel.serverUrl.collectAsState()
    val serverVersion by viewModel.serverVersion.collectAsState()
    val currentAudiobook by viewModel.playerState.currentAudiobook.collectAsState()
    val context = androidx.compose.ui.platform.LocalContext.current

    var showCastDialog by remember { mutableStateOf(false) }
    var showDownloadsDialog by remember { mutableStateOf(false) }
    var showUploadDialog by remember { mutableStateOf(false) }
    var downloadToDelete by remember { mutableStateOf<Int?>(null) }
    val castHelper = viewModel.castHelper
    val downloadManager = viewModel.downloadManager
    val downloadedBooks by downloadManager.downloadedBooks.collectAsState()

    Scaffold(
        topBar = {
            TopBar(
                navController = navController,
                user = user,
                serverUrl = serverUrl,
                serverVersion = serverVersion,
                showUserMenu = showUserMenu,
                onUserMenuToggle = { showUserMenu = !showUserMenu },
                onProfileClick = {
                    showUserMenu = false
                    navController.navigate(Screen.Profile.route) {
                        launchSingleTop = true
                    }
                },
                onReadingListClick = {
                    showUserMenu = false
                    navController.navigate(Screen.ReadingList.route) {
                        launchSingleTop = true
                    }
                },
                onCollectionsClick = {
                    showUserMenu = false
                    navController.navigate(Screen.Collections.route) {
                        launchSingleTop = true
                    }
                },
                onSettingsClick = {
                    showUserMenu = false
                    navController.navigate(Screen.Settings.route) {
                        launchSingleTop = true
                    }
                },
                onAdminClick = {
                    showUserMenu = false
                    navController.navigate(Screen.Admin.route) {
                        launchSingleTop = true
                    }
                },
                onLogout = {
                    showUserMenu = false
                    onLogout()
                },
                onDismissMenu = { showUserMenu = false },
                onLogoClick = {
                    navController.navigate(Screen.Home.route) {
                        popUpTo(Screen.Home.route) {
                            inclusive = true
                        }
                        launchSingleTop = true
                    }
                },
                onDownloadsClick = {
                    showUserMenu = false
                    showDownloadsDialog = true
                },
                onUploadClick = {
                    showUserMenu = false
                    showUploadDialog = true
                },
                downloadCount = downloadedBooks.size
            )
        },
        bottomBar = {
            if (currentAudiobook != null) {
                MinimizedPlayerBar(
                    playerState = viewModel.playerState,
                    serverUrl = serverUrl,
                    castHelper = castHelper,
                    onExpand = {
                        currentAudiobook?.let { book ->
                            val intent = android.content.Intent(context, com.sappho.audiobooks.presentation.player.PlayerActivity::class.java)
                            intent.putExtra("AUDIOBOOK_ID", book.id)
                            intent.putExtra("FROM_MINIMIZED", true)
                            context.startActivity(intent)
                            // Apply slide up animation
                            (context as? android.app.Activity)?.overridePendingTransition(
                                R.anim.slide_up,
                                R.anim.stay
                            )
                        }
                    },
                    onCastClick = { showCastDialog = true }
                )
            }
        },
        containerColor = Color(0xFF0A0E1A)
    ) { paddingValues ->
        Box {
            NavHost(
                navController = navController,
                startDestination = Screen.Home.route,
                modifier = Modifier.padding(paddingValues)
            ) {
            composable(Screen.Home.route) {
                HomeScreen(
                    onAudiobookClick = { audiobookId ->
                        navController.navigate(Screen.AudiobookDetail.createRoute(audiobookId))
                    }
                )
            }
            composable(
                route = Screen.Library.route,
                arguments = listOf(
                    navArgument("author") {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = null
                    },
                    navArgument("series") {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = null
                    }
                )
            ) { backStackEntry ->
                val author = backStackEntry.arguments?.getString("author")?.let {
                    java.net.URLDecoder.decode(it, "UTF-8")
                }
                val series = backStackEntry.arguments?.getString("series")?.let {
                    java.net.URLDecoder.decode(it, "UTF-8")
                }
                LibraryScreen(
                    onAudiobookClick = { audiobookId ->
                        navController.navigate(Screen.AudiobookDetail.createRoute(audiobookId))
                    },
                    initialAuthor = author,
                    initialSeries = series
                )
            }
            composable(Screen.Search.route) {
                SearchScreen(
                    onAudiobookClick = { audiobookId ->
                        navController.navigate(Screen.AudiobookDetail.createRoute(audiobookId))
                    },
                    onSeriesClick = { series ->
                        navController.navigate(Screen.Library.createRoute(series = series))
                    },
                    onAuthorClick = { author ->
                        navController.navigate(Screen.Library.createRoute(author = author))
                    }
                )
            }
            composable(Screen.ReadingList.route) {
                com.sappho.audiobooks.presentation.readinglist.ReadingListScreen(
                    onAudiobookClick = { audiobookId ->
                        navController.navigate(Screen.AudiobookDetail.createRoute(audiobookId))
                    },
                    onBackClick = { navController.navigateUp() }
                )
            }
            composable(Screen.Profile.route) {
                ProfileScreen(onLogout = onLogout)
            }
            composable(Screen.Settings.route) {
                com.sappho.audiobooks.presentation.settings.SettingsScreen(
                    onBackClick = { navController.navigateUp() }
                )
            }
            composable(Screen.Admin.route) {
                com.sappho.audiobooks.presentation.admin.AdminScreen(
                    onBack = { navController.navigateUp() }
                )
            }
            composable(Screen.Collections.route) {
                com.sappho.audiobooks.presentation.collections.CollectionsScreen(
                    onCollectionClick = { collectionId ->
                        navController.navigate(Screen.CollectionDetail.createRoute(collectionId))
                    },
                    onBackClick = { navController.navigateUp() }
                )
            }
            composable(
                route = Screen.CollectionDetail.route,
                arguments = listOf(navArgument("id") { type = NavType.IntType })
            ) { backStackEntry ->
                val collectionId = backStackEntry.arguments?.getInt("id") ?: 0
                com.sappho.audiobooks.presentation.collections.CollectionDetailScreen(
                    collectionId = collectionId,
                    onBookClick = { audiobookId ->
                        navController.navigate(Screen.AudiobookDetail.createRoute(audiobookId))
                    },
                    onBackClick = { navController.navigateUp() }
                )
            }
            composable(
                route = Screen.AudiobookDetail.route,
                arguments = listOf(navArgument("id") { type = NavType.IntType })
            ) { backStackEntry ->
                val audiobookId = backStackEntry.arguments?.getInt("id") ?: 0
                val context = androidx.compose.ui.platform.LocalContext.current
                AudiobookDetailScreen(
                    audiobookId = audiobookId,
                    onBackClick = { navController.navigateUp() },
                    onPlayClick = { id, position ->
                        android.util.Log.d("MainScreen", "onPlayClick: id=$id, position=$position")
                        val intent = android.content.Intent(context, com.sappho.audiobooks.presentation.player.PlayerActivity::class.java)
                        intent.putExtra("AUDIOBOOK_ID", id)
                        intent.putExtra("START_POSITION", position ?: 0)
                        context.startActivity(intent)
                    },
                    onAuthorClick = { author ->
                        navController.navigate(Screen.Library.createRoute(author = author))
                    },
                    onSeriesClick = { series ->
                        navController.navigate(Screen.Library.createRoute(series = series))
                    },
                    isAdmin = user?.isAdmin == 1
                )
            }
        }

            // Invisible overlay to dismiss menu when clicking outside
            if (showUserMenu) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = { showUserMenu = false }
                        )
                )
            }
        }

        // Cast Dialog
        if (showCastDialog) {
            val isCasting = castHelper.isCasting()
            val availableRoutes = remember { castHelper.getAvailableRoutes(context) }

            AlertDialog(
                onDismissRequest = { showCastDialog = false },
                title = { Text("Cast to Device", color = Color.White) },
                text = {
                    Column {
                        if (isCasting) {
                            Text(
                                "Currently casting",
                                color = Color(0xFF10b981),
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                            TextButton(
                                onClick = {
                                    castHelper.disconnectCast()
                                    showCastDialog = false
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Disconnect", color = Color(0xFFef4444))
                            }
                        } else if (availableRoutes.isEmpty()) {
                            Text(
                                "No Cast devices found on your network. Make sure your Cast devices are powered on and connected to the same WiFi network.",
                                color = Color(0xFF9ca3af)
                            )
                        } else {
                            Text(
                                "Select a device:",
                                color = Color(0xFF9ca3af),
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                            availableRoutes.forEach { route ->
                                TextButton(
                                    onClick = {
                                        castHelper.selectRoute(context, route)

                                        // If there's a current audiobook, cast it after connecting
                                        currentAudiobook?.let { book ->
                                            serverUrl?.let { url ->
                                                val streamUrl = "$url/api/audiobooks/${book.id}/stream"
                                                val coverUrl = if (book.coverImage != null) {
                                                    "$url/api/audiobooks/${book.id}/cover"
                                                } else null

                                                // Pause local playback
                                                if (viewModel.playerState.isPlaying.value) {
                                                    AudioPlaybackService.instance?.togglePlayPause()
                                                }

                                                // Send to cast after a delay for connection
                                                val currentPos = viewModel.playerState.currentPosition.value
                                                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                                                    castHelper.castAudiobook(
                                                        audiobook = book,
                                                        streamUrl = streamUrl,
                                                        coverUrl = coverUrl,
                                                        currentPosition = currentPos
                                                    )
                                                }, 2000)
                                            }
                                        }

                                        showCastDialog = false
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = route.name ?: "Unknown Device",
                                            color = Color.White
                                        )
                                        Icon(
                                            imageVector = Icons.Default.Cast,
                                            contentDescription = null,
                                            tint = Color(0xFF9ca3af),
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showCastDialog = false }) {
                        Text("Cancel", color = Color(0xFF3b82f6))
                    }
                },
                containerColor = Color(0xFF1e293b)
            )
        }

        // Downloads Dialog
        if (showDownloadsDialog) {
            AlertDialog(
                onDismissRequest = { showDownloadsDialog = false },
                title = { Text("Downloaded Books", color = Color.White) },
                text = {
                    Column(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (downloadedBooks.isEmpty()) {
                            Text(
                                "No downloaded books yet. Download books from the book detail page to listen offline.",
                                color = Color(0xFF9ca3af)
                            )
                        } else {
                            downloadedBooks.forEach { downloadedBook ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text(
                                            text = downloadedBook.audiobook.title,
                                            color = Color.White,
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Medium,
                                            maxLines = 1
                                        )
                                        Text(
                                            text = formatFileSize(downloadedBook.fileSize),
                                            color = Color(0xFF9ca3af),
                                            fontSize = 12.sp
                                        )
                                    }
                                    IconButton(
                                        onClick = {
                                            downloadToDelete = downloadedBook.audiobook.id
                                        }
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Delete,
                                            contentDescription = "Delete",
                                            tint = Color(0xFFef4444),
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showDownloadsDialog = false }) {
                        Text("Close", color = Color(0xFF3b82f6))
                    }
                },
                containerColor = Color(0xFF1e293b)
            )
        }

        // Delete Download Confirmation Dialog
        if (downloadToDelete != null) {
            val bookToDelete = downloadedBooks.find { it.audiobook.id == downloadToDelete }
            AlertDialog(
                onDismissRequest = { downloadToDelete = null },
                title = { Text("Remove Download", color = Color.White) },
                text = {
                    Text(
                        "Remove \"${bookToDelete?.audiobook?.title ?: "this book"}\" from downloads? This will only delete the local file - your listening progress on the server will not be affected.",
                        color = Color(0xFFd1d5db)
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            downloadToDelete?.let { id ->
                                downloadManager.deleteDownload(id)
                            }
                            downloadToDelete = null
                        }
                    ) {
                        Text("Remove", color = Color(0xFFef4444))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { downloadToDelete = null }) {
                        Text("Cancel", color = Color(0xFF3b82f6))
                    }
                },
                containerColor = Color(0xFF1e293b)
            )
        }

        // Upload Dialog
        if (showUploadDialog) {
            UploadDialog(
                viewModel = viewModel,
                onDismiss = { showUploadDialog = false }
            )
        }
    }
}

@Composable
private fun UploadDialog(
    viewModel: MainViewModel,
    onDismiss: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val uploadState by viewModel.uploadState.collectAsState()
    val uploadProgress by viewModel.uploadProgress.collectAsState()
    val uploadResult by viewModel.uploadResult.collectAsState()

    var selectedFiles by remember { mutableStateOf<List<Uri>>(emptyList()) }
    var title by remember { mutableStateOf("") }
    var author by remember { mutableStateOf("") }
    var narrator by remember { mutableStateOf("") }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        selectedFiles = uris
    }

    AlertDialog(
        onDismissRequest = {
            if (uploadState != UploadState.UPLOADING) {
                viewModel.clearUploadResult()
                onDismiss()
            }
        },
        title = { Text("Upload Audiobooks", color = Color.White) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Supported: MP3, M4A, M4B, FLAC, OGG, WAV",
                    color = Color(0xFF9ca3af),
                    fontSize = 12.sp
                )

                // File picker button
                Button(
                    onClick = { filePickerLauncher.launch(arrayOf("audio/*")) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF3b82f6)),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(Icons.Outlined.FolderOpen, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Select Files")
                }

                // Selected files display
                if (selectedFiles.isNotEmpty()) {
                    Text(
                        text = "${selectedFiles.size} file(s) selected",
                        color = Color(0xFF10b981),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )

                    selectedFiles.take(5).forEach { uri ->
                        val fileName = uri.lastPathSegment?.substringAfterLast("/") ?: "Unknown file"
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Outlined.AudioFile, contentDescription = null, tint = Color(0xFF9ca3af), modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(fileName, color = Color(0xFFd1d5db), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    if (selectedFiles.size > 5) {
                        Text("...and ${selectedFiles.size - 5} more", color = Color(0xFF6b7280), fontSize = 11.sp)
                    }

                    TextButton(onClick = { selectedFiles = emptyList() }) {
                        Text("Clear selection", color = Color(0xFFef4444), fontSize = 12.sp)
                    }

                    // Optional metadata
                    Text("Optional metadata (leave blank to auto-detect)", color = Color(0xFF6b7280), fontSize = 11.sp)

                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it },
                        label = { Text("Title") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = Color(0xFF3b82f6),
                            unfocusedBorderColor = Color(0xFF374151),
                            focusedLabelColor = Color(0xFF3b82f6),
                            unfocusedLabelColor = Color(0xFF9ca3af)
                        )
                    )

                    OutlinedTextField(
                        value = author,
                        onValueChange = { author = it },
                        label = { Text("Author") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = Color(0xFF3b82f6),
                            unfocusedBorderColor = Color(0xFF374151),
                            focusedLabelColor = Color(0xFF3b82f6),
                            unfocusedLabelColor = Color(0xFF9ca3af)
                        )
                    )

                    OutlinedTextField(
                        value = narrator,
                        onValueChange = { narrator = it },
                        label = { Text("Narrator") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = Color(0xFF3b82f6),
                            unfocusedBorderColor = Color(0xFF374151),
                            focusedLabelColor = Color(0xFF3b82f6),
                            unfocusedLabelColor = Color(0xFF9ca3af)
                        )
                    )
                }

                // Upload progress
                if (uploadState == UploadState.UPLOADING) {
                    LinearProgressIndicator(
                        progress = uploadProgress,
                        modifier = Modifier.fillMaxWidth(),
                        color = Color(0xFF3b82f6)
                    )
                    Text(
                        "Uploading... ${(uploadProgress * 100).toInt()}%",
                        color = Color(0xFF9ca3af),
                        fontSize = 12.sp
                    )
                }

                // Upload result
                uploadResult?.let { result ->
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        color = if (result.success) Color(0xFF10b981).copy(alpha = 0.1f) else Color(0xFFef4444).copy(alpha = 0.1f)
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                if (result.success) Icons.Outlined.CheckCircle else Icons.Outlined.Error,
                                contentDescription = null,
                                tint = if (result.success) Color(0xFF10b981) else Color(0xFFef4444),
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                result.message ?: if (result.success) "Upload complete" else "Upload failed",
                                color = if (result.success) Color(0xFF10b981) else Color(0xFFef4444),
                                fontSize = 13.sp
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (selectedFiles.isNotEmpty() && uploadResult == null) {
                Button(
                    onClick = {
                        viewModel.uploadAudiobooks(
                            context = context,
                            uris = selectedFiles,
                            title = title.ifBlank { null },
                            author = author.ifBlank { null },
                            narrator = narrator.ifBlank { null }
                        )
                    },
                    enabled = uploadState != UploadState.UPLOADING,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10b981))
                ) {
                    Text("Upload")
                }
            } else if (uploadResult != null) {
                Button(
                    onClick = {
                        selectedFiles = emptyList()
                        title = ""
                        author = ""
                        narrator = ""
                        viewModel.clearUploadResult()
                        onDismiss()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF3b82f6))
                ) {
                    Text("Done")
                }
            }
        },
        dismissButton = {
            if (uploadState != UploadState.UPLOADING && uploadResult == null) {
                TextButton(onClick = onDismiss) {
                    Text("Cancel", color = Color(0xFF9ca3af))
                }
            }
        },
        containerColor = Color(0xFF1e293b)
    )
}

private fun formatFileSize(size: Long): String {
    return when {
        size >= 1_073_741_824 -> String.format("%.1f GB", size / 1_073_741_824.0)
        size >= 1_048_576 -> String.format("%.1f MB", size / 1_048_576.0)
        size >= 1_024 -> String.format("%.1f KB", size / 1_024.0)
        else -> "$size B"
    }
}

@Composable
fun TopBar(
    navController: NavHostController,
    user: User?,
    serverUrl: String?,
    serverVersion: String?,
    showUserMenu: Boolean,
    onUserMenuToggle: () -> Unit,
    onProfileClick: () -> Unit,
    onReadingListClick: () -> Unit,
    onCollectionsClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onAdminClick: () -> Unit,
    onLogout: () -> Unit,
    onDismissMenu: () -> Unit,
    onLogoClick: () -> Unit,
    onDownloadsClick: () -> Unit,
    onUploadClick: () -> Unit,
    downloadCount: Int
) {
    val appVersion = BuildConfig.VERSION_NAME
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    Box(modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF1a1a1a))
                .border(width = 1.dp, color = Color(0xFF2a2a2a))
        ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Logo - just the S icon, clickable to go home
            Image(
                painter = painterResource(id = R.drawable.sappho_logo_icon),
                contentDescription = "Sappho Logo",
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .clickable(onClick = onLogoClick)
            )

            // Center Navigation Buttons
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val navItems = listOf(
                    Triple(Screen.Home, Icons.Default.Home, "Home"),
                    Triple(Screen.Library, Icons.Default.MenuBook, "Library"),
                    Triple(Screen.Search, Icons.Default.Search, "Search")
                )

                navItems.forEach { (screen, icon, label) ->
                    IconButton(
                        onClick = {
                            // Use base route for Library (without parameters)
                            val route = when (screen) {
                                Screen.Library -> Screen.Library.baseRoute
                                else -> screen.route
                            }
                            navController.navigate(route) {
                                // Pop up to home to avoid building up a large back stack
                                popUpTo(Screen.Home.route) {
                                    saveState = false
                                }
                                launchSingleTop = true
                            }
                        },
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = label,
                            tint = Color(0xFF9ca3af),
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }

            // User Avatar
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF3B82F6))
                    .clickable(onClick = onUserMenuToggle),
                contentAlignment = Alignment.Center
            ) {
                if (user?.avatar != null && serverUrl != null) {
                    AsyncImage(
                        model = "$serverUrl/api/profile/avatar",
                        contentDescription = "User Avatar",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Text(
                        text = user?.username?.first()?.uppercaseChar()?.toString() ?: "U",
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
        }

        // Dropdown Menu - positioned absolutely in the top right
        if (showUserMenu) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = (-20).dp, y = 65.dp)
                    .width(200.dp)
                    .background(
                        color = Color(0xFF1a1a1a),
                        shape = RoundedCornerShape(8.dp)
                    )
                    .border(
                        width = 1.dp,
                        color = Color(0xFF2a2a2a),
                        shape = RoundedCornerShape(8.dp)
                    )
                    .padding(8.dp)
            ) {
                Column {
                    UserMenuItem(
                        icon = Icons.Default.Person,
                        text = "Profile",
                        onClick = onProfileClick
                    )
                    UserMenuItem(
                        icon = Icons.Default.BookmarkAdded,
                        text = "Reading List",
                        onClick = onReadingListClick
                    )
                    UserMenuItem(
                        icon = Icons.Default.LibraryBooks,
                        text = "Collections",
                        onClick = onCollectionsClick
                    )
                    UserMenuItem(
                        icon = Icons.Default.Download,
                        text = if (downloadCount > 0) "Downloads ($downloadCount)" else "Downloads",
                        onClick = onDownloadsClick
                    )
                    if (user?.isAdmin == 1) {
                        UserMenuItem(
                            icon = Icons.Default.Upload,
                            text = "Upload",
                            onClick = onUploadClick
                        )
                        UserMenuItem(
                            icon = Icons.Default.Settings,
                            text = "Admin",
                            onClick = onAdminClick
                        )
                    }
                    UserMenuItem(
                        icon = Icons.Default.ExitToApp,
                        text = "Logout",
                        onClick = onLogout
                    )

                    // Version info at the bottom
                    Spacer(modifier = Modifier.height(8.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 4.dp)
                    ) {
                        Column {
                            Text(
                                text = "App v$appVersion",
                                color = Color(0xFF6b7280),
                                fontSize = 11.sp
                            )
                            if (serverVersion != null) {
                                Text(
                                    text = "Server v$serverVersion",
                                    color = Color(0xFF6b7280),
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun UserMenuItem(
    icon: ImageVector,
    text: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = text,
            tint = Color(0xFF9ca3af),
            modifier = Modifier.size(18.dp)
        )
        Text(
            text = text,
            color = Color(0xFFd1d5db),
            fontSize = 14.sp
        )
    }
}

