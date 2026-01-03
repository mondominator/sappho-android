package com.sappho.audiobooks.presentation.profile

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest
import com.sappho.audiobooks.BuildConfig
import com.sappho.audiobooks.presentation.theme.*
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    onLogout: () -> Unit = {},
    onAvatarChanged: () -> Unit = {},
    viewModel: ProfileViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val user by viewModel.user.collectAsState()
    val stats by viewModel.stats.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val isSaving by viewModel.isSaving.collectAsState()
    val saveMessage by viewModel.saveMessage.collectAsState()
    val serverUrl by viewModel.serverUrl.collectAsState()
    val avatarUri by viewModel.avatarUri.collectAsState()
    val serverVersion by viewModel.serverVersion.collectAsState()
    val avatarUpdated by viewModel.avatarUpdated.collectAsState()
    val coroutineScope = rememberCoroutineScope()

    // Avatar picker
    var selectedAvatarFile by remember { mutableStateOf<File?>(null) }
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            viewModel.setAvatarUri(it)
            try {
                // Load and compress image to avoid "File too large" errors (server limit is 5MB)
                val inputStream = context.contentResolver.openInputStream(it)
                val originalBitmap = android.graphics.BitmapFactory.decodeStream(inputStream)
                inputStream?.close()

                if (originalBitmap != null) {
                    // Scale down to max 800x800 while maintaining aspect ratio
                    val maxSize = 800
                    val scale = minOf(
                        maxSize.toFloat() / originalBitmap.width,
                        maxSize.toFloat() / originalBitmap.height,
                        1f // Don't upscale
                    )
                    val scaledBitmap = if (scale < 1f) {
                        android.graphics.Bitmap.createScaledBitmap(
                            originalBitmap,
                            (originalBitmap.width * scale).toInt(),
                            (originalBitmap.height * scale).toInt(),
                            true
                        )
                    } else {
                        originalBitmap
                    }

                    // Save as compressed JPEG
                    val tempFile = File(context.cacheDir, "avatar_temp.jpg")
                    tempFile.outputStream().use { output ->
                        scaledBitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 85, output)
                    }

                    // Clean up bitmaps
                    if (scaledBitmap != originalBitmap) {
                        scaledBitmap.recycle()
                    }
                    originalBitmap.recycle()

                    selectedAvatarFile = tempFile
                    viewModel.updateProfileWithAvatar(null, null, tempFile, "image/jpeg")
                }
            } catch (e: Exception) {
                // Silently ignore image compression errors
            }
        }
    }

    // Snackbar
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(saveMessage) {
        saveMessage?.let { message ->
            snackbarHostState.showSnackbar(message)
            viewModel.clearMessage()
        }
    }

    // Watch for avatar updates
    LaunchedEffect(avatarUpdated) {
        if (avatarUpdated) {
            onAvatarChanged()
            viewModel.clearAvatarUpdatedFlag()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = SapphoBackground
    ) { paddingValues ->
        var showEditDialog by remember { mutableStateOf(false) }

        PullToRefreshBox(
            isRefreshing = isLoading,
            onRefresh = { viewModel.refresh() },
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
            ) {
                // Hero section - avatar left, info right
                val avatarGradient = LibraryGradients.forAvatar(user?.username ?: "U")
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(SapphoSurfaceLight)
                        .padding(Spacing.M),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Avatar (display only - edit via Edit Profile dialog)
                    Box(
                        modifier = Modifier
                            .size(80.dp)
                            .clip(CircleShape)
                            .background(Brush.verticalGradient(avatarGradient)),
                        contentAlignment = Alignment.Center
                    ) {
                        when {
                            avatarUri != null -> {
                                AsyncImage(
                                    model = avatarUri,
                                    contentDescription = "Avatar Preview",
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                            }
                            user?.avatar != null && serverUrl != null -> {
                                // Force fresh load with timestamp
                                val avatarUrl = "$serverUrl/api/profile/avatar?_=${System.currentTimeMillis()}"
                                AsyncImage(
                                    model = ImageRequest.Builder(context)
                                        .data(avatarUrl)
                                        .diskCachePolicy(CachePolicy.DISABLED)
                                        .memoryCachePolicy(CachePolicy.DISABLED)
                                        .crossfade(false)
                                        .build(),
                                    contentDescription = "Avatar",
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                            }
                            else -> {
                                Text(
                                    text = (user?.displayName ?: user?.username)?.firstOrNull()?.uppercaseChar()?.toString() ?: "U",
                                    color = SapphoText,
                                    style = MaterialTheme.typography.headlineMedium
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.width(Spacing.M))

                    // User info
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = user?.displayName ?: user?.username ?: "User",
                            style = MaterialTheme.typography.titleLarge,
                            color = SapphoText
                        )
                        Text(
                            text = "@${user?.username ?: ""}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = SapphoIconDefault
                        )
                        Spacer(modifier = Modifier.height(Spacing.XXS))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(Spacing.XS)
                        ) {
                            Surface(
                                shape = RoundedCornerShape(Spacing.S),
                                color = if (user?.isAdmin == 1) SapphoSuccess.copy(alpha = 0.15f)
                                else SapphoInfo.copy(alpha = 0.15f)
                            ) {
                                Text(
                                    text = if (user?.isAdmin == 1) "Admin" else "User",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (user?.isAdmin == 1) SapphoSuccess else SapphoInfo,
                                    modifier = Modifier.padding(horizontal = Spacing.XS, vertical = 2.dp)
                                )
                            }
                            user?.createdAt?.let { createdAt ->
                                val dateText = try {
                                    val inputFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
                                    val outputFormat = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
                                    val parsed = inputFormat.parse(createdAt.split(".")[0])
                                    "Joined ${outputFormat.format(parsed ?: Date())}"
                                } catch (e: Exception) { "" }
                                if (dateText.isNotEmpty()) {
                                    Text(
                                        text = dateText,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = SapphoTextMuted
                                    )
                                }
                            }
                        }
                    }

                    // Edit button
                    IconButton(onClick = { showEditDialog = true }) {
                        Icon(
                            imageVector = Icons.Outlined.Edit,
                            contentDescription = "Edit Profile",
                            tint = SapphoIconDefault
                        )
                    }
                }

                // Profile content
                ProfileTab(
                    stats = stats,
                    serverUrl = serverUrl,
                    serverVersion = serverVersion,
                    onLogout = onLogout
                )
            }

            // Edit Profile Dialog
            if (showEditDialog) {
                EditProfileDialog(
                    user = user,
                    avatarUri = avatarUri,
                    serverUrl = serverUrl,
                    onDismiss = { showEditDialog = false },
                    onPickImage = { imagePickerLauncher.launch("image/*") },
                    onRemoveAvatar = {
                        selectedAvatarFile = null
                        viewModel.setAvatarUri(null)
                        if (user?.avatar != null) {
                            viewModel.deleteAvatar()
                        }
                    },
                    onSave = { displayName, email ->
                        viewModel.updateProfileWithAvatar(displayName, email, null)
                        showEditDialog = false
                    },
                    onUpdatePassword = { currentPassword, newPassword ->
                        viewModel.updatePassword(currentPassword, newPassword)
                    }
                )
            }
        }
    }
}

@Composable
private fun ProfileTab(
    stats: com.sappho.audiobooks.domain.model.UserStats?,
    serverUrl: String?,
    serverVersion: String?,
    onLogout: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(Spacing.M)
    ) {
        stats?.let { userStats ->
            // Main stats row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.S)
            ) {
                StatCard(
                    modifier = Modifier.weight(1f),
                    value = formatListenTime(userStats.totalListenTime),
                    label = "Listen Time",
                    icon = Icons.Outlined.Headphones,
                    color = SapphoInfo
                )
                StatCard(
                    modifier = Modifier.weight(1f),
                    value = userStats.booksCompleted.toString(),
                    label = "Completed",
                    icon = Icons.Outlined.CheckCircle,
                    color = SapphoSuccess
                )
            }

            Spacer(modifier = Modifier.height(Spacing.S))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.S)
            ) {
                StatCard(
                    modifier = Modifier.weight(1f),
                    value = userStats.currentlyListening.toString(),
                    label = "In Progress",
                    icon = Icons.Outlined.PlayCircle,
                    color = SapphoWarning
                )
                StatCard(
                    modifier = Modifier.weight(1f),
                    value = "${userStats.currentStreak} days",
                    label = "Streak",
                    icon = Icons.Outlined.LocalFireDepartment,
                    color = SapphoError
                )
            }

            Spacer(modifier = Modifier.height(Spacing.S))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.S)
            ) {
                StatCard(
                    modifier = Modifier.weight(1f),
                    value = userStats.booksStarted.toString(),
                    label = "Started",
                    icon = Icons.Outlined.MenuBook,
                    color = SapphoInfo
                )
                StatCard(
                    modifier = Modifier.weight(1f),
                    value = "${userStats.activeDaysLast30}",
                    label = "Active Days",
                    icon = Icons.Outlined.CalendarMonth,
                    color = SapphoIconDefault
                )
            }

            // Top authors
            if (userStats.topAuthors.isNotEmpty()) {
                Spacer(modifier = Modifier.height(Spacing.L))
                SectionCard(
                    title = "Top Authors",
                    icon = Icons.Outlined.Person
                ) {
                    userStats.topAuthors.take(3).forEach { author ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = Spacing.XS),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = author.author,
                                color = SapphoText,
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                text = "${author.bookCount} books • ${formatListenTime(author.listenTime)}",
                                color = SapphoIconDefault,
                                style = MaterialTheme.typography.labelMedium
                            )
                        }
                    }
                }
            }

            // Top genres
            if (userStats.topGenres.isNotEmpty()) {
                Spacer(modifier = Modifier.height(Spacing.S))
                SectionCard(
                    title = "Top Genres",
                    icon = Icons.Outlined.Category
                ) {
                    userStats.topGenres.take(3).forEach { genre ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = Spacing.XS),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = genre.genre,
                                color = SapphoText,
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                text = "${genre.bookCount} books • ${formatListenTime(genre.listenTime)}",
                                color = SapphoIconDefault,
                                style = MaterialTheme.typography.labelMedium
                            )
                        }
                    }
                }
            }

            // Recent Activity
            if (userStats.recentActivity.isNotEmpty()) {
                Spacer(modifier = Modifier.height(Spacing.S))
                SectionCard(
                    title = "Recent Activity",
                    icon = Icons.Outlined.History
                ) {
                    userStats.recentActivity.take(5).forEach { item ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = Spacing.XS),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Cover image
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(SapphoProgressTrack)
                            ) {
                                if (item.coverImage != null && serverUrl != null) {
                                    AsyncImage(
                                        model = "$serverUrl/api/audiobooks/${item.id}/cover",
                                        contentDescription = item.title,
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = ContentScale.Crop
                                    )
                                } else {
                                    Box(
                                        modifier = Modifier.fillMaxSize(),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = item.title.take(1).uppercase(),
                                            color = SapphoIconDefault,
                                            style = MaterialTheme.typography.titleMedium
                                        )
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.width(Spacing.S))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = item.title,
                                    color = SapphoText,
                                    style = MaterialTheme.typography.bodyMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                item.author?.let { author ->
                                    Text(
                                        text = author,
                                        color = SapphoTextMuted,
                                        style = MaterialTheme.typography.labelMedium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                            // Progress indicator
                            if (item.completed == 1) {
                                Icon(
                                    imageVector = Icons.Outlined.CheckCircle,
                                    contentDescription = "Completed",
                                    tint = SapphoSuccess,
                                    modifier = Modifier.size(IconSize.Medium)
                                )
                            } else if (item.duration != null && item.duration > 0) {
                                val progress = (item.position.toFloat() / item.duration).coerceIn(0f, 1f)
                                Text(
                                    text = "${(progress * 100).toInt()}%",
                                    color = SapphoInfo,
                                    style = MaterialTheme.typography.labelMedium
                                )
                            }
                        }
                    }
                }
            }
        } ?: run {
            // No stats available - improved empty state
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = Spacing.XL),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = Icons.Outlined.Headphones,
                    contentDescription = null,
                    tint = SapphoIconMuted,
                    modifier = Modifier.size(IconSize.Hero)
                )
                Spacer(modifier = Modifier.height(Spacing.M))
                Text(
                    text = "No listening stats yet",
                    color = SapphoTextMuted,
                    style = MaterialTheme.typography.bodyLarge
                )
                Spacer(modifier = Modifier.height(Spacing.XS))
                Text(
                    text = "Start listening to see your stats!",
                    color = SapphoIconMuted,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }

        // About section
        Spacer(modifier = Modifier.height(Spacing.L))

        SectionCard(
            title = "About",
            icon = Icons.Outlined.Info
        ) {
            // App Version
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = Spacing.XS),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "App Version",
                    color = SapphoTextMuted,
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = BuildConfig.VERSION_NAME,
                    color = SapphoText,
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            // Server Version
            serverVersion?.let { version ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = Spacing.XS),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Server Version",
                        color = SapphoTextMuted,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = version,
                        color = SapphoText,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }

            Spacer(modifier = Modifier.height(Spacing.XS))

            // Logout button
            Button(
                onClick = onLogout,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = SapphoError.copy(alpha = 0.15f),
                    contentColor = SapphoError
                ),
                shape = RoundedCornerShape(Spacing.S)
            ) {
                Icon(
                    imageVector = Icons.Outlined.Logout,
                    contentDescription = null,
                    modifier = Modifier.size(IconSize.Small + 2.dp)
                )
                Spacer(modifier = Modifier.width(Spacing.XS))
                Text("Logout")
            }
        }

        Spacer(modifier = Modifier.height(Spacing.L))
    }
}

@Composable
private fun StatCard(
    modifier: Modifier = Modifier,
    value: String,
    label: String,
    icon: ImageVector,
    color: Color
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(Spacing.M),
        color = SapphoSurfaceLight
    ) {
        Column(
            modifier = Modifier
                .padding(Spacing.M)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(IconSize.Standard)
            )
            Spacer(modifier = Modifier.height(Spacing.XS))
            Text(
                text = value,
                style = MaterialTheme.typography.headlineSmall,
                color = SapphoText,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = SapphoIconDefault,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun SectionCard(
    title: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(Spacing.M),
        color = SapphoSurfaceLight
    ) {
        Column(
            modifier = Modifier
                .padding(Spacing.M)
                .animateContentSize()
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(bottom = Spacing.S)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = SapphoInfo,
                    modifier = Modifier.size(IconSize.Medium)
                )
                Spacer(modifier = Modifier.width(Spacing.XS))
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = SapphoText
                )
            }
            content()
        }
    }
}

private fun formatListenTime(seconds: Long): String {
    val hours = seconds / 3600
    val minutes = (seconds % 3600) / 60

    return when {
        hours >= 24 -> {
            val days = hours / 24
            val remainingHours = hours % 24
            if (remainingHours > 0) "${days}d ${remainingHours}h" else "${days}d"
        }
        hours > 0 -> "${hours}h ${minutes}m"
        minutes > 0 -> "${minutes}m"
        else -> "${seconds}s"
    }
}
