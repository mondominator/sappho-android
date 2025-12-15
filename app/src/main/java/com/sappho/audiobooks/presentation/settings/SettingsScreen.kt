package com.sappho.audiobooks.presentation.settings

import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.sappho.audiobooks.presentation.theme.*
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.sappho.audiobooks.BuildConfig
import com.sappho.audiobooks.data.repository.UserPreferencesRepository

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBackClick: () -> Unit,
    onLogout: () -> Unit,
    viewModel: UserSettingsViewModel = hiltViewModel()
) {
    val user by viewModel.user.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val isSaving by viewModel.isSaving.collectAsState()
    val message by viewModel.message.collectAsState()
    val serverVersion by viewModel.serverVersion.collectAsState()
    val skipForward by viewModel.userPreferences.skipForwardSeconds.collectAsState()
    val skipBackward by viewModel.userPreferences.skipBackwardSeconds.collectAsState()

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

    // Snackbar
    val snackbarHostState = remember { SnackbarHostState() }

    // Handle system back button
    BackHandler { onBackClick() }

    LaunchedEffect(message) {
        message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearMessage()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = SapphoBackground,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Settings",
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = SapphoSurface
                )
            )
        }
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
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp)
            ) {
                // Playback Section
                SectionCard(
                    title = "Playback",
                    icon = Icons.Outlined.PlayCircle
                ) {
                    // Skip Forward Setting
                    SkipIntervalSelector(
                        label = "Skip Forward",
                        currentValue = skipForward,
                        options = UserPreferencesRepository.SKIP_FORWARD_OPTIONS,
                        onValueChange = { viewModel.userPreferences.setSkipForwardSeconds(it) }
                    )

                    HorizontalDivider(color = SapphoProgressTrack, modifier = Modifier.padding(vertical = 8.dp))

                    // Skip Backward Setting
                    SkipIntervalSelector(
                        label = "Skip Back",
                        currentValue = skipBackward,
                        options = UserPreferencesRepository.SKIP_BACKWARD_OPTIONS,
                        onValueChange = { viewModel.userPreferences.setSkipBackwardSeconds(it) }
                    )

                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Changes take effect on next playback start",
                        color = SapphoTextMuted,
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Account Section
                SectionCard(
                    title = "Account",
                    icon = Icons.Outlined.Person
                ) {
                    SettingsRow(
                        icon = Icons.Outlined.Edit,
                        title = "Edit Profile",
                        subtitle = "Update your display name and email",
                        onClick = { isEditMode = true }
                    )

                    HorizontalDivider(color = SapphoProgressTrack, modifier = Modifier.padding(vertical = 8.dp))

                    SettingsRow(
                        icon = Icons.Outlined.Lock,
                        title = "Change Password",
                        subtitle = "Update your account password",
                        onClick = { showPasswordDialog = true }
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
                            style = MaterialTheme.typography.titleSmall
                        )
                        Text(
                            text = BuildConfig.VERSION_NAME,
                            color = SapphoIconDefault,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }

                    HorizontalDivider(color = SapphoProgressTrack, modifier = Modifier.padding(vertical = 4.dp))

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
                            style = MaterialTheme.typography.titleSmall
                        )
                        Text(
                            text = serverVersion ?: "Unknown",
                            color = SapphoIconDefault,
                            style = MaterialTheme.typography.bodyMedium
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
                        containerColor = SapphoProgressTrack,
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
                        style = MaterialTheme.typography.titleMedium
                    )
                }

                Spacer(modifier = Modifier.height(32.dp))
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
                            focusedBorderColor = SapphoInfo,
                            unfocusedBorderColor = SapphoProgressTrack,
                            focusedLabelColor = SapphoInfo,
                            unfocusedLabelColor = SapphoIconDefault,
                            cursorColor = SapphoInfo
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
                            focusedBorderColor = SapphoInfo,
                            unfocusedBorderColor = SapphoProgressTrack,
                            focusedLabelColor = SapphoInfo,
                            unfocusedLabelColor = SapphoIconDefault,
                            cursorColor = SapphoInfo
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
                    Text("Save", color = SapphoInfo)
                }
            },
            dismissButton = {
                TextButton(onClick = { isEditMode = false }) {
                    Text("Cancel", color = SapphoIconDefault)
                }
            },
            containerColor = SapphoSurfaceLight
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
                                    tint = SapphoIconDefault
                                )
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = SapphoInfo,
                            unfocusedBorderColor = SapphoProgressTrack,
                            focusedLabelColor = SapphoInfo,
                            unfocusedLabelColor = SapphoIconDefault,
                            cursorColor = SapphoInfo
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
                                    tint = SapphoIconDefault
                                )
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = SapphoInfo,
                            unfocusedBorderColor = SapphoProgressTrack,
                            focusedLabelColor = SapphoInfo,
                            unfocusedLabelColor = SapphoIconDefault,
                            cursorColor = SapphoInfo
                        )
                    )

                    OutlinedTextField(
                        value = confirmPassword,
                        onValueChange = { confirmPassword = it },
                        label = { Text("Confirm New Password") },
                        visualTransformation = PasswordVisualTransformation(),
                        isError = confirmPassword.isNotEmpty() && confirmPassword != newPassword,
                        supportingText = if (confirmPassword.isNotEmpty() && confirmPassword != newPassword) {
                            { Text("Passwords do not match", color = SapphoError) }
                        } else null,
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = SapphoInfo,
                            unfocusedBorderColor = SapphoProgressTrack,
                            focusedLabelColor = SapphoInfo,
                            unfocusedLabelColor = SapphoIconDefault,
                            cursorColor = SapphoInfo,
                            errorBorderColor = SapphoError,
                            errorLabelColor = SapphoError
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
                    Text("Update Password", color = SapphoInfo)
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
                    Text("Cancel", color = SapphoIconDefault)
                }
            },
            containerColor = SapphoSurfaceLight
        )
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
                    style = MaterialTheme.typography.titleMedium,
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
    iconTint: Color = SapphoIconDefault,
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
                style = MaterialTheme.typography.titleSmall
            )
            Text(
                text = subtitle,
                color = SapphoTextMuted,
                style = MaterialTheme.typography.bodySmall
            )
        }
        Icon(
            imageVector = Icons.Default.ChevronRight,
            contentDescription = null,
            tint = SapphoTextMuted,
            modifier = Modifier.size(20.dp)
        )
    }
}

@Composable
private fun SkipIntervalSelector(
    label: String,
    currentValue: Int,
    options: List<Int>,
    onValueChange: (Int) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                color = Color.White,
                style = MaterialTheme.typography.titleSmall
            )
            Text(
                text = "${currentValue}s",
                color = SapphoInfo,
                style = MaterialTheme.typography.labelLarge
            )
        }
        Spacer(modifier = Modifier.height(10.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            options.forEach { seconds ->
                val isSelected = seconds == currentValue
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .clickable { onValueChange(seconds) },
                    shape = RoundedCornerShape(8.dp),
                    color = if (isSelected) SapphoInfo else SapphoProgressTrack
                ) {
                    Text(
                        text = "${seconds}s",
                        color = if (isSelected) Color.White else SapphoIconDefault,
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(vertical = 10.dp, horizontal = 4.dp)
                    )
                }
            }
        }
    }
}
