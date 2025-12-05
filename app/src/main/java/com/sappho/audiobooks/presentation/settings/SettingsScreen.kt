package com.sappho.audiobooks.presentation.settings

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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.sappho.audiobooks.data.remote.AiSettings
import com.sappho.audiobooks.data.remote.AiSettingsUpdate
import com.sappho.audiobooks.data.remote.UserInfo

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBackClick: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val librarySettings by viewModel.librarySettings.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val isSaving by viewModel.isSaving.collectAsState()
    val isScanning by viewModel.isScanning.collectAsState()
    val message by viewModel.message.collectAsState()
    val aiSettings by viewModel.aiSettings.collectAsState()
    val users by viewModel.users.collectAsState()
    val scanResult by viewModel.scanResult.collectAsState()

    // Library settings fields
    var libraryPath by remember(librarySettings) { mutableStateOf(librarySettings?.libraryPath ?: "") }
    var uploadPath by remember(librarySettings) { mutableStateOf(librarySettings?.uploadPath ?: "") }

    // Dialogs
    var showForceRescanDialog by remember { mutableStateOf(false) }
    var showAiDialog by remember { mutableStateOf(false) }
    var showUserDialog by remember { mutableStateOf(false) }
    var showDeleteUserDialog by remember { mutableStateOf<UserInfo?>(null) }

    // New user fields
    var newUsername by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var newEmail by remember { mutableStateOf("") }
    var newIsAdmin by remember { mutableStateOf(false) }

    // AI settings state
    var aiProvider by remember(aiSettings) { mutableStateOf(aiSettings?.aiProvider ?: "openai") }
    var openaiKey by remember { mutableStateOf("") }
    var geminiKey by remember { mutableStateOf("") }
    var offensiveMode by remember(aiSettings) { mutableStateOf(aiSettings?.recapOffensiveMode ?: false) }
    var customPrompt by remember(aiSettings) { mutableStateOf(aiSettings?.recapCustomPrompt ?: "") }
    var showPromptEditor by remember { mutableStateOf(false) }

    // Snackbar
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(message) {
        message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearMessage()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = Color(0xFF0A0E1A),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Administration",
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
                    containerColor = Color(0xFF1a1a1a)
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
                CircularProgressIndicator(color = Color(0xFF3B82F6))
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp)
            ) {
                // Library Management Section
                SectionCard(
                    title = "Library",
                    icon = Icons.Outlined.LibraryBooks
                ) {
                    SettingsRow(
                        icon = Icons.Outlined.Refresh,
                        title = "Scan Library",
                        subtitle = "Scan for new audiobooks",
                        onClick = { viewModel.scanLibrary(refresh = true) }
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
                        icon = Icons.Outlined.Warning,
                        title = "Force Rescan",
                        subtitle = "Clear database and re-import all audiobooks",
                        iconTint = Color(0xFFf59e0b),
                        onClick = { showForceRescanDialog = true }
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Library Settings Section
                SectionCard(
                    title = "Library Paths",
                    icon = Icons.Outlined.Folder
                ) {
                    OutlinedTextField(
                        value = libraryPath,
                        onValueChange = { libraryPath = it },
                        label = { Text("Library Directory") },
                        placeholder = { Text("/app/data/audiobooks") },
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
                        text = "Main directory where audiobooks are stored",
                        fontSize = 12.sp,
                        color = Color(0xFF6b7280),
                        modifier = Modifier.padding(start = 4.dp, top = 4.dp)
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    OutlinedTextField(
                        value = uploadPath,
                        onValueChange = { uploadPath = it },
                        label = { Text("Upload Directory") },
                        placeholder = { Text("/app/data/uploads") },
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
                        text = "Temporary directory for web uploads",
                        fontSize = 12.sp,
                        color = Color(0xFF6b7280),
                        modifier = Modifier.padding(start = 4.dp, top = 4.dp)
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Button(
                        onClick = { viewModel.updateLibrarySettings(libraryPath, uploadPath) },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !isSaving && libraryPath.isNotBlank() && uploadPath.isNotBlank(),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF3b82f6),
                            contentColor = Color.White
                        )
                    ) {
                        if (isSaving) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = Color.White,
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                        Text(if (isSaving) "Saving..." else "Save Paths")
                    }
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
                            "gemini" -> aiSettings?.geminiApiKey?.isNotEmpty() == true
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
                        subtitle = "Set up OpenAI or Gemini API key for Catch Me Up",
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
                                    onClick = { showDeleteUserDialog = user }
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
        }
    }

    // Force Rescan Confirmation Dialog
    if (showForceRescanDialog) {
        AlertDialog(
            onDismissRequest = { showForceRescanDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = null,
                        tint = Color(0xFFdc2626),
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text("Force Rescan", color = Color.White)
                }
            },
            text = {
                Text(
                    "This will CLEAR the entire library database and reimport all audiobooks. All playback progress will be lost.\n\nThis action cannot be undone. Are you sure?",
                    color = Color(0xFFd1d5db)
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showForceRescanDialog = false
                        viewModel.forceRescanLibrary()
                    }
                ) {
                    Text("Force Rescan", color = Color(0xFFdc2626))
                }
            },
            dismissButton = {
                TextButton(onClick = { showForceRescanDialog = false }) {
                    Text("Cancel", color = Color(0xFF3b82f6))
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
                Column(
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.verticalScroll(rememberScrollState())
                ) {
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

                    Divider(color = Color(0xFF374151))

                    Text(
                        text = "Recap Style",
                        color = Color(0xFF9ca3af),
                        fontSize = 12.sp
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { offensiveMode = !offensiveMode }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Offensive Mode",
                                color = Color.White,
                                fontSize = 14.sp
                            )
                            Text(
                                text = "Crude, irreverent recaps with profanity",
                                color = Color(0xFF6b7280),
                                fontSize = 11.sp
                            )
                        }
                        Switch(
                            checked = offensiveMode,
                            onCheckedChange = { offensiveMode = it },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = Color(0xFFef4444),
                                uncheckedThumbColor = Color(0xFF9ca3af),
                                uncheckedTrackColor = Color(0xFF374151)
                            )
                        )
                    }

                    Divider(color = Color(0xFF374151))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showPromptEditor = true }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Custom Prompt",
                                color = Color.White,
                                fontSize = 14.sp
                            )
                            Text(
                                text = if (customPrompt.isNotBlank()) "Custom prompt set" else "Using default prompt",
                                color = Color(0xFF6b7280),
                                fontSize = 11.sp
                            )
                        }
                        Icon(
                            imageVector = Icons.Default.ChevronRight,
                            contentDescription = null,
                            tint = Color(0xFF6b7280)
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val settings = AiSettingsUpdate(
                            aiProvider = aiProvider,
                            openaiApiKey = openaiKey.ifBlank { null },
                            geminiApiKey = geminiKey.ifBlank { null },
                            recapOffensiveMode = offensiveMode,
                            recapCustomPrompt = customPrompt.ifBlank { null }
                        )
                        viewModel.updateAiSettings(settings) { success, _ ->
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

    // Custom Prompt Editor Dialog
    if (showPromptEditor) {
        AlertDialog(
            onDismissRequest = { showPromptEditor = false },
            title = { Text("Custom Recap Prompt", color = Color.White) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "Customize the system prompt used for series recaps. Leave empty to use the default.",
                        color = Color(0xFF9ca3af),
                        fontSize = 12.sp
                    )

                    OutlinedTextField(
                        value = customPrompt,
                        onValueChange = { customPrompt = it },
                        placeholder = { Text("Enter custom prompt...") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp),
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

                    TextButton(
                        onClick = {
                            customPrompt = aiSettings?.recapDefaultPrompt ?: ""
                        }
                    ) {
                        Text("Load Default Prompt", color = Color(0xFF3b82f6), fontSize = 12.sp)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showPromptEditor = false }) {
                    Text("Done", color = Color(0xFF3b82f6))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        customPrompt = ""
                        showPromptEditor = false
                    }
                ) {
                    Text("Clear", color = Color(0xFFef4444))
                }
            },
            containerColor = Color(0xFF1e293b)
        )
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
                        ) { success, _ ->
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

    // Delete User Confirmation Dialog
    showDeleteUserDialog?.let { user ->
        AlertDialog(
            onDismissRequest = { showDeleteUserDialog = null },
            title = { Text("Delete User", color = Color.White) },
            text = {
                Text(
                    "Are you sure you want to delete user \"${user.username}\"? This action cannot be undone.",
                    color = Color(0xFFd1d5db)
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteUser(user.id) { _, _ -> }
                        showDeleteUserDialog = null
                    }
                ) {
                    Text("Delete", color = Color(0xFFdc2626))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteUserDialog = null }) {
                    Text("Cancel", color = Color(0xFF3b82f6))
                }
            },
            containerColor = Color(0xFF1e293b)
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
