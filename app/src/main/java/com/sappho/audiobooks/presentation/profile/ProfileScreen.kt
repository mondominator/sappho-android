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
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.sappho.audiobooks.presentation.theme.*
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
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
    val coroutineScope = rememberCoroutineScope()

    // Avatar picker
    var selectedAvatarFile by remember { mutableStateOf<File?>(null) }
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            viewModel.setAvatarUri(it)
            try {
                val inputStream = context.contentResolver.openInputStream(it)
                val tempFile = File(context.cacheDir, "avatar_temp.jpg")
                inputStream?.use { input ->
                    tempFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
                selectedAvatarFile = tempFile
                // Auto-save avatar
                viewModel.updateProfileWithAvatar(null, null, tempFile)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    // Snackbar
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(saveMessage) {
        saveMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearMessage()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = SapphoBackground
    ) { paddingValues ->
        if (isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = SapphoInfo)
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                // Header with gradient background
                Box(
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
                        .padding(24.dp)
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // Avatar
                        Box(
                            modifier = Modifier
                                .size(100.dp)
                                .clip(CircleShape)
                                .background(SapphoInfo)
                                .clickable { imagePickerLauncher.launch("image/*") },
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
                                    AsyncImage(
                                        model = "$serverUrl/api/profile/avatar",
                                        contentDescription = "Avatar",
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = ContentScale.Crop
                                    )
                                }
                                else -> {
                                    Text(
                                        text = (user?.displayName ?: user?.username)?.firstOrNull()?.uppercaseChar()?.toString() ?: "U",
                                        color = Color.White,
                                        fontSize = 40.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                            // Edit overlay
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(Color.Black.copy(alpha = 0.3f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CameraAlt,
                                    contentDescription = "Change Avatar",
                                    tint = Color.White.copy(alpha = 0.9f),
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                        }

                        // Avatar controls
                        Row(
                            horizontalArrangement = Arrangement.Center,
                            modifier = Modifier.padding(top = 8.dp)
                        ) {
                            TextButton(
                                onClick = { imagePickerLauncher.launch("image/*") },
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Upload,
                                    contentDescription = null,
                                    tint = SapphoInfo,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Upload", fontSize = 13.sp, color = SapphoInfo)
                            }
                            if (user?.avatar != null || avatarUri != null) {
                                TextButton(
                                    onClick = {
                                        selectedAvatarFile = null
                                        viewModel.setAvatarUri(null)
                                        if (user?.avatar != null) {
                                            viewModel.deleteAvatar()
                                        }
                                    },
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.Delete,
                                        contentDescription = null,
                                        tint = SapphoError,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Remove", fontSize = 13.sp, color = SapphoError)
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Name
                        Text(
                            text = user?.displayName ?: user?.username ?: "User",
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        // Username
                        Text(
                            text = "@${user?.username ?: ""}",
                            fontSize = 14.sp,
                            color = SapphoIconDefault
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        // Role badge
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = if (user?.isAdmin == 1) SapphoSuccess.copy(alpha = 0.15f)
                            else SapphoInfo.copy(alpha = 0.15f)
                        ) {
                            Text(
                                text = if (user?.isAdmin == 1) "Administrator" else "User",
                                fontSize = 12.sp,
                                color = if (user?.isAdmin == 1) SapphoSuccess else SapphoInfo,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                            )
                        }

                        // Member since
                        user?.createdAt?.let { createdAt ->
                            Spacer(modifier = Modifier.height(8.dp))
                            val date = try {
                                val inputFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
                                val outputFormat = SimpleDateFormat("MMMM yyyy", Locale.getDefault())
                                val parsed = inputFormat.parse(createdAt.split(".")[0])
                                outputFormat.format(parsed ?: Date())
                            } catch (e: Exception) { createdAt }
                            Text(
                                text = "Member since $date",
                                fontSize = 12.sp,
                                color = SapphoTextMuted
                            )
                        }
                    }
                }

                // Profile content (scrollable)
                ProfileTab(stats, serverUrl)
            }
        }
    }
}

@Composable
private fun ProfileTab(
    stats: com.sappho.audiobooks.domain.model.UserStats?,
    serverUrl: String?
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        stats?.let { userStats ->
            // Main stats row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
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

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
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

            // Top authors
            if (userStats.topAuthors.isNotEmpty()) {
                Spacer(modifier = Modifier.height(24.dp))
                SectionCard(
                    title = "Top Authors",
                    icon = Icons.Outlined.Person
                ) {
                    userStats.topAuthors.take(3).forEach { author ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = author.author,
                                color = Color.White,
                                fontSize = 14.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                text = "${author.bookCount} books • ${formatListenTime(author.listenTime)}",
                                color = SapphoIconDefault,
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            }

            // Top genres
            if (userStats.topGenres.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                SectionCard(
                    title = "Top Genres",
                    icon = Icons.Outlined.Category
                ) {
                    userStats.topGenres.take(3).forEach { genre ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = genre.genre,
                                color = Color.White,
                                fontSize = 14.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                text = "${genre.bookCount} books • ${formatListenTime(genre.listenTime)}",
                                color = SapphoIconDefault,
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            }

            // Recent Activity
            if (userStats.recentActivity.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                SectionCard(
                    title = "Recent Activity",
                    icon = Icons.Outlined.History
                ) {
                    userStats.recentActivity.take(5).forEach { item ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
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
                                            fontSize = 16.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = item.title,
                                    color = Color.White,
                                    fontSize = 14.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                item.author?.let { author ->
                                    Text(
                                        text = author,
                                        color = SapphoTextMuted,
                                        fontSize = 12.sp,
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
                                    modifier = Modifier.size(20.dp)
                                )
                            } else if (item.duration != null && item.duration > 0) {
                                val progress = (item.position.toFloat() / item.duration).coerceIn(0f, 1f)
                                Text(
                                    text = "${(progress * 100).toInt()}%",
                                    color = SapphoInfo,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                }
            }
        } ?: run {
            // No stats available
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No listening stats yet",
                    color = SapphoTextMuted,
                    fontSize = 14.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
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
        shape = RoundedCornerShape(16.dp),
        color = SapphoSurfaceLight
    ) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = value,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = label,
                fontSize = 12.sp,
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
        shape = RoundedCornerShape(16.dp),
        color = SapphoSurfaceLight
    ) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .animateContentSize()
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(bottom = 12.dp)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = SapphoInfo,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = title,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White
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
