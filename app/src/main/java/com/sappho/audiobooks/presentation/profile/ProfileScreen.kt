package com.sappho.audiobooks.presentation.profile

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest
import com.sappho.audiobooks.BuildConfig
import com.sappho.audiobooks.presentation.theme.*
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    onLogout: () -> Unit = {},
    onSettingsClick: () -> Unit = {},
    onAvatarChanged: () -> Unit = {},
    onBookClick: (Int) -> Unit = {},
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
    val snackbarHostState = remember { SnackbarHostState() }

    // Form state
    var displayName by remember(user) { mutableStateOf(user?.displayName ?: "") }
    var email by remember(user) { mutableStateOf(user?.email ?: "") }

    // Password state
    var showPasswordSection by remember { mutableStateOf(false) }
    var currentPassword by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var showCurrentPassword by remember { mutableStateOf(false) }
    var showNewPassword by remember { mutableStateOf(false) }
    var passwordError by remember { mutableStateOf<String?>(null) }

    // Avatar picker
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            viewModel.setAvatarUri(it)
            try {
                val inputStream = context.contentResolver.openInputStream(it)
                val originalBitmap = android.graphics.BitmapFactory.decodeStream(inputStream)
                inputStream?.close()

                if (originalBitmap != null) {
                    val maxSize = 800
                    val scale = minOf(
                        maxSize.toFloat() / originalBitmap.width,
                        maxSize.toFloat() / originalBitmap.height,
                        1f
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

                    val tempFile = File(context.cacheDir, "avatar_temp.jpg")
                    tempFile.outputStream().use { output ->
                        scaledBitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 85, output)
                    }

                    if (scaledBitmap != originalBitmap) {
                        scaledBitmap.recycle()
                    }
                    originalBitmap.recycle()

                    viewModel.updateProfileWithAvatar(null, null, tempFile, "image/jpeg")
                }
            } catch (e: OutOfMemoryError) {
                coroutineScope.launch {
                    snackbarHostState.showSnackbar("Image too large. Please choose a smaller image.")
                }
            } catch (e: Exception) {
                coroutineScope.launch {
                    snackbarHostState.showSnackbar("Failed to process image. Please try again.")
                }
            }
        }
    }

    LaunchedEffect(saveMessage) {
        saveMessage?.let { message ->
            snackbarHostState.showSnackbar(message)
            viewModel.clearMessage()
        }
    }

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
                    .padding(horizontal = Spacing.M)
            ) {
                Spacer(modifier = Modifier.height(Spacing.L))

                // ===== HEADER WITH AVATAR =====
                val avatarGradient = LibraryGradients.forAvatar(user?.username ?: "U")
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Avatar (100dp, clickable with "Edit" overlay)
                    Box(
                        modifier = Modifier
                            .size(100.dp)
                            .clip(CircleShape)
                            .clickable { imagePickerLauncher.launch("image/*") },
                        contentAlignment = Alignment.Center
                    ) {
                        // Avatar background/image
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
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
                                        style = MaterialTheme.typography.headlineLarge
                                    )
                                }
                            }
                        }

                        // "Edit" overlay at bottom
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .fillMaxWidth()
                                .background(SapphoBackground.copy(alpha = 0.7f))
                                .padding(vertical = 4.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Edit",
                                color = SapphoText,
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }

                    // Remove photo link
                    if (user?.avatar != null || avatarUri != null) {
                        TextButton(
                            onClick = {
                                viewModel.setAvatarUri(null)
                                if (user?.avatar != null) {
                                    viewModel.deleteAvatar()
                                }
                            },
                            contentPadding = PaddingValues(horizontal = Spacing.XS, vertical = 0.dp)
                        ) {
                            Text(
                                "Remove photo",
                                color = SapphoTextMuted,
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    } else {
                        Spacer(modifier = Modifier.height(Spacing.XS))
                    }

                    // Name
                    Text(
                        text = user?.displayName ?: user?.username ?: "User",
                        style = MaterialTheme.typography.headlineSmall,
                        color = SapphoText
                    )

                    // Admin/Member since date
                    val memberSinceText = user?.createdAt?.let { createdAt ->
                        try {
                            val inputFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
                            val outputFormat = SimpleDateFormat("MMM yyyy", Locale.getDefault())
                            val parsed = inputFormat.parse(createdAt.split(".")[0])
                            outputFormat.format(parsed ?: Date())
                        } catch (e: Exception) { null }
                    }
                    Text(
                        text = "${if (user?.isAdmin == 1) "Admin" else "Member"} since ${memberSinceText ?: ""}",
                        style = MaterialTheme.typography.bodySmall,
                        color = SapphoTextMuted
                    )
                }

                Spacer(modifier = Modifier.height(Spacing.L))

                // ===== STATS ROW =====
                stats?.let { userStats ->
                    val listenTime = formatListenTime(userStats.totalListenTime)

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = Spacing.M),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Listen time
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Row(verticalAlignment = Alignment.Bottom) {
                                Text(
                                    text = listenTime.first.toString(),
                                    style = MaterialTheme.typography.headlineSmall,
                                    color = SapphoText
                                )
                                Text(
                                    text = "h",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = SapphoTextMuted,
                                    modifier = Modifier.padding(start = 2.dp, bottom = 4.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = listenTime.second.toString(),
                                    style = MaterialTheme.typography.headlineSmall,
                                    color = SapphoText
                                )
                                Text(
                                    text = "m",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = SapphoTextMuted,
                                    modifier = Modifier.padding(start = 2.dp, bottom = 4.dp)
                                )
                            }
                            Text(
                                text = "listened",
                                style = MaterialTheme.typography.labelSmall,
                                color = SapphoTextMuted
                            )
                        }

                        // Divider
                        Box(
                            modifier = Modifier
                                .padding(horizontal = Spacing.L)
                                .width(1.dp)
                                .height(40.dp)
                                .background(SapphoProgressTrack)
                        )

                        // Finished
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = userStats.booksCompleted.toString(),
                                style = MaterialTheme.typography.headlineSmall,
                                color = SapphoText
                            )
                            Text(
                                text = "finished",
                                style = MaterialTheme.typography.labelSmall,
                                color = SapphoTextMuted
                            )
                        }

                        // Divider
                        Box(
                            modifier = Modifier
                                .padding(horizontal = Spacing.L)
                                .width(1.dp)
                                .height(40.dp)
                                .background(SapphoProgressTrack)
                        )

                        // In progress
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = userStats.currentlyListening.toString(),
                                style = MaterialTheme.typography.headlineSmall,
                                color = SapphoText
                            )
                            Text(
                                text = "in progress",
                                style = MaterialTheme.typography.labelSmall,
                                color = SapphoTextMuted
                            )
                        }
                    }

                    HorizontalDivider(color = SapphoProgressTrack)

                    // ===== RECENT BOOKS =====
                    if (userStats.recentActivity.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(Spacing.L))

                        SectionTitle("Recent")

                        Spacer(modifier = Modifier.height(Spacing.S))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(Spacing.S)
                        ) {
                            userStats.recentActivity.take(4).forEach { book ->
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .aspectRatio(1f)
                                        .clip(RoundedCornerShape(Spacing.XS))
                                        .background(SapphoSurfaceLight)
                                        .clickable { onBookClick(book.id) }
                                ) {
                                    if (book.coverImage != null && serverUrl != null) {
                                        AsyncImage(
                                            model = com.sappho.audiobooks.util.buildCoverUrl(serverUrl!!, book.id, com.sappho.audiobooks.util.COVER_WIDTH_THUMBNAIL),
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
                                                text = book.title.take(1).uppercase(),
                                                color = SapphoTextMuted,
                                                style = MaterialTheme.typography.titleLarge
                                            )
                                        }
                                    }

                                    // Progress bar at bottom
                                    if (book.duration != null && book.duration > 0) {
                                        val progress = (book.position.toFloat() / book.duration).coerceIn(0f, 1f)
                                        Box(
                                            modifier = Modifier
                                                .align(Alignment.BottomStart)
                                                .fillMaxWidth(progress)
                                                .height(3.dp)
                                                .background(SapphoPrimary)
                                        )
                                    }
                                }
                            }
                            // Fill remaining slots if less than 4 books
                            repeat(4 - userStats.recentActivity.take(4).size) {
                                Box(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(Spacing.XL))

                // ===== ACCOUNT SECTION =====
                SectionTitle("Account")

                Spacer(modifier = Modifier.height(Spacing.S))

                val textFieldColors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = SapphoPrimary,
                    unfocusedBorderColor = SapphoProgressTrack,
                    focusedLabelColor = SapphoPrimary,
                    unfocusedLabelColor = SapphoTextMuted,
                    cursorColor = SapphoPrimary,
                    focusedTextColor = SapphoText,
                    unfocusedTextColor = SapphoText
                )

                OutlinedTextField(
                    value = displayName,
                    onValueChange = { displayName = it },
                    label = { Text("Display Name") },
                    placeholder = { Text(user?.username ?: "") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = textFieldColors,
                    shape = RoundedCornerShape(Spacing.XS)
                )

                Spacer(modifier = Modifier.height(Spacing.S))

                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text("Email") },
                    placeholder = { Text("your@email.com") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = textFieldColors,
                    shape = RoundedCornerShape(Spacing.XS)
                )

                Spacer(modifier = Modifier.height(Spacing.M))

                Button(
                    onClick = {
                        viewModel.updateProfileWithAvatar(
                            displayName.ifBlank { null },
                            email.ifBlank { null },
                            null
                        )
                    },
                    enabled = !isSaving,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = SapphoPrimary),
                    shape = RoundedCornerShape(Spacing.XS)
                ) {
                    Text(if (isSaving) "Saving..." else "Save Changes")
                }

                Spacer(modifier = Modifier.height(Spacing.XL))

                // ===== SECURITY SECTION =====
                SectionTitle("Security")

                Spacer(modifier = Modifier.height(Spacing.S))

                // Password change
                if (!showPasswordSection) {
                    OutlinedButton(
                        onClick = { showPasswordSection = true },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = SapphoText),
                        shape = RoundedCornerShape(Spacing.XS)
                    ) {
                        Text("Change Password")
                    }
                } else {
                    AnimatedVisibility(
                        visible = showPasswordSection,
                        enter = expandVertically(),
                        exit = shrinkVertically()
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(Spacing.S)
                        ) {
                            OutlinedTextField(
                                value = currentPassword,
                                onValueChange = {
                                    currentPassword = it
                                    passwordError = null
                                },
                                label = { Text("Current Password") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                                visualTransformation = if (showCurrentPassword) VisualTransformation.None else PasswordVisualTransformation(),
                                trailingIcon = {
                                    IconButton(onClick = { showCurrentPassword = !showCurrentPassword }) {
                                        Icon(
                                            imageVector = if (showCurrentPassword) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                                            contentDescription = if (showCurrentPassword) "Hide" else "Show",
                                            tint = SapphoIconDefault
                                        )
                                    }
                                },
                                colors = textFieldColors,
                                shape = RoundedCornerShape(Spacing.XS)
                            )

                            OutlinedTextField(
                                value = newPassword,
                                onValueChange = {
                                    newPassword = it
                                    passwordError = null
                                },
                                label = { Text("New Password") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                                visualTransformation = if (showNewPassword) VisualTransformation.None else PasswordVisualTransformation(),
                                trailingIcon = {
                                    IconButton(onClick = { showNewPassword = !showNewPassword }) {
                                        Icon(
                                            imageVector = if (showNewPassword) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                                            contentDescription = if (showNewPassword) "Hide" else "Show",
                                            tint = SapphoIconDefault
                                        )
                                    }
                                },
                                colors = textFieldColors,
                                shape = RoundedCornerShape(Spacing.XS)
                            )

                            OutlinedTextField(
                                value = confirmPassword,
                                onValueChange = {
                                    confirmPassword = it
                                    passwordError = null
                                },
                                label = { Text("Confirm Password") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                                visualTransformation = if (showNewPassword) VisualTransformation.None else PasswordVisualTransformation(),
                                isError = passwordError != null,
                                supportingText = passwordError?.let { { Text(it, color = SapphoError) } },
                                colors = textFieldColors,
                                shape = RoundedCornerShape(Spacing.XS)
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(Spacing.S)
                            ) {
                                OutlinedButton(
                                    onClick = {
                                        showPasswordSection = false
                                        currentPassword = ""
                                        newPassword = ""
                                        confirmPassword = ""
                                        passwordError = null
                                    },
                                    modifier = Modifier.weight(1f),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = SapphoTextMuted),
                                    shape = RoundedCornerShape(Spacing.XS)
                                ) {
                                    Text("Cancel")
                                }
                                Button(
                                    onClick = {
                                        when {
                                            currentPassword.isEmpty() -> passwordError = "Enter current password"
                                            newPassword.isEmpty() -> passwordError = "Enter new password"
                                            newPassword.length < 6 -> passwordError = "Password must be at least 6 characters"
                                            newPassword != confirmPassword -> passwordError = "Passwords don't match"
                                            else -> {
                                                viewModel.updatePassword(currentPassword, newPassword)
                                                currentPassword = ""
                                                newPassword = ""
                                                confirmPassword = ""
                                                showPasswordSection = false
                                            }
                                        }
                                    },
                                    modifier = Modifier.weight(1f),
                                    colors = ButtonDefaults.buttonColors(containerColor = SapphoPrimary),
                                    shape = RoundedCornerShape(Spacing.XS)
                                ) {
                                    Text("Change Password")
                                }
                            }
                        }
                    }
                }

                // TODO: MFA toggle would go here when backend supports it

                Spacer(modifier = Modifier.height(Spacing.XL))

                // ===== PLAYER SECTION =====
                SectionTitle("Player")

                Spacer(modifier = Modifier.height(Spacing.S))

                // Settings button (links to Settings screen for playback preferences)
                OutlinedButton(
                    onClick = onSettingsClick,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = SapphoText),
                    shape = RoundedCornerShape(Spacing.XS)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Settings,
                        contentDescription = null,
                        modifier = Modifier.size(IconSize.Small)
                    )
                    Spacer(modifier = Modifier.width(Spacing.XS))
                    Text("Playback Settings")
                }

                Spacer(modifier = Modifier.height(Spacing.XL))

                // ===== ABOUT SECTION =====
                SectionTitle("About")

                Spacer(modifier = Modifier.height(Spacing.S))

                // Version info
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(Spacing.XS),
                    color = SapphoSurfaceLight
                ) {
                    Column(modifier = Modifier.padding(Spacing.M)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "App Version",
                                style = MaterialTheme.typography.bodyMedium,
                                color = SapphoTextMuted
                            )
                            Text(
                                text = BuildConfig.VERSION_NAME,
                                style = MaterialTheme.typography.bodyMedium,
                                color = SapphoText
                            )
                        }
                        serverVersion?.let { version ->
                            Spacer(modifier = Modifier.height(Spacing.XS))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "Server Version",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = SapphoTextMuted
                                )
                                Text(
                                    text = version,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = SapphoText
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(Spacing.M))

                // Logout button
                Button(
                    onClick = onLogout,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = SapphoError.copy(alpha = 0.15f),
                        contentColor = SapphoError
                    ),
                    shape = RoundedCornerShape(Spacing.XS)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Logout,
                        contentDescription = null,
                        modifier = Modifier.size(IconSize.Small)
                    )
                    Spacer(modifier = Modifier.width(Spacing.XS))
                    Text("Logout")
                }

                Spacer(modifier = Modifier.height(Spacing.XL))
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        color = SapphoTextMuted,
        letterSpacing = 0.5.sp
    )
}

/**
 * Format listen time to hours and minutes
 * Returns Pair(hours, minutes)
 */
private fun formatListenTime(seconds: Long): Pair<Int, Int> {
    val totalMinutes = (seconds / 60).toInt()
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return Pair(hours, minutes)
}
