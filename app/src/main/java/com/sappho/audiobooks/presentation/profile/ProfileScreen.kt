package com.sappho.audiobooks.presentation.profile

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
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

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun ProfileScreen(
    viewModel: ProfileViewModel = hiltViewModel(),
    onLogout: () -> Unit
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

    // Admin state
    val users by viewModel.users.collectAsState()
    val aiSettings by viewModel.aiSettings.collectAsState()
    val isScanning by viewModel.isScanning.collectAsState()
    val scanResult by viewModel.scanResult.collectAsState()

    // Tab state - 3 tabs if admin, 2 otherwise
    val isAdmin = user?.isAdmin == 1
    val pagerState = rememberPagerState(pageCount = { if (isAdmin) 3 else 2 })
    val coroutineScope = rememberCoroutineScope()

    // Edit mode state
    var isEditMode by remember { mutableStateOf(false) }
    var displayName by remember(user) { mutableStateOf(user?.displayName ?: "") }
    var email by remember(user) { mutableStateOf(user?.email ?: "") }

    // Password dialog
    var showPasswordDialog by remember { mutableStateOf(false) }
    var currentPassword by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var showCurrentPassword by remember { mutableStateOf(false) }
    var showNewPassword by remember { mutableStateOf(false) }

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
        containerColor = Color(0xFF0A0E1A)
    ) { paddingValues ->
        if (isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = Color(0xFF3B82F6))
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
                                    Color(0xFF1e3a5f),
                                    Color(0xFF0A0E1A)
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
                                .background(Color(0xFF3B82F6))
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
                                    tint = Color(0xFF3b82f6),
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Upload", fontSize = 13.sp, color = Color(0xFF3b82f6))
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
                                        tint = Color(0xFFef4444),
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Remove", fontSize = 13.sp, color = Color(0xFFef4444))
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
                            color = Color(0xFF9ca3af)
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        // Role badge
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = if (user?.isAdmin == 1) Color(0xFF10b981).copy(alpha = 0.15f)
                            else Color(0xFF3b82f6).copy(alpha = 0.15f)
                        ) {
                            Text(
                                text = if (user?.isAdmin == 1) "Administrator" else "User",
                                fontSize = 12.sp,
                                color = if (user?.isAdmin == 1) Color(0xFF10b981) else Color(0xFF3b82f6),
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
                                color = Color(0xFF6b7280)
                            )
                        }
                    }
                }

                // Tab Row
                TabRow(
                    selectedTabIndex = pagerState.currentPage,
                    containerColor = Color(0xFF0A0E1A),
                    contentColor = Color(0xFF3b82f6),
                    indicator = { tabPositions ->
                        if (pagerState.currentPage < tabPositions.size) {
                            Box(
                                Modifier
                                    .tabIndicatorOffset(tabPositions[pagerState.currentPage])
                                    .height(3.dp)
                                    .background(Color(0xFF3b82f6), RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp))
                            )
                        }
                    },
                    divider = { Divider(color = Color(0xFF1e293b)) }
                ) {
                    Tab(
                        selected = pagerState.currentPage == 0,
                        onClick = { coroutineScope.launch { pagerState.animateScrollToPage(0) } },
                        text = { Text("Profile") },
                        icon = { Icon(Icons.Outlined.Person, contentDescription = null, modifier = Modifier.size(18.dp)) },
                        selectedContentColor = Color(0xFF3b82f6),
                        unselectedContentColor = Color(0xFF6b7280)
                    )
                    Tab(
                        selected = pagerState.currentPage == 1,
                        onClick = { coroutineScope.launch { pagerState.animateScrollToPage(1) } },
                        text = { Text("Settings") },
                        icon = { Icon(Icons.Outlined.Settings, contentDescription = null, modifier = Modifier.size(18.dp)) },
                        selectedContentColor = Color(0xFF3b82f6),
                        unselectedContentColor = Color(0xFF6b7280)
                    )
                    if (isAdmin) {
                        Tab(
                            selected = pagerState.currentPage == 2,
                            onClick = { coroutineScope.launch { pagerState.animateScrollToPage(2) } },
                            text = { Text("Admin") },
                            icon = { Icon(Icons.Outlined.AdminPanelSettings, contentDescription = null, modifier = Modifier.size(18.dp)) },
                            selectedContentColor = Color(0xFF3b82f6),
                            unselectedContentColor = Color(0xFF6b7280)
                        )
                    }
                }

                // Pager content
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize()
                ) { page ->
                    when (page) {
                        0 -> ProfileTab(stats, serverUrl)
                        1 -> SettingsTab(
                            user = user,
                            isSaving = isSaving,
                            serverVersion = serverVersion,
                            onEditProfile = { isEditMode = true },
                            onChangePassword = { showPasswordDialog = true },
                            onLogout = onLogout
                        )
                        2 -> if (isAdmin) {
                            AdminTab(
                                viewModel = viewModel,
                                users = users,
                                aiSettings = aiSettings,
                                isScanning = isScanning,
                                scanResult = scanResult
                            )
                        }
                    }
                }
            }
        }
    }

    // Edit Profile Dialog
    if (isEditMode) {
        AlertDialog(
            onDismissRequest = { isEditMode = false },
            title = { Text("Edit Profile", color = Color.White) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    OutlinedTextField(
                        value = displayName,
                        onValueChange = { displayName = it },
                        label = { Text("Display Name") },
                        placeholder = { Text("Your display name") },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = Color(0xFF3b82f6),
                            unfocusedBorderColor = Color(0xFF374151),
                            focusedLabelColor = Color(0xFF3b82f6),
                            unfocusedLabelColor = Color(0xFF9ca3af),
                            cursorColor = Color(0xFF3b82f6)
                        )
                    )

                    OutlinedTextField(
                        value = email,
                        onValueChange = { email = it },
                        label = { Text("Email") },
                        placeholder = { Text("your.email@example.com") },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = Color(0xFF3b82f6),
                            unfocusedBorderColor = Color(0xFF374151),
                            focusedLabelColor = Color(0xFF3b82f6),
                            unfocusedLabelColor = Color(0xFF9ca3af),
                            cursorColor = Color(0xFF3b82f6)
                        )
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.updateProfile(
                            displayName.ifBlank { null },
                            email.ifBlank { null }
                        )
                        isEditMode = false
                    },
                    enabled = !isSaving
                ) {
                    Text("Save", color = Color(0xFF3b82f6))
                }
            },
            dismissButton = {
                TextButton(onClick = { isEditMode = false }) {
                    Text("Cancel", color = Color(0xFF9ca3af))
                }
            },
            containerColor = Color(0xFF1e293b)
        )
    }

    // Change Password Dialog
    if (showPasswordDialog) {
        AlertDialog(
            onDismissRequest = {
                showPasswordDialog = false
                currentPassword = ""
                newPassword = ""
                confirmPassword = ""
            },
            title = { Text("Change Password", color = Color.White) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    OutlinedTextField(
                        value = currentPassword,
                        onValueChange = { currentPassword = it },
                        label = { Text("Current Password") },
                        visualTransformation = if (showCurrentPassword) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { showCurrentPassword = !showCurrentPassword }) {
                                Icon(
                                    imageVector = if (showCurrentPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = "Toggle password visibility",
                                    tint = Color(0xFF9ca3af)
                                )
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = Color(0xFF3b82f6),
                            unfocusedBorderColor = Color(0xFF374151),
                            focusedLabelColor = Color(0xFF3b82f6),
                            unfocusedLabelColor = Color(0xFF9ca3af),
                            cursorColor = Color(0xFF3b82f6)
                        )
                    )

                    OutlinedTextField(
                        value = newPassword,
                        onValueChange = { newPassword = it },
                        label = { Text("New Password") },
                        visualTransformation = if (showNewPassword) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { showNewPassword = !showNewPassword }) {
                                Icon(
                                    imageVector = if (showNewPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = "Toggle password visibility",
                                    tint = Color(0xFF9ca3af)
                                )
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = Color(0xFF3b82f6),
                            unfocusedBorderColor = Color(0xFF374151),
                            focusedLabelColor = Color(0xFF3b82f6),
                            unfocusedLabelColor = Color(0xFF9ca3af),
                            cursorColor = Color(0xFF3b82f6)
                        )
                    )

                    OutlinedTextField(
                        value = confirmPassword,
                        onValueChange = { confirmPassword = it },
                        label = { Text("Confirm New Password") },
                        visualTransformation = PasswordVisualTransformation(),
                        isError = confirmPassword.isNotEmpty() && confirmPassword != newPassword,
                        supportingText = if (confirmPassword.isNotEmpty() && confirmPassword != newPassword) {
                            { Text("Passwords do not match", color = Color(0xFFef4444)) }
                        } else null,
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = Color(0xFF3b82f6),
                            unfocusedBorderColor = Color(0xFF374151),
                            focusedLabelColor = Color(0xFF3b82f6),
                            unfocusedLabelColor = Color(0xFF9ca3af),
                            cursorColor = Color(0xFF3b82f6),
                            errorBorderColor = Color(0xFFef4444),
                            errorLabelColor = Color(0xFFef4444)
                        )
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (newPassword == confirmPassword && currentPassword.isNotBlank() && newPassword.isNotBlank()) {
                            viewModel.updatePassword(currentPassword, newPassword)
                            showPasswordDialog = false
                            currentPassword = ""
                            newPassword = ""
                            confirmPassword = ""
                        }
                    },
                    enabled = currentPassword.isNotBlank() && newPassword.isNotBlank() && newPassword == confirmPassword && !isSaving
                ) {
                    Text("Update Password", color = Color(0xFF3b82f6))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showPasswordDialog = false
                        currentPassword = ""
                        newPassword = ""
                        confirmPassword = ""
                    }
                ) {
                    Text("Cancel", color = Color(0xFF9ca3af))
                }
            },
            containerColor = Color(0xFF1e293b)
        )
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
                    color = Color(0xFF3b82f6)
                )
                StatCard(
                    modifier = Modifier.weight(1f),
                    value = userStats.booksCompleted.toString(),
                    label = "Completed",
                    icon = Icons.Outlined.CheckCircle,
                    color = Color(0xFF10b981)
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
                    color = Color(0xFFf59e0b)
                )
                StatCard(
                    modifier = Modifier.weight(1f),
                    value = "${userStats.currentStreak} days",
                    label = "Streak",
                    icon = Icons.Outlined.LocalFireDepartment,
                    color = Color(0xFFef4444)
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
                                color = Color(0xFF9ca3af),
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
                                color = Color(0xFF9ca3af),
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
                                    .background(Color(0xFF374151))
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
                                            color = Color(0xFF9ca3af),
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
                                        color = Color(0xFF6b7280),
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
                                    tint = Color(0xFF10b981),
                                    modifier = Modifier.size(20.dp)
                                )
                            } else if (item.duration != null && item.duration > 0) {
                                val progress = (item.position.toFloat() / item.duration).coerceIn(0f, 1f)
                                Text(
                                    text = "${(progress * 100).toInt()}%",
                                    color = Color(0xFF3b82f6),
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
                    color = Color(0xFF6b7280),
                    fontSize = 14.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
private fun SettingsTab(
    user: com.sappho.audiobooks.domain.model.User?,
    isSaving: Boolean,
    serverVersion: String?,
    onEditProfile: () -> Unit,
    onChangePassword: () -> Unit,
    onLogout: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        // Account Section
        SectionCard(
            title = "Account",
            icon = Icons.Outlined.Person
        ) {
            SettingsRow(
                icon = Icons.Outlined.Edit,
                title = "Edit Profile",
                subtitle = "Update your display name and email",
                onClick = onEditProfile
            )

            Divider(color = Color(0xFF374151), modifier = Modifier.padding(vertical = 8.dp))

            SettingsRow(
                icon = Icons.Outlined.Lock,
                title = "Change Password",
                subtitle = "Update your account password",
                onClick = onChangePassword
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // About Section
        SectionCard(
            title = "About",
            icon = Icons.Outlined.Info
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "App Version",
                    color = Color.White,
                    fontSize = 15.sp
                )
                Text(
                    text = "1.1.0",
                    color = Color(0xFF9ca3af),
                    fontSize = 14.sp
                )
            }

            Divider(color = Color(0xFF374151), modifier = Modifier.padding(vertical = 4.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Server Version",
                    color = Color.White,
                    fontSize = 15.sp
                )
                Text(
                    text = serverVersion ?: "Unknown",
                    color = Color(0xFF9ca3af),
                    fontSize = 14.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Logout Button
        Button(
            onClick = onLogout,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFF374151),
                contentColor = Color.White
            )
        ) {
            Icon(
                imageVector = Icons.Default.ExitToApp,
                contentDescription = "Logout",
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Logout",
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium
            )
        }

        Spacer(modifier = Modifier.height(32.dp))
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
        color = Color(0xFF1e293b)
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
                color = Color(0xFF9ca3af),
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
        color = Color(0xFF1e293b)
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
                    tint = Color(0xFF3b82f6),
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

@Composable
private fun SettingsRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    iconTint: Color = Color(0xFF9ca3af),
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = iconTint,
            modifier = Modifier.size(22.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = Color.White,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = subtitle,
                color = Color(0xFF6b7280),
                fontSize = 13.sp
            )
        }
        Icon(
            imageVector = Icons.Default.ChevronRight,
            contentDescription = null,
            tint = Color(0xFF6b7280),
            modifier = Modifier.size(20.dp)
        )
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AdminTab(
    viewModel: ProfileViewModel,
    users: List<com.sappho.audiobooks.data.remote.UserInfo>,
    aiSettings: com.sappho.audiobooks.data.remote.AiSettings?,
    isScanning: Boolean,
    scanResult: com.sappho.audiobooks.data.remote.ScanResult?
) {
    var showUserDialog by remember { mutableStateOf(false) }
    var showAiDialog by remember { mutableStateOf(false) }
    var newUsername by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var newEmail by remember { mutableStateOf("") }
    var newIsAdmin by remember { mutableStateOf(false) }
    var actionMessage by remember { mutableStateOf<String?>(null) }

    // AI settings state
    var aiProvider by remember(aiSettings) { mutableStateOf(aiSettings?.aiProvider ?: "openai") }
    var openaiKey by remember(aiSettings) { mutableStateOf("") }
    var geminiKey by remember(aiSettings) { mutableStateOf("") }

    LaunchedEffect(Unit) {
        viewModel.loadUsers()
        viewModel.loadAiSettings()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        // Library Section
        SectionCard(
            title = "Library",
            icon = Icons.Outlined.LibraryBooks
        ) {
            SettingsRow(
                icon = Icons.Outlined.Refresh,
                title = "Scan Library",
                subtitle = "Scan for new audiobooks",
                onClick = { viewModel.scanLibrary(false) }
            )

            if (isScanning) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = Color(0xFF3b82f6),
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text("Scanning library...", color = Color(0xFF9ca3af), fontSize = 14.sp)
                }
            }

            scanResult?.let { result ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF10b981).copy(alpha = 0.1f))
                        .padding(12.dp)
                ) {
                    Text(
                        text = "Scan Complete",
                        color = Color(0xFF10b981),
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Imported: ${result.stats.imported} • Skipped: ${result.stats.skipped} • Errors: ${result.stats.errors}",
                        color = Color(0xFF9ca3af),
                        fontSize = 12.sp
                    )
                }
            }

            Divider(color = Color(0xFF374151), modifier = Modifier.padding(vertical = 8.dp))

            SettingsRow(
                icon = Icons.Outlined.RestartAlt,
                title = "Force Rescan",
                subtitle = "Re-import all audiobooks (preserves progress)",
                iconTint = Color(0xFFf59e0b),
                onClick = { viewModel.scanLibrary(true) }
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // AI Configuration Section
        SectionCard(
            title = "AI Configuration",
            icon = Icons.Outlined.AutoAwesome
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Provider",
                        color = Color.White,
                        fontSize = 15.sp
                    )
                    Text(
                        text = when (aiSettings?.aiProvider) {
                            "gemini" -> "Google Gemini"
                            "openai" -> "OpenAI"
                            else -> "Not configured"
                        },
                        color = Color(0xFF9ca3af),
                        fontSize = 13.sp
                    )
                }
                val hasKey = when (aiSettings?.aiProvider) {
                    "gemini" -> aiSettings.geminiApiKey?.isNotEmpty() == true
                    "openai" -> aiSettings?.openaiApiKey?.isNotEmpty() == true
                    else -> false
                }
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (hasKey) Color(0xFF10b981).copy(alpha = 0.15f)
                    else Color(0xFFf59e0b).copy(alpha = 0.15f)
                ) {
                    Text(
                        text = if (hasKey) "Configured" else "Not Set",
                        fontSize = 12.sp,
                        color = if (hasKey) Color(0xFF10b981) else Color(0xFFf59e0b),
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }

            Divider(color = Color(0xFF374151), modifier = Modifier.padding(vertical = 8.dp))

            SettingsRow(
                icon = Icons.Outlined.Key,
                title = "Configure AI",
                subtitle = "Set up OpenAI or Gemini API key",
                onClick = { showAiDialog = true }
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // User Management Section
        SectionCard(
            title = "Users",
            icon = Icons.Outlined.People
        ) {
            users.forEach { user ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF3b82f6)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = user.username.first().uppercaseChar().toString(),
                                color = Color.White,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = user.username,
                                color = Color.White,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = user.email ?: "No email",
                                color = Color(0xFF6b7280),
                                fontSize = 12.sp
                            )
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (user.isAdmin == 1) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = Color(0xFF10b981).copy(alpha = 0.15f)
                            ) {
                                Text(
                                    text = "Admin",
                                    fontSize = 11.sp,
                                    color = Color(0xFF10b981),
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                )
                            }
                        }
                        IconButton(
                            onClick = {
                                viewModel.deleteUser(user.id) { success, message ->
                                    actionMessage = message
                                }
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Delete,
                                contentDescription = "Delete",
                                tint = Color(0xFFef4444),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }

            Divider(color = Color(0xFF374151), modifier = Modifier.padding(vertical = 8.dp))

            Button(
                onClick = { showUserDialog = true },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF3b82f6)
                )
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Add User")
            }
        }

        Spacer(modifier = Modifier.height(32.dp))
    }

    // Create User Dialog
    if (showUserDialog) {
        AlertDialog(
            onDismissRequest = { showUserDialog = false },
            title = { Text("Create User", color = Color.White) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = newUsername,
                        onValueChange = { newUsername = it },
                        label = { Text("Username") },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = Color(0xFF3b82f6),
                            unfocusedBorderColor = Color(0xFF374151),
                            focusedLabelColor = Color(0xFF3b82f6),
                            unfocusedLabelColor = Color(0xFF9ca3af),
                            cursorColor = Color(0xFF3b82f6)
                        )
                    )
                    OutlinedTextField(
                        value = newPassword,
                        onValueChange = { newPassword = it },
                        label = { Text("Password") },
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = Color(0xFF3b82f6),
                            unfocusedBorderColor = Color(0xFF374151),
                            focusedLabelColor = Color(0xFF3b82f6),
                            unfocusedLabelColor = Color(0xFF9ca3af),
                            cursorColor = Color(0xFF3b82f6)
                        )
                    )
                    OutlinedTextField(
                        value = newEmail,
                        onValueChange = { newEmail = it },
                        label = { Text("Email (optional)") },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = Color(0xFF3b82f6),
                            unfocusedBorderColor = Color(0xFF374151),
                            focusedLabelColor = Color(0xFF3b82f6),
                            unfocusedLabelColor = Color(0xFF9ca3af),
                            cursorColor = Color(0xFF3b82f6)
                        )
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable { newIsAdmin = !newIsAdmin }
                    ) {
                        Checkbox(
                            checked = newIsAdmin,
                            onCheckedChange = { newIsAdmin = it },
                            colors = CheckboxDefaults.colors(
                                checkedColor = Color(0xFF3b82f6),
                                uncheckedColor = Color(0xFF6b7280)
                            )
                        )
                        Text("Administrator", color = Color.White)
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.createUser(
                            newUsername,
                            newPassword,
                            newEmail.ifBlank { null },
                            newIsAdmin
                        ) { success, message ->
                            actionMessage = message
                            if (success) {
                                showUserDialog = false
                                newUsername = ""
                                newPassword = ""
                                newEmail = ""
                                newIsAdmin = false
                            }
                        }
                    },
                    enabled = newUsername.isNotBlank() && newPassword.isNotBlank()
                ) {
                    Text("Create", color = Color(0xFF3b82f6))
                }
            },
            dismissButton = {
                TextButton(onClick = { showUserDialog = false }) {
                    Text("Cancel", color = Color(0xFF9ca3af))
                }
            },
            containerColor = Color(0xFF1e293b)
        )
    }

    // AI Configuration Dialog
    if (showAiDialog) {
        AlertDialog(
            onDismissRequest = { showAiDialog = false },
            title = { Text("AI Configuration", color = Color.White) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text(
                        text = "Provider",
                        color = Color(0xFF9ca3af),
                        fontSize = 12.sp
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = aiProvider == "openai",
                            onClick = { aiProvider = "openai" },
                            label = { Text("OpenAI") },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Color(0xFF3b82f6),
                                selectedLabelColor = Color.White,
                                containerColor = Color(0xFF374151),
                                labelColor = Color(0xFF9ca3af)
                            )
                        )
                        FilterChip(
                            selected = aiProvider == "gemini",
                            onClick = { aiProvider = "gemini" },
                            label = { Text("Gemini (Free)") },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Color(0xFF3b82f6),
                                selectedLabelColor = Color.White,
                                containerColor = Color(0xFF374151),
                                labelColor = Color(0xFF9ca3af)
                            )
                        )
                    }

                    if (aiProvider == "openai") {
                        OutlinedTextField(
                            value = openaiKey,
                            onValueChange = { openaiKey = it },
                            label = { Text("OpenAI API Key") },
                            placeholder = { Text("sk-...") },
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedBorderColor = Color(0xFF3b82f6),
                                unfocusedBorderColor = Color(0xFF374151),
                                focusedLabelColor = Color(0xFF3b82f6),
                                unfocusedLabelColor = Color(0xFF9ca3af),
                                cursorColor = Color(0xFF3b82f6)
                            )
                        )
                        Text(
                            text = "Get your key at platform.openai.com/api-keys",
                            color = Color(0xFF6b7280),
                            fontSize = 11.sp
                        )
                    } else {
                        OutlinedTextField(
                            value = geminiKey,
                            onValueChange = { geminiKey = it },
                            label = { Text("Gemini API Key") },
                            placeholder = { Text("AIza...") },
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedBorderColor = Color(0xFF3b82f6),
                                unfocusedBorderColor = Color(0xFF374151),
                                focusedLabelColor = Color(0xFF3b82f6),
                                unfocusedLabelColor = Color(0xFF9ca3af),
                                cursorColor = Color(0xFF3b82f6)
                            )
                        )
                        Text(
                            text = "Get your free key at aistudio.google.com/app/apikey",
                            color = Color(0xFF6b7280),
                            fontSize = 11.sp
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val settings = com.sappho.audiobooks.data.remote.AiSettingsUpdate(
                            aiProvider = aiProvider,
                            openaiApiKey = openaiKey.ifBlank { null },
                            geminiApiKey = geminiKey.ifBlank { null }
                        )
                        viewModel.updateAiSettings(settings) { success, message ->
                            actionMessage = message
                            if (success) {
                                showAiDialog = false
                            }
                        }
                    }
                ) {
                    Text("Save", color = Color(0xFF3b82f6))
                }
            },
            dismissButton = {
                TextButton(onClick = { showAiDialog = false }) {
                    Text("Cancel", color = Color(0xFF9ca3af))
                }
            },
            containerColor = Color(0xFF1e293b)
        )
    }
}
