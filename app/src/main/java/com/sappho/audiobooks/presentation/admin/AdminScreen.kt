package com.sappho.audiobooks.presentation.admin

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sappho.audiobooks.data.remote.UserInfo
import com.sappho.audiobooks.presentation.theme.SapphoBackground
import com.sappho.audiobooks.presentation.theme.SapphoError
import com.sappho.audiobooks.presentation.theme.SapphoIconDefault
import com.sappho.audiobooks.presentation.theme.SapphoInfo
import com.sappho.audiobooks.presentation.theme.SapphoPrimary
import com.sappho.audiobooks.presentation.theme.SapphoProgressTrack
import com.sappho.audiobooks.presentation.theme.SapphoSuccess
import com.sappho.audiobooks.presentation.theme.SapphoSurfaceLight
import com.sappho.audiobooks.presentation.theme.SapphoTextMuted
import com.sappho.audiobooks.presentation.theme.SapphoWarning

/**
 * Admin screen, matching the iOS AdminView: a "Library" section (scan / force
 * rescan) and a "Users" section (add, list, delete). Only reachable from the
 * admin-gated entry in the profile menu.
 */
@Composable
fun AdminScreen(
    onBack: () -> Unit,
    viewModel: AdminViewModel = hiltViewModel()
) {
    val users by viewModel.users.collectAsStateWithLifecycle()
    val isLoadingUsers by viewModel.isLoadingUsers.collectAsStateWithLifecycle()
    val isScanning by viewModel.isScanning.collectAsStateWithLifecycle()
    val scanMessage by viewModel.scanMessage.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    var showCreateUser by remember { mutableStateOf(false) }
    var selectedUser by remember { mutableStateOf<UserInfo?>(null) }
    var userToDelete by remember { mutableStateOf<UserInfo?>(null) }

    LaunchedEffect(message) {
        message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearMessage()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = SapphoBackground
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item { AdminHeader(onBack = onBack) }

            item {
                AdminSectionCard(title = "Library") {
                    AdminActionRow(
                        text = "Scan for New Books",
                        icon = Icons.Default.Refresh,
                        tint = SapphoPrimary,
                        busy = isScanning,
                        onClick = viewModel::scanLibrary
                    )
                    AdminActionRow(
                        text = "Force Full Rescan",
                        icon = Icons.Default.Sync,
                        tint = SapphoWarning,
                        busy = isScanning,
                        onClick = viewModel::forceRescan
                    )
                    scanMessage?.let {
                        Text(
                            text = it,
                            color = SapphoSuccess,
                            fontSize = 13.sp,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
            }

            item {
                AdminSectionCard(title = "Users") {
                    AdminActionRow(
                        text = "Add New User",
                        icon = Icons.Default.PersonAdd,
                        tint = SapphoPrimary,
                        onClick = { showCreateUser = true }
                    )
                    if (isLoadingUsers && users.isEmpty()) {
                        Row(
                            modifier = Modifier.padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            CircularProgressIndicator(color = SapphoInfo, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                            Text("Loading users...", color = SapphoTextMuted, fontSize = 14.sp)
                        }
                    }
                }
            }

            items(users, key = { it.id }) { user ->
                AdminUserRow(
                    user = user,
                    onClick = { selectedUser = user },
                    onDelete = { userToDelete = user }
                )
            }
        }
    }

    if (showCreateUser) {
        CreateUserDialog(
            onDismiss = { showCreateUser = false },
            onCreate = { username, password, isAdmin, onResult ->
                viewModel.createUser(username, password, isAdmin) { error ->
                    onResult(error)
                    if (error == null) showCreateUser = false
                }
            }
        )
    }

    selectedUser?.let { user ->
        UserDetailDialog(
            user = user,
            onDismiss = { selectedUser = null },
            onDelete = {
                selectedUser = null
                userToDelete = user
            }
        )
    }

    userToDelete?.let { user ->
        AlertDialog(
            onDismissRequest = { userToDelete = null },
            title = { Text("Delete User", color = Color.White) },
            text = { Text("Are you sure you want to delete ${user.username}?", color = SapphoIconDefault) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteUser(user.id)
                    userToDelete = null
                }) {
                    Text("Delete", color = SapphoError)
                }
            },
            dismissButton = {
                TextButton(onClick = { userToDelete = null }) {
                    Text("Cancel", color = SapphoIconDefault)
                }
            },
            containerColor = SapphoSurfaceLight
        )
    }
}

@Composable
private fun AdminHeader(onBack: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Surface(
            onClick = onBack,
            shape = RoundedCornerShape(8.dp),
            color = SapphoSurfaceLight,
            modifier = Modifier.size(40.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
        Text(
            text = "Admin",
            color = Color.White,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun AdminSectionCard(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .border(width = 1.dp, color = SapphoProgressTrack, shape = RoundedCornerShape(12.dp)),
        shape = RoundedCornerShape(12.dp),
        color = SapphoSurfaceLight
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = title,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.White,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            content()
        }
    }
}

@Composable
private fun AdminActionRow(
    text: String,
    icon: ImageVector,
    tint: Color,
    busy: Boolean = false,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = !busy, onClick = onClick)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = tint, modifier = Modifier.size(22.dp))
        Text(
            text = text,
            color = if (busy) SapphoTextMuted else Color.White,
            fontSize = 15.sp,
            modifier = Modifier.weight(1f)
        )
        if (busy) {
            CircularProgressIndicator(color = SapphoInfo, modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
        }
    }
}

@Composable
private fun Badge(text: String, color: Color) {
    Surface(shape = RoundedCornerShape(8.dp), color = color.copy(alpha = 0.2f)) {
        Text(
            text = text,
            color = color,
            fontSize = 11.sp,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
        )
    }
}

@Composable
private fun AdminUserRow(
    user: UserInfo,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = SapphoSurfaceLight
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(text = user.username, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                    if (user.isAdmin == 1) Badge("Admin", SapphoPrimary)
                    if (user.accountDisabled) Badge("Disabled", SapphoError)
                }
                user.email?.takeIf { it.isNotBlank() }?.let {
                    Text(text = it, color = SapphoTextMuted, fontSize = 13.sp)
                }
                UserActivityFormatter.listeningLine(user)?.let { ListeningRow(it) }
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Outlined.Delete, contentDescription = "Delete ${user.username}", tint = SapphoError)
            }
        }
    }
}

/** "Listened to <title> · <when>": the title ellipsizes so the time always stays visible. */
@Composable
private fun ListeningRow(line: ListeningLine) {
    when (line) {
        ListeningLine.NoneYet -> Text(line.text, color = SapphoTextMuted, fontSize = 12.sp, maxLines = 1)
        is ListeningLine.Listened -> Row {
            Text(
                text = line.lead,
                color = SapphoTextMuted,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false)
            )
            Text(text = " · ${line.time}", color = SapphoTextMuted, fontSize = 12.sp, maxLines = 1)
        }
    }
}

@Composable
private fun UserDetailDialog(
    user: UserInfo,
    onDismiss: () -> Unit,
    onDelete: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(user.username, color = Color.White) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                DetailRow("Role", if (user.isAdmin == 1) "Admin" else "Member")
                user.email?.takeIf { it.isNotBlank() }?.let { DetailRow("Email", it) }
                if (user.accountDisabled) DetailRow("Status", "Disabled")
                UserActivityFormatter.listeningLine(user)?.let { line ->
                    Text(line.text, color = Color.White, fontSize = 14.sp)
                }
                DetailRow("Last login", UserActivityFormatter.lastLoginLabel(user))
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Done", color = SapphoInfo) }
        },
        dismissButton = {
            TextButton(onClick = onDelete) { Text("Delete User", color = SapphoError) }
        },
        containerColor = SapphoSurfaceLight
    )
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = SapphoIconDefault, fontSize = 14.sp)
        Spacer(modifier = Modifier.width(16.dp))
        Text(value, color = Color.White, fontSize = 14.sp)
    }
}

@Composable
private fun CreateUserDialog(
    onDismiss: () -> Unit,
    onCreate: (username: String, password: String, isAdmin: Boolean, onResult: (String?) -> Unit) -> Unit
) {
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var isAdmin by remember { mutableStateOf(false) }
    var showPassword by remember { mutableStateOf(false) }
    var isSubmitting by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedTextColor = Color.White,
        unfocusedTextColor = Color.White,
        focusedBorderColor = SapphoInfo,
        unfocusedBorderColor = SapphoProgressTrack,
        focusedLabelColor = SapphoInfo,
        unfocusedLabelColor = SapphoIconDefault,
        cursorColor = SapphoInfo
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New User", color = Color.White) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it },
                    label = { Text("Username") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = fieldColors
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Password") },
                    singleLine = true,
                    visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { showPassword = !showPassword }) {
                            Icon(
                                if (showPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = if (showPassword) "Hide password" else "Show password",
                                tint = SapphoIconDefault
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = fieldColors
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Administrator", color = Color.White)
                    Switch(
                        checked = isAdmin,
                        onCheckedChange = { isAdmin = it },
                        colors = SwitchDefaults.colors(checkedTrackColor = SapphoInfo)
                    )
                }
                errorMessage?.let { Text(it, color = SapphoError, fontSize = 13.sp) }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    isSubmitting = true
                    errorMessage = null
                    onCreate(username, password, isAdmin) { error ->
                        isSubmitting = false
                        errorMessage = error
                    }
                },
                enabled = username.isNotBlank() && password.isNotBlank() && !isSubmitting
            ) {
                Text(if (isSubmitting) "Creating..." else "Create", color = SapphoInfo)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = SapphoIconDefault) }
        },
        containerColor = SapphoSurfaceLight
    )
}
