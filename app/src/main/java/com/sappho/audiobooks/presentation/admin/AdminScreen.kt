package com.sappho.audiobooks.presentation.admin

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.sappho.audiobooks.data.remote.*
import java.text.SimpleDateFormat
import java.util.*

enum class AdminTab(val title: String, val icon: ImageVector) {
    LIBRARY("Library", Icons.Outlined.LibraryBooks),
    SERVER("Server", Icons.Outlined.Dns),
    AI("AI", Icons.Outlined.Psychology),
    USERS("Users", Icons.Outlined.People),
    BACKUP("Backup", Icons.Outlined.Backup),
    MAINTENANCE("Maintenance", Icons.Outlined.Build),
    LOGS("Logs", Icons.Outlined.Article),
    STATISTICS("Statistics", Icons.Outlined.BarChart)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminScreen(
    onBack: () -> Unit,
    viewModel: AdminViewModel = hiltViewModel()
) {
    var selectedTab by remember { mutableStateOf(AdminTab.LIBRARY) }
    val isLoading by viewModel.isLoading.collectAsState()
    val message by viewModel.message.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(message) {
        message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearMessage()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Admin Panel", color = Color.White) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF0A0E1A)
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = Color(0xFF0A0E1A)
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Tab Row - Scrollable horizontal tabs
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF1e293b))
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(AdminTab.entries) { tab ->
                    AdminTabChip(
                        tab = tab,
                        selected = selectedTab == tab,
                        onClick = { selectedTab = tab }
                    )
                }
            }

            // Content
            if (isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = Color(0xFF3b82f6))
                }
            } else {
                when (selectedTab) {
                    AdminTab.LIBRARY -> LibraryTab(viewModel)
                    AdminTab.SERVER -> ServerSettingsTab(viewModel)
                    AdminTab.AI -> AiSettingsTab(viewModel)
                    AdminTab.USERS -> UsersTab(viewModel)
                    AdminTab.BACKUP -> BackupTab(viewModel)
                    AdminTab.MAINTENANCE -> MaintenanceTab(viewModel)
                    AdminTab.LOGS -> LogsTab(viewModel)
                    AdminTab.STATISTICS -> StatisticsTab(viewModel)
                }
            }
        }
    }
}

@Composable
private fun AdminTabChip(
    tab: AdminTab,
    selected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        color = if (selected) Color(0xFF3b82f6) else Color(0xFF374151)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = tab.icon,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(18.dp)
            )
            Text(
                text = tab.title,
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
            )
        }
    }
}

// ============ Library Tab ============
@Composable
private fun LibraryTab(viewModel: AdminViewModel) {
    val serverSettings by viewModel.serverSettings.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.loadServerSettings()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        AdminSectionCard(title = "Library Actions", icon = Icons.Outlined.Refresh) {
            ActionButton(
                text = "Scan Library",
                description = "Scan for new audiobooks",
                icon = Icons.Outlined.Search,
                onClick = { viewModel.scanLibrary() }
            )
            ActionButton(
                text = "Force Rescan",
                description = "Rescan all audiobook metadata",
                icon = Icons.Outlined.Refresh,
                onClick = { viewModel.forceRescan() }
            )
        }

        serverSettings?.settings?.let { settings ->
            AdminSectionCard(title = "Library Paths", icon = Icons.Outlined.Folder) {
                InfoRow(label = "Audiobooks Directory", value = settings.audiobooksDir ?: "Not set")
                InfoRow(label = "Upload Directory", value = settings.uploadDir ?: "Not set")
            }
        }
    }
}

// ============ Server Settings Tab ============
@Composable
private fun ServerSettingsTab(viewModel: AdminViewModel) {
    val serverSettings by viewModel.serverSettings.collectAsState()
    var editMode by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.loadServerSettings()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        serverSettings?.settings?.let { settings ->
            AdminSectionCard(title = "Server Configuration", icon = Icons.Outlined.Settings) {
                InfoRow(label = "Port", value = settings.port ?: "3000")
                InfoRow(label = "Environment", value = settings.nodeEnv ?: "production")
                InfoRow(label = "Database Path", value = settings.databasePath ?: "Not set")
                InfoRow(label = "Data Directory", value = settings.dataDir ?: "Not set")
                InfoRow(
                    label = "Scan Interval",
                    value = settings.libraryScanInterval?.let { "${it / 60} minutes" } ?: "Not set"
                )
            }
        } ?: run {
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Text("Loading server settings...", color = Color(0xFF9ca3af))
            }
        }
    }
}

// ============ AI Settings Tab ============
@Composable
private fun AiSettingsTab(viewModel: AdminViewModel) {
    val aiSettings by viewModel.aiSettings.collectAsState()
    var showEditDialog by remember { mutableStateOf(false) }
    var testResult by remember { mutableStateOf<AiTestResponse?>(null) }

    LaunchedEffect(Unit) {
        viewModel.loadAiSettings()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        aiSettings?.let { settings ->
            AdminSectionCard(
                title = "AI Provider",
                icon = Icons.Outlined.Psychology,
                action = {
                    IconButton(onClick = { showEditDialog = true }) {
                        Icon(Icons.Outlined.Edit, contentDescription = "Edit", tint = Color(0xFF3b82f6))
                    }
                }
            ) {
                InfoRow(
                    label = "Provider",
                    value = settings.aiProvider?.uppercase() ?: "Not configured"
                )
                if (settings.aiProvider == "openai") {
                    InfoRow(label = "Model", value = settings.openaiModel ?: "gpt-3.5-turbo")
                    InfoRow(
                        label = "API Key",
                        value = if (settings.openaiApiKey != null) "****configured****" else "Not set"
                    )
                } else if (settings.aiProvider == "gemini") {
                    InfoRow(label = "Model", value = settings.geminiModel ?: "gemini-pro")
                    InfoRow(
                        label = "API Key",
                        value = if (settings.geminiApiKey != null) "****configured****" else "Not set"
                    )
                }
            }

            AdminSectionCard(title = "Recap Settings", icon = Icons.Outlined.AutoStories) {
                InfoRow(
                    label = "Offensive Mode",
                    value = if (settings.recapOffensiveMode == true) "Enabled" else "Disabled"
                )
                if (settings.recapCustomPrompt != null) {
                    Text(
                        "Custom Prompt:",
                        color = Color(0xFF9ca3af),
                        fontSize = 12.sp
                    )
                    Text(
                        settings.recapCustomPrompt,
                        color = Color.White,
                        fontSize = 14.sp,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Button(
                onClick = {
                    viewModel.testAiConnection(
                        AiSettingsUpdate(
                            aiProvider = settings.aiProvider,
                            openaiApiKey = settings.openaiApiKey,
                            openaiModel = settings.openaiModel,
                            geminiApiKey = settings.geminiApiKey,
                            geminiModel = settings.geminiModel
                        )
                    ) { result ->
                        testResult = result
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF374151))
            ) {
                Icon(Icons.Outlined.Science, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Test AI Connection")
            }

            testResult?.let { result ->
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    color = if (result.error == null) Color(0xFF10b981).copy(alpha = 0.2f)
                    else Color(0xFFef4444).copy(alpha = 0.2f)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = if (result.error == null) "Test Successful" else "Test Failed",
                            color = if (result.error == null) Color(0xFF10b981) else Color(0xFFef4444),
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = result.error ?: result.response ?: result.message ?: "",
                            color = Color.White,
                            fontSize = 14.sp
                        )
                    }
                }
            }
        } ?: run {
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Text("Loading AI settings...", color = Color(0xFF9ca3af))
            }
        }
    }

    // AI Settings Edit Dialog
    if (showEditDialog) {
        aiSettings?.let { settings ->
            EditAiSettingsDialog(
                settings = settings,
                onDismiss = { showEditDialog = false },
                onSave = { update ->
                    viewModel.updateAiSettings(update)
                    showEditDialog = false
                }
            )
        }
    }
}

@Composable
private fun EditAiSettingsDialog(
    settings: AiSettings,
    onDismiss: () -> Unit,
    onSave: (AiSettingsUpdate) -> Unit
) {
    var provider by remember { mutableStateOf(settings.aiProvider ?: "") }
    var openaiApiKey by remember { mutableStateOf(settings.openaiApiKey ?: "") }
    var openaiModel by remember { mutableStateOf(settings.openaiModel ?: "gpt-3.5-turbo") }
    var geminiApiKey by remember { mutableStateOf(settings.geminiApiKey ?: "") }
    var geminiModel by remember { mutableStateOf(settings.geminiModel ?: "gemini-pro") }
    var customPrompt by remember { mutableStateOf(settings.recapCustomPrompt ?: "") }
    var offensiveMode by remember { mutableStateOf(settings.recapOffensiveMode ?: false) }
    var showApiKey by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit AI Settings", color = Color.White) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Provider Selection
                Text("AI Provider", color = Color(0xFF9ca3af), fontSize = 12.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("openai", "gemini", "").forEach { p ->
                        val label = when(p) {
                            "openai" -> "OpenAI"
                            "gemini" -> "Gemini"
                            else -> "None"
                        }
                        Surface(
                            modifier = Modifier.clickable { provider = p },
                            shape = RoundedCornerShape(8.dp),
                            color = if (provider == p) Color(0xFF3b82f6) else Color(0xFF374151)
                        ) {
                            Text(
                                label,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                color = Color.White,
                                fontSize = 14.sp
                            )
                        }
                    }
                }

                if (provider == "openai") {
                    OutlinedTextField(
                        value = openaiApiKey,
                        onValueChange = { openaiApiKey = it },
                        label = { Text("OpenAI API Key") },
                        visualTransformation = if (showApiKey) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { showApiKey = !showApiKey }) {
                                Icon(
                                    if (showApiKey) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = null,
                                    tint = Color(0xFF9ca3af)
                                )
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = adminTextFieldColors()
                    )
                    OutlinedTextField(
                        value = openaiModel,
                        onValueChange = { openaiModel = it },
                        label = { Text("Model") },
                        modifier = Modifier.fillMaxWidth(),
                        colors = adminTextFieldColors()
                    )
                } else if (provider == "gemini") {
                    OutlinedTextField(
                        value = geminiApiKey,
                        onValueChange = { geminiApiKey = it },
                        label = { Text("Gemini API Key") },
                        visualTransformation = if (showApiKey) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { showApiKey = !showApiKey }) {
                                Icon(
                                    if (showApiKey) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = null,
                                    tint = Color(0xFF9ca3af)
                                )
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = adminTextFieldColors()
                    )
                    OutlinedTextField(
                        value = geminiModel,
                        onValueChange = { geminiModel = it },
                        label = { Text("Model") },
                        modifier = Modifier.fillMaxWidth(),
                        colors = adminTextFieldColors()
                    )
                }

                Divider(color = Color(0xFF374151))

                Text("Recap Settings", color = Color(0xFF9ca3af), fontSize = 12.sp)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Checkbox(
                        checked = offensiveMode,
                        onCheckedChange = { offensiveMode = it },
                        colors = CheckboxDefaults.colors(
                            checkedColor = Color(0xFF3b82f6),
                            uncheckedColor = Color(0xFF6b7280)
                        )
                    )
                    Text("Offensive Mode (uncensored recaps)", color = Color.White)
                }

                OutlinedTextField(
                    value = customPrompt,
                    onValueChange = { customPrompt = it },
                    label = { Text("Custom Prompt (optional)") },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 100.dp),
                    colors = adminTextFieldColors(),
                    maxLines = 5
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onSave(AiSettingsUpdate(
                        aiProvider = provider.ifBlank { null },
                        openaiApiKey = openaiApiKey.ifBlank { null },
                        openaiModel = openaiModel.ifBlank { null },
                        geminiApiKey = geminiApiKey.ifBlank { null },
                        geminiModel = geminiModel.ifBlank { null },
                        recapCustomPrompt = customPrompt.ifBlank { null },
                        recapOffensiveMode = offensiveMode
                    ))
                }
            ) {
                Text("Save", color = Color(0xFF3b82f6))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = Color(0xFF9ca3af))
            }
        },
        containerColor = Color(0xFF1e293b)
    )
}

// ============ Users Tab ============
@Composable
private fun UsersTab(viewModel: AdminViewModel) {
    val users by viewModel.users.collectAsState()
    var showCreateDialog by remember { mutableStateOf(false) }
    var editingUser by remember { mutableStateOf<UserInfo?>(null) }
    var userToDelete by remember { mutableStateOf<UserInfo?>(null) }

    LaunchedEffect(Unit) {
        viewModel.loadUsers()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "User Management",
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
            Button(
                onClick = { showCreateDialog = true },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF3b82f6))
            ) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(modifier = Modifier.width(4.dp))
                Text("Add User")
            }
        }

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(users) { user ->
                UserCard(
                    user = user,
                    onEdit = { editingUser = user },
                    onDelete = { userToDelete = user }
                )
            }
        }
    }

    // Create User Dialog
    if (showCreateDialog) {
        CreateUserDialog(
            onDismiss = { showCreateDialog = false },
            onCreate = { request ->
                viewModel.createUser(request) { showCreateDialog = false }
            }
        )
    }

    // Edit User Dialog
    editingUser?.let { user ->
        EditUserDialog(
            user = user,
            onDismiss = { editingUser = null },
            onUpdate = { request ->
                viewModel.updateUser(user.id, request) { editingUser = null }
            }
        )
    }

    // Delete Confirmation Dialog
    userToDelete?.let { user ->
        AlertDialog(
            onDismissRequest = { userToDelete = null },
            title = { Text("Delete User", color = Color.White) },
            text = { Text("Are you sure you want to delete ${user.username}?", color = Color(0xFF9ca3af)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteUser(user.id)
                        userToDelete = null
                    }
                ) {
                    Text("Delete", color = Color(0xFFef4444))
                }
            },
            dismissButton = {
                TextButton(onClick = { userToDelete = null }) {
                    Text("Cancel", color = Color(0xFF9ca3af))
                }
            },
            containerColor = Color(0xFF1e293b)
        )
    }
}

@Composable
private fun UserCard(
    user: UserInfo,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = Color(0xFF1e293b)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = user.username,
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium
                    )
                    if (user.isAdmin == 1) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = Color(0xFF10b981).copy(alpha = 0.2f)
                        ) {
                            Text(
                                "Admin",
                                color = Color(0xFF10b981),
                                fontSize = 11.sp,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
                user.email?.let { email ->
                    Text(
                        text = email,
                        color = Color(0xFF9ca3af),
                        fontSize = 13.sp
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                IconButton(onClick = onEdit) {
                    Icon(Icons.Outlined.Edit, contentDescription = "Edit", tint = Color(0xFF3b82f6))
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Outlined.Delete, contentDescription = "Delete", tint = Color(0xFFef4444))
                }
            }
        }
    }
}

@Composable
private fun CreateUserDialog(
    onDismiss: () -> Unit,
    onCreate: (CreateUserRequest) -> Unit
) {
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var isAdmin by remember { mutableStateOf(false) }
    var showPassword by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Create User", color = Color.White) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it },
                    label = { Text("Username") },
                    modifier = Modifier.fillMaxWidth(),
                    colors = adminTextFieldColors()
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Password") },
                    visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { showPassword = !showPassword }) {
                            Icon(
                                if (showPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = null,
                                tint = Color(0xFF9ca3af)
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = adminTextFieldColors()
                )
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text("Email (optional)") },
                    modifier = Modifier.fillMaxWidth(),
                    colors = adminTextFieldColors()
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Checkbox(
                        checked = isAdmin,
                        onCheckedChange = { isAdmin = it },
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
                    onCreate(CreateUserRequest(username, password, email.ifBlank { null }, isAdmin))
                },
                enabled = username.isNotBlank() && password.isNotBlank()
            ) {
                Text("Create", color = Color(0xFF3b82f6))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = Color(0xFF9ca3af))
            }
        },
        containerColor = Color(0xFF1e293b)
    )
}

@Composable
private fun EditUserDialog(
    user: UserInfo,
    onDismiss: () -> Unit,
    onUpdate: (UpdateUserRequest) -> Unit
) {
    var username by remember { mutableStateOf(user.username) }
    var password by remember { mutableStateOf("") }
    var email by remember { mutableStateOf(user.email ?: "") }
    var isAdmin by remember { mutableStateOf(user.isAdmin == 1) }
    var showPassword by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit User", color = Color.White) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it },
                    label = { Text("Username") },
                    modifier = Modifier.fillMaxWidth(),
                    colors = adminTextFieldColors()
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("New Password (leave blank to keep current)") },
                    visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { showPassword = !showPassword }) {
                            Icon(
                                if (showPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = null,
                                tint = Color(0xFF9ca3af)
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = adminTextFieldColors()
                )
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text("Email") },
                    modifier = Modifier.fillMaxWidth(),
                    colors = adminTextFieldColors()
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Checkbox(
                        checked = isAdmin,
                        onCheckedChange = { isAdmin = it },
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
                    onUpdate(
                        UpdateUserRequest(
                            username = username.takeIf { it != user.username },
                            password = password.ifBlank { null },
                            email = email.ifBlank { null },
                            isAdmin = isAdmin
                        )
                    )
                },
                enabled = username.isNotBlank()
            ) {
                Text("Update", color = Color(0xFF3b82f6))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = Color(0xFF9ca3af))
            }
        },
        containerColor = Color(0xFF1e293b)
    )
}

// ============ Backup Tab ============
@Composable
private fun BackupTab(viewModel: AdminViewModel) {
    val backups by viewModel.backups.collectAsState()
    val retention by viewModel.backupRetention.collectAsState()
    var backupToDelete by remember { mutableStateOf<BackupInfo?>(null) }
    var backupToRestore by remember { mutableStateOf<BackupInfo?>(null) }

    LaunchedEffect(Unit) {
        viewModel.loadBackups()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Backups",
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
            Button(
                onClick = { viewModel.createBackup() },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF3b82f6))
            ) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(modifier = Modifier.width(4.dp))
                Text("Create Backup")
            }
        }

        retention?.let { ret ->
            AdminSectionCard(title = "Retention Settings", icon = Icons.Outlined.Schedule) {
                InfoRow(label = "Max Backups", value = ret.maxBackups.toString())
                InfoRow(label = "Auto Backup", value = if (ret.autoBackup == true) "Enabled" else "Disabled")
                ret.backupIntervalDays?.let { days ->
                    InfoRow(label = "Interval", value = "$days days")
                }
            }
        }

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(backups) { backup ->
                BackupCard(
                    backup = backup,
                    onRestore = { backupToRestore = backup },
                    onDelete = { backupToDelete = backup }
                )
            }
        }
    }

    // Delete Confirmation
    backupToDelete?.let { backup ->
        AlertDialog(
            onDismissRequest = { backupToDelete = null },
            title = { Text("Delete Backup", color = Color.White) },
            text = { Text("Are you sure you want to delete ${backup.filename}?", color = Color(0xFF9ca3af)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteBackup(backup.filename)
                        backupToDelete = null
                    }
                ) {
                    Text("Delete", color = Color(0xFFef4444))
                }
            },
            dismissButton = {
                TextButton(onClick = { backupToDelete = null }) {
                    Text("Cancel", color = Color(0xFF9ca3af))
                }
            },
            containerColor = Color(0xFF1e293b)
        )
    }

    // Restore Confirmation
    backupToRestore?.let { backup ->
        AlertDialog(
            onDismissRequest = { backupToRestore = null },
            title = { Text("Restore Backup", color = Color.White) },
            text = {
                Text(
                    "Are you sure you want to restore ${backup.filename}? This will overwrite the current database.",
                    color = Color(0xFF9ca3af)
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.restoreBackup(backup.filename)
                        backupToRestore = null
                    }
                ) {
                    Text("Restore", color = Color(0xFFf59e0b))
                }
            },
            dismissButton = {
                TextButton(onClick = { backupToRestore = null }) {
                    Text("Cancel", color = Color(0xFF9ca3af))
                }
            },
            containerColor = Color(0xFF1e293b)
        )
    }
}

@Composable
private fun BackupCard(
    backup: BackupInfo,
    onRestore: () -> Unit,
    onDelete: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = Color(0xFF1e293b)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = backup.filename,
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text(
                        text = backup.sizeFormatted ?: formatFileSize(backup.size),
                        color = Color(0xFF9ca3af),
                        fontSize = 12.sp
                    )
                    Text(
                        text = backup.createdFormatted ?: backup.created ?: "Unknown",
                        color = Color(0xFF9ca3af),
                        fontSize = 12.sp
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                IconButton(onClick = onRestore) {
                    Icon(Icons.Outlined.Restore, contentDescription = "Restore", tint = Color(0xFFf59e0b))
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Outlined.Delete, contentDescription = "Delete", tint = Color(0xFFef4444))
                }
            }
        }
    }
}

// ============ Maintenance Tab ============
@Composable
private fun MaintenanceTab(viewModel: AdminViewModel) {
    val duplicates by viewModel.duplicates.collectAsState()
    val jobs by viewModel.jobs.collectAsState()
    var showClearLibraryDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.loadDuplicates()
        viewModel.loadJobs()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        AdminSectionCard(title = "Library Actions", icon = Icons.Outlined.Build) {
            ActionButton(
                text = "Scan Library",
                description = "Scan for new audiobooks",
                icon = Icons.Outlined.Search,
                onClick = { viewModel.scanLibrary() }
            )
            ActionButton(
                text = "Force Rescan",
                description = "Rescan all audiobook metadata",
                icon = Icons.Outlined.Refresh,
                onClick = { viewModel.forceRescan() }
            )
            ActionButton(
                text = "Clear Library",
                description = "Remove all audiobooks from database",
                icon = Icons.Outlined.DeleteForever,
                color = Color(0xFFef4444),
                onClick = { showClearLibraryDialog = true }
            )
        }

        if (duplicates.isNotEmpty()) {
            AdminSectionCard(title = "Duplicates (${duplicates.size})", icon = Icons.Outlined.ContentCopy) {
                duplicates.take(5).forEach { group ->
                    Text(
                        text = "${group.key} (${group.books.size} copies)",
                        color = Color.White,
                        fontSize = 14.sp
                    )
                }
                if (duplicates.size > 5) {
                    Text(
                        "And ${duplicates.size - 5} more...",
                        color = Color(0xFF9ca3af),
                        fontSize = 12.sp
                    )
                }
            }
        }

        if (jobs.isNotEmpty()) {
            AdminSectionCard(title = "Scheduled Jobs", icon = Icons.Outlined.Schedule) {
                jobs.forEach { job ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(job.name, color = Color.White, fontSize = 14.sp)
                            Text(
                                "Status: ${job.status}",
                                color = Color(0xFF9ca3af),
                                fontSize = 12.sp
                            )
                        }
                        job.nextRun?.let {
                            Text(
                                "Next: ${formatBackupDate(it)}",
                                color = Color(0xFF6b7280),
                                fontSize = 12.sp
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
        }
    }

    if (showClearLibraryDialog) {
        AlertDialog(
            onDismissRequest = { showClearLibraryDialog = false },
            title = { Text("Clear Library", color = Color.White) },
            text = {
                Text(
                    "This will remove ALL audiobooks from the database. Audio files will not be deleted. Are you sure?",
                    color = Color(0xFFef4444)
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.clearLibrary { showClearLibraryDialog = false }
                    }
                ) {
                    Text("Clear Library", color = Color(0xFFef4444))
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearLibraryDialog = false }) {
                    Text("Cancel", color = Color(0xFF9ca3af))
                }
            },
            containerColor = Color(0xFF1e293b)
        )
    }
}

// ============ Logs Tab ============
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LogsTab(viewModel: AdminViewModel) {
    val logs by viewModel.logs.collectAsState()
    var selectedLevel by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(selectedLevel) {
        viewModel.loadLogs(level = selectedLevel)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Server Logs",
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = { viewModel.loadLogs(level = selectedLevel) }) {
                    Icon(Icons.Outlined.Refresh, contentDescription = "Refresh", tint = Color(0xFF3b82f6))
                }
                TextButton(onClick = { viewModel.clearLogs() }) {
                    Text("Clear", color = Color(0xFFef4444))
                }
            }
        }

        // Level filter chips
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(null to "All", "info" to "Info", "warn" to "Warn", "error" to "Error").forEach { (level, label) ->
                FilterChip(
                    selected = selectedLevel == level,
                    onClick = { selectedLevel = level },
                    label = { Text(label) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = Color(0xFF3b82f6),
                        selectedLabelColor = Color.White,
                        containerColor = Color(0xFF374151),
                        labelColor = Color(0xFF9ca3af)
                    )
                )
            }
        }

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            items(logs) { log ->
                LogEntryCard(log)
            }
        }
    }
}

@Composable
private fun LogEntryCard(log: LogEntry) {
    val levelColor = when (log.level.lowercase()) {
        "error" -> Color(0xFFef4444)
        "warn" -> Color(0xFFf59e0b)
        "info" -> Color(0xFF3b82f6)
        else -> Color(0xFF9ca3af)
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = Color(0xFF1e293b)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(4.dp),
                color = levelColor.copy(alpha = 0.2f)
            ) {
                Text(
                    text = log.level.uppercase().take(4),
                    color = levelColor,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = log.message,
                    color = Color.White,
                    fontSize = 13.sp,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = formatBackupDate(log.timestamp),
                    color = Color(0xFF6b7280),
                    fontSize = 11.sp
                )
            }
        }
    }
}

// ============ Statistics Tab ============
@Composable
private fun StatisticsTab(viewModel: AdminViewModel) {
    val statistics by viewModel.statistics.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.loadStatistics()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        statistics?.let { stats ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                StatCard(
                    modifier = Modifier.weight(1f),
                    value = stats.totalAudiobooks.toString(),
                    label = "Audiobooks",
                    icon = Icons.Outlined.LibraryBooks,
                    color = Color(0xFF3b82f6)
                )
                StatCard(
                    modifier = Modifier.weight(1f),
                    value = stats.totalAuthors.toString(),
                    label = "Authors",
                    icon = Icons.Outlined.Person,
                    color = Color(0xFF10b981)
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                StatCard(
                    modifier = Modifier.weight(1f),
                    value = stats.totalSeries.toString(),
                    label = "Series",
                    icon = Icons.Outlined.Collections,
                    color = Color(0xFFf59e0b)
                )
                StatCard(
                    modifier = Modifier.weight(1f),
                    value = formatFileSize(stats.totalSize),
                    label = "Total Size",
                    icon = Icons.Outlined.Storage,
                    color = Color(0xFF8b5cf6)
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                StatCard(
                    modifier = Modifier.weight(1f),
                    value = formatDuration(stats.totalDuration),
                    label = "Total Duration",
                    icon = Icons.Outlined.Timer,
                    color = Color(0xFFec4899)
                )
                StatCard(
                    modifier = Modifier.weight(1f),
                    value = stats.totalGenres.toString(),
                    label = "Genres",
                    icon = Icons.Outlined.Category,
                    color = Color(0xFF06b6d4)
                )
            }

            stats.booksByGenre?.let { genres ->
                if (genres.isNotEmpty()) {
                    AdminSectionCard(title = "Books by Genre", icon = Icons.Outlined.PieChart) {
                        genres.entries.sortedByDescending { it.value }.take(10).forEach { (genre, count) ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(genre, color = Color.White, fontSize = 14.sp)
                                Text(count.toString(), color = Color(0xFF9ca3af), fontSize = 14.sp)
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                        }
                    }
                }
            }
        } ?: run {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text("Loading statistics...", color = Color(0xFF9ca3af))
            }
        }
    }
}

// ============ Helper Components ============
@Composable
private fun AdminSectionCard(
    title: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    action: @Composable (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = Color(0xFF1e293b)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = Color(0xFF3b82f6),
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = title,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White
                    )
                }
                action?.invoke()
            }
            content()
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, color = Color(0xFF9ca3af), fontSize = 14.sp)
        Text(
            value,
            color = Color.White,
            fontSize = 14.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.widthIn(max = 200.dp)
        )
    }
}

@Composable
private fun ActionButton(
    text: String,
    description: String,
    icon: ImageVector,
    color: Color = Color(0xFF3b82f6),
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp),
        color = Color.Transparent
    ) {
        Row(
            modifier = Modifier.padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(24.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(text, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                Text(description, color = Color(0xFF6b7280), fontSize = 13.sp)
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
                color = Color.White
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = label,
                fontSize = 12.sp,
                color = Color(0xFF9ca3af)
            )
        }
    }
}

@Composable
private fun adminTextFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = Color.White,
    unfocusedTextColor = Color.White,
    focusedBorderColor = Color(0xFF3b82f6),
    unfocusedBorderColor = Color(0xFF374151),
    focusedLabelColor = Color(0xFF3b82f6),
    unfocusedLabelColor = Color(0xFF9ca3af),
    cursorColor = Color(0xFF3b82f6)
)

// ============ Utility Functions ============
private fun formatFileSize(bytes: Long): String {
    return when {
        bytes >= 1_073_741_824 -> String.format("%.1f GB", bytes / 1_073_741_824.0)
        bytes >= 1_048_576 -> String.format("%.1f MB", bytes / 1_048_576.0)
        bytes >= 1_024 -> String.format("%.1f KB", bytes / 1_024.0)
        else -> "$bytes B"
    }
}

private fun formatDuration(seconds: Long): String {
    val hours = seconds / 3600
    return when {
        hours >= 24 -> {
            val days = hours / 24
            "${days}d ${hours % 24}h"
        }
        hours > 0 -> "${hours}h ${(seconds % 3600) / 60}m"
        else -> "${seconds / 60}m"
    }
}

private fun formatBackupDate(dateString: String): String {
    return try {
        val inputFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
        val outputFormat = SimpleDateFormat("MMM d, yyyy HH:mm", Locale.getDefault())
        val date = inputFormat.parse(dateString.split(".")[0])
        outputFormat.format(date ?: Date())
    } catch (e: Exception) {
        dateString
    }
}
