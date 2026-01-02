package com.sappho.audiobooks.presentation.profile

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.sappho.audiobooks.domain.model.User
import com.sappho.audiobooks.presentation.theme.*
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun EditProfileDialog(
    user: User?,
    avatarUri: Uri?,
    serverUrl: String?,
    onDismiss: () -> Unit,
    onPickImage: () -> Unit,
    onRemoveAvatar: () -> Unit,
    onSave: (displayName: String?, email: String?) -> Unit,
    onUpdatePassword: (currentPassword: String, newPassword: String) -> Unit
) {
    var displayName by remember { mutableStateOf(user?.displayName ?: "") }
    var email by remember { mutableStateOf(user?.email ?: "") }

    // Password fields
    var currentPassword by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var showCurrentPassword by remember { mutableStateOf(false) }
    var showNewPassword by remember { mutableStateOf(false) }
    var passwordError by remember { mutableStateOf<String?>(null) }

    val hasAvatar = user?.avatar != null || avatarUri != null

    // Format member since date
    val memberSinceText = user?.createdAt?.let { createdAt ->
        try {
            val inputFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
            val outputFormat = SimpleDateFormat("MMMM d, yyyy", Locale.getDefault())
            val parsed = inputFormat.parse(createdAt.split(".")[0])
            outputFormat.format(parsed ?: Date())
        } catch (e: Exception) { null }
    }

    val textFieldColors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = SapphoInfo,
        unfocusedBorderColor = SapphoIconDefault,
        focusedLabelColor = SapphoInfo,
        unfocusedLabelColor = SapphoIconDefault,
        cursorColor = SapphoInfo,
        focusedTextColor = SapphoText,
        unfocusedTextColor = SapphoText
    )

    // Avatar gradient
    val avatarGradient = LibraryGradients.forAvatar(user?.username ?: "U")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit Profile", color = SapphoText) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(Spacing.M)
            ) {
                // Avatar section - tappable to upload
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box(
                        modifier = Modifier
                            .size(80.dp)
                            .clip(CircleShape)
                            .background(Brush.verticalGradient(avatarGradient))
                            .clickable { onPickImage() },
                        contentAlignment = Alignment.Center
                    ) {
                        when {
                            avatarUri != null -> {
                                AsyncImage(
                                    model = avatarUri,
                                    contentDescription = "Avatar",
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                            }
                            user?.avatar != null && serverUrl != null -> {
                                val avatarUrl = "$serverUrl/api/profile/avatar"
                                AsyncImage(
                                    model = avatarUrl,
                                    contentDescription = "Avatar",
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                            }
                            else -> {
                                Icon(
                                    imageVector = Icons.Outlined.CameraAlt,
                                    contentDescription = "Upload photo",
                                    tint = SapphoText,
                                    modifier = Modifier.size(IconSize.Large)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(Spacing.XXS))

                    Text(
                        text = "Tap to change photo",
                        style = MaterialTheme.typography.labelSmall,
                        color = SapphoTextMuted
                    )

                    if (hasAvatar) {
                        TextButton(
                            onClick = onRemoveAvatar,
                            contentPadding = PaddingValues(horizontal = Spacing.XS, vertical = 0.dp)
                        ) {
                            Text("Remove photo", color = SapphoError, style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }

                // Member since
                memberSinceText?.let { date ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = "Member since $date",
                            style = MaterialTheme.typography.labelMedium,
                            color = SapphoTextMuted
                        )
                    }
                }

                HorizontalDivider(color = SapphoProgressTrack)

                // Display Name
                OutlinedTextField(
                    value = displayName,
                    onValueChange = { displayName = it },
                    label = { Text("Display Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = textFieldColors
                )

                // Email
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text("Email") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = textFieldColors
                )

                HorizontalDivider(color = SapphoProgressTrack)

                // Password section
                Text(
                    text = "Change Password",
                    style = MaterialTheme.typography.titleSmall,
                    color = SapphoText
                )

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
                                contentDescription = if (showCurrentPassword) "Hide password" else "Show password",
                                tint = SapphoIconDefault
                            )
                        }
                    },
                    colors = textFieldColors
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
                                contentDescription = if (showNewPassword) "Hide password" else "Show password",
                                tint = SapphoIconDefault
                            )
                        }
                    },
                    colors = textFieldColors
                )

                OutlinedTextField(
                    value = confirmPassword,
                    onValueChange = {
                        confirmPassword = it
                        passwordError = null
                    },
                    label = { Text("Confirm New Password") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    visualTransformation = if (showNewPassword) VisualTransformation.None else PasswordVisualTransformation(),
                    isError = passwordError != null,
                    supportingText = passwordError?.let { { Text(it, color = SapphoError) } },
                    colors = textFieldColors
                )

                // Update Password button
                if (currentPassword.isNotEmpty() || newPassword.isNotEmpty() || confirmPassword.isNotEmpty()) {
                    Button(
                        onClick = {
                            when {
                                currentPassword.isEmpty() -> passwordError = "Enter current password"
                                newPassword.isEmpty() -> passwordError = "Enter new password"
                                newPassword.length < 6 -> passwordError = "Password must be at least 6 characters"
                                newPassword != confirmPassword -> passwordError = "Passwords don't match"
                                else -> {
                                    onUpdatePassword(currentPassword, newPassword)
                                    currentPassword = ""
                                    newPassword = ""
                                    confirmPassword = ""
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = SapphoInfo),
                        shape = RoundedCornerShape(Spacing.XS)
                    ) {
                        Text("Update Password")
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onSave(
                        displayName.ifBlank { null },
                        email.ifBlank { null }
                    )
                }
            ) {
                Text("Save", color = SapphoInfo)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = SapphoIconDefault)
            }
        },
        containerColor = SapphoSurfaceLight
    )
}
