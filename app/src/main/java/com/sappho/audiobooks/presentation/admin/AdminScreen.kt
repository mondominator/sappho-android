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
import com.google.accompanist.swiperefresh.SwipeRefresh
import com.google.accompanist.swiperefresh.SwipeRefreshIndicator
import com.google.accompanist.swiperefresh.rememberSwipeRefreshState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.sappho.audiobooks.presentation.theme.*
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import kotlinx.coroutines.launch
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalContext
import com.sappho.audiobooks.data.remote.*
import java.text.SimpleDateFormat
import java.util.*

enum class AdminTab(val title: String, val icon: ImageVector) {
    STATISTICS("Statistics", Icons.Outlined.BarChart),
    LIBRARY("Library", Icons.Outlined.LibraryBooks),
    SERVER("Server", Icons.Outlined.Dns),
    AI("AI", Icons.Outlined.Psychology),
    USERS("Users", Icons.Outlined.People),
    API_KEYS("API Keys", Icons.Outlined.Key),
    BACKUP("Backup", Icons.Outlined.Backup),
    LOGS("Logs", Icons.Outlined.Article)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminScreen(
    onBack: () -> Unit,
    viewModel: AdminViewModel = hiltViewModel()
) {
    var selectedTab by remember { mutableStateOf(AdminTab.STATISTICS) }
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
                    containerColor = SapphoBackground
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = SapphoBackground
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
                    .background(SapphoSurfaceLight)
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

            // Content - show tab immediately, let each tab handle its own loading
            when (selectedTab) {
                AdminTab.STATISTICS -> StatisticsTab(viewModel)
                AdminTab.LIBRARY -> LibraryTab(viewModel)
                AdminTab.SERVER -> ServerSettingsTab(viewModel)
                AdminTab.AI -> AiSettingsTab(viewModel)
                AdminTab.USERS -> UsersTab(viewModel)
                AdminTab.API_KEYS -> ApiKeysTab(viewModel)
                AdminTab.BACKUP -> BackupTab(viewModel)
                AdminTab.LOGS -> LogsTab(viewModel)
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
        color = if (selected) SapphoInfo else SapphoProgressTrack
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
    val duplicates by viewModel.duplicates.collectAsState()
    val jobs by viewModel.jobs.collectAsState()
    val loadingSection by viewModel.loadingSection.collectAsState()
    val isLoading = loadingSection == "serverSettings" || loadingSection == "library"
    var showEditDialog by remember { mutableStateOf(false) }
    var selectedDuplicateGroup by remember { mutableStateOf<DuplicateGroup?>(null) }
    var isRefreshing by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.loadServerSettings()
        viewModel.loadDuplicates()
        viewModel.loadJobs()
    }

    LaunchedEffect(loadingSection) {
        if (loadingSection != "library") isRefreshing = false
    }

    val swipeRefreshState = rememberSwipeRefreshState(isRefreshing)

    SwipeRefresh(
        state = swipeRefreshState,
        onRefresh = {
            isRefreshing = true
            viewModel.refreshLibraryTab()
        },
        indicator = { state, trigger ->
            SwipeRefreshIndicator(
                state = state,
                refreshTriggerDistance = trigger,
                backgroundColor = SapphoSurfaceLight,
                contentColor = SapphoInfo
            )
        },
        modifier = Modifier.fillMaxSize()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
        AdminSectionCard(title = "Library Actions") {
            ActionButton(
                text = "Scan Library",
                description = "Scan for new audiobooks",
                icon = Icons.Outlined.Search,
                onClick = { viewModel.scanLibrary() }
            )
            ActionButton(
                text = "Refresh Library",
                description = "Re-import all audiobooks (preserves progress)",
                icon = Icons.Outlined.Refresh,
                onClick = { viewModel.forceRescan() }
            )
        }

        if (isLoading) {
            Box(
                modifier = Modifier.fillMaxWidth().padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = SapphoInfo, modifier = Modifier.size(32.dp))
            }
        } else {
            serverSettings?.let { response ->
                val settings = response.settings
                val lockedFields = response.lockedFields ?: emptyList()

                AdminSectionCard(
                    title = "Library Paths",
                    icon = Icons.Outlined.Folder,
                    action = {
                        IconButton(onClick = { showEditDialog = true }) {
                            Icon(Icons.Outlined.Edit, contentDescription = "Edit", tint = SapphoInfo)
                        }
                    }
                ) {
                    InfoRow(
                        label = "Audiobooks Directory",
                        value = settings.audiobooksDir ?: "Not set",
                        locked = "audiobooksDir" in lockedFields
                    )
                    InfoRow(
                        label = "Upload Directory",
                        value = settings.uploadDir ?: "Not set",
                        locked = "uploadDir" in lockedFields
                    )
                    InfoRow(
                        label = "Scan Interval",
                        value = settings.libraryScanInterval?.let { "$it minutes" } ?: "Not set",
                        locked = "libraryScanInterval" in lockedFields
                    )
                }
            }
        }

        // Duplicates section
        if (duplicates.isNotEmpty()) {
            AdminSectionCard(title = "Duplicates (${duplicates.size} groups)", icon = Icons.Outlined.ContentCopy) {
                duplicates.forEach { group ->
                    DuplicateGroupCard(
                        group = group,
                        onClick = { selectedDuplicateGroup = group }
                    )
                }
            }
        }

        // Jobs section
        if (jobs.isNotEmpty()) {
            AdminSectionCard(title = "Scheduled Jobs", icon = Icons.Outlined.Schedule) {
                jobs.forEach { job ->
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        shape = RoundedCornerShape(8.dp),
                        color = SapphoProgressTrack.copy(alpha = 0.5f)
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(job.name, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                                    job.description?.let { desc ->
                                        Text(desc, color = SapphoIconDefault, fontSize = 12.sp)
                                    }
                                }
                                if (job.canTrigger == true) {
                                    Button(
                                        onClick = { viewModel.triggerJob(job.id) },
                                        colors = ButtonDefaults.buttonColors(containerColor = SapphoInfo),
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                        modifier = Modifier.height(32.dp)
                                    ) {
                                        Icon(
                                            Icons.Outlined.PlayArrow,
                                            contentDescription = "Trigger",
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Run", fontSize = 12.sp)
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                val statusColor = when (job.status.lowercase()) {
                                    "running" -> SapphoSuccess
                                    "scheduled" -> SapphoInfo
                                    "idle" -> SapphoIconDefault
                                    else -> SapphoIconDefault
                                }
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = statusColor.copy(alpha = 0.2f)
                                ) {
                                    Text(
                                        job.status.uppercase(),
                                        color = statusColor,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                                job.interval?.let {
                                    Text("Interval: $it", color = SapphoTextMuted, fontSize = 11.sp)
                                }
                            }
                            job.lastRun?.let { lastRun ->
                                Spacer(modifier = Modifier.height(4.dp))
                                Text("Last run: ${formatBackupDate(lastRun)}", color = SapphoTextMuted, fontSize = 11.sp)
                            }
                            job.lastResult?.let { result ->
                                Text("Result: $result", color = SapphoIconDefault, fontSize = 11.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            }
                            job.nextRun?.let { nextRun ->
                                Text("Next: ${formatBackupDate(nextRun)}", color = SapphoInfo, fontSize = 11.sp)
                            }
                        }
                    }
                }
            }
        }
        }
    }

    // Edit Dialog
    if (showEditDialog) {
        serverSettings?.let { response ->
            EditLibrarySettingsDialog(
                settings = response.settings,
                lockedFields = response.lockedFields ?: emptyList(),
                onDismiss = { showEditDialog = false },
                onSave = { update ->
                    viewModel.updateServerSettings(update)
                    showEditDialog = false
                }
            )
        }
    }

    // Duplicate Merge Dialog
    selectedDuplicateGroup?.let { group ->
        DuplicateMergeDialog(
            group = group,
            onDismiss = { selectedDuplicateGroup = null },
            onMerge = { keepId, deleteIds ->
                viewModel.mergeDuplicates(keepId, deleteIds)
                selectedDuplicateGroup = null
            }
        )
    }
}

@Composable
private fun EditLibrarySettingsDialog(
    settings: ServerSettings,
    lockedFields: List<String>,
    onDismiss: () -> Unit,
    onSave: (ServerSettingsUpdate) -> Unit
) {
    var audiobooksDir by remember { mutableStateOf(settings.audiobooksDir ?: "") }
    var uploadDir by remember { mutableStateOf(settings.uploadDir ?: "") }
    var scanInterval by remember { mutableStateOf(settings.libraryScanInterval?.toString() ?: "5") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit Library Settings", color = Color.White) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if ("audiobooksDir" !in lockedFields) {
                    OutlinedTextField(
                        value = audiobooksDir,
                        onValueChange = { audiobooksDir = it },
                        label = { Text("Audiobooks Directory") },
                        modifier = Modifier.fillMaxWidth(),
                        colors = adminTextFieldColors()
                    )
                } else {
                    LockedField("Audiobooks Directory", settings.audiobooksDir ?: "")
                }

                if ("uploadDir" !in lockedFields) {
                    OutlinedTextField(
                        value = uploadDir,
                        onValueChange = { uploadDir = it },
                        label = { Text("Upload Directory") },
                        modifier = Modifier.fillMaxWidth(),
                        colors = adminTextFieldColors()
                    )
                } else {
                    LockedField("Upload Directory", settings.uploadDir ?: "")
                }

                if ("libraryScanInterval" !in lockedFields) {
                    OutlinedTextField(
                        value = scanInterval,
                        onValueChange = { scanInterval = it },
                        label = { Text("Scan Interval (minutes)") },
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        colors = adminTextFieldColors()
                    )
                } else {
                    LockedField("Scan Interval", "${settings.libraryScanInterval ?: 5} minutes")
                }

                if (lockedFields.isNotEmpty()) {
                    Text(
                        "Locked fields are set via docker-compose and cannot be changed here.",
                        color = SapphoIconDefault,
                        fontSize = 12.sp
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(ServerSettingsUpdate(
                        audiobooksDir = if ("audiobooksDir" !in lockedFields) audiobooksDir else null,
                        uploadDir = if ("uploadDir" !in lockedFields) uploadDir else null,
                        libraryScanInterval = if ("libraryScanInterval" !in lockedFields) scanInterval.toIntOrNull() else null
                    ))
                },
                colors = ButtonDefaults.buttonColors(containerColor = SapphoInfo)
            ) {
                Text("Save")
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

@Composable
private fun LockedField(label: String, value: String) {
    Column {
        Text(label, color = SapphoIconDefault, fontSize = 12.sp)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(Icons.Outlined.Lock, contentDescription = "Locked", tint = SapphoTextMuted, modifier = Modifier.size(14.dp))
            Text(value, color = SapphoTextMuted, fontSize = 14.sp)
        }
    }
}

// ============ Server Settings Tab ============
@Composable
private fun ServerSettingsTab(viewModel: AdminViewModel) {
    val serverSettings by viewModel.serverSettings.collectAsState()
    val loadingSection by viewModel.loadingSection.collectAsState()
    val isLoading = loadingSection == "serverSettings"
    var showEditDialog by remember { mutableStateOf(false) }

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
        if (isLoading) {
            Box(
                modifier = Modifier.fillMaxWidth().padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = SapphoInfo, modifier = Modifier.size(32.dp))
            }
        } else {
            serverSettings?.let { response ->
                val settings = response.settings
                val lockedFields = response.lockedFields ?: emptyList()

                AdminSectionCard(
                    title = "Server Configuration",
                    icon = Icons.Outlined.Settings,
                    action = {
                        IconButton(onClick = { showEditDialog = true }) {
                            Icon(Icons.Outlined.Edit, contentDescription = "Edit", tint = SapphoInfo)
                        }
                    }
                ) {
                    InfoRow(label = "Port", value = settings.port ?: "3000", locked = "port" in lockedFields)
                    InfoRow(label = "Environment", value = settings.nodeEnv ?: "production", locked = "nodeEnv" in lockedFields)
                    InfoRow(label = "Database Path", value = settings.databasePath ?: "Not set", locked = "databasePath" in lockedFields)
                    InfoRow(label = "Data Directory", value = settings.dataDir ?: "Not set", locked = "dataDir" in lockedFields)
                }
            } ?: run {
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Text("No server settings available", color = SapphoIconDefault)
                }
            }
        }
    }

    // Edit Dialog
    if (showEditDialog) {
        serverSettings?.let { response ->
            EditServerSettingsDialog(
                settings = response.settings,
                lockedFields = response.lockedFields ?: emptyList(),
                onDismiss = { showEditDialog = false },
                onSave = { update ->
                    viewModel.updateServerSettings(update)
                    showEditDialog = false
                }
            )
        }
    }
}

@Composable
private fun EditServerSettingsDialog(
    settings: ServerSettings,
    lockedFields: List<String>,
    onDismiss: () -> Unit,
    onSave: (ServerSettingsUpdate) -> Unit
) {
    var port by remember { mutableStateOf(settings.port ?: "3001") }
    var nodeEnv by remember { mutableStateOf(settings.nodeEnv ?: "production") }
    var databasePath by remember { mutableStateOf(settings.databasePath ?: "") }
    var dataDir by remember { mutableStateOf(settings.dataDir ?: "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit Server Settings", color = Color.White) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if ("port" !in lockedFields) {
                    OutlinedTextField(
                        value = port,
                        onValueChange = { port = it },
                        label = { Text("Port") },
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        colors = adminTextFieldColors()
                    )
                } else {
                    LockedField("Port", settings.port ?: "3001")
                }

                if ("nodeEnv" !in lockedFields) {
                    Text("Environment", color = SapphoIconDefault, fontSize = 12.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("development", "production").forEach { env ->
                            Surface(
                                modifier = Modifier.clickable { nodeEnv = env },
                                shape = RoundedCornerShape(8.dp),
                                color = if (nodeEnv == env) SapphoInfo else SapphoProgressTrack
                            ) {
                                Text(
                                    env.replaceFirstChar { it.uppercase() },
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                    color = Color.White,
                                    fontSize = 14.sp
                                )
                            }
                        }
                    }
                } else {
                    LockedField("Environment", settings.nodeEnv ?: "production")
                }

                if ("databasePath" !in lockedFields) {
                    OutlinedTextField(
                        value = databasePath,
                        onValueChange = { databasePath = it },
                        label = { Text("Database Path") },
                        modifier = Modifier.fillMaxWidth(),
                        colors = adminTextFieldColors()
                    )
                } else {
                    LockedField("Database Path", settings.databasePath ?: "")
                }

                if ("dataDir" !in lockedFields) {
                    OutlinedTextField(
                        value = dataDir,
                        onValueChange = { dataDir = it },
                        label = { Text("Data Directory") },
                        modifier = Modifier.fillMaxWidth(),
                        colors = adminTextFieldColors()
                    )
                } else {
                    LockedField("Data Directory", settings.dataDir ?: "")
                }

                if (lockedFields.isNotEmpty()) {
                    Text(
                        "Locked fields are set via docker-compose and cannot be changed here.",
                        color = SapphoIconDefault,
                        fontSize = 12.sp
                    )
                }

                Text(
                    "Note: Some changes require a server restart to take effect.",
                    color = SapphoWarning,
                    fontSize = 12.sp
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(ServerSettingsUpdate(
                        port = if ("port" !in lockedFields) port else null,
                        nodeEnv = if ("nodeEnv" !in lockedFields) nodeEnv else null,
                        databasePath = if ("databasePath" !in lockedFields) databasePath else null,
                        dataDir = if ("dataDir" !in lockedFields) dataDir else null
                    ))
                },
                colors = ButtonDefaults.buttonColors(containerColor = SapphoInfo)
            ) {
                Text("Save")
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

// ============ AI Settings Tab ============
@Composable
private fun AiSettingsTab(viewModel: AdminViewModel) {
    val aiSettings by viewModel.aiSettings.collectAsState()
    val loadingSection by viewModel.loadingSection.collectAsState()
    val isLoading = loadingSection == "aiSettings"
    var showProviderDialog by remember { mutableStateOf(false) }
    var showRecapDialog by remember { mutableStateOf(false) }
    var testResult by remember { mutableStateOf<AiTestResponse?>(null) }
    // Track if initial load is done (separate from loading indicator)
    var hasLoaded by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.loadAiSettings()
    }

    // Mark as loaded once loading completes
    LaunchedEffect(isLoading) {
        if (!isLoading && !hasLoaded) {
            hasLoaded = true
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        val settings = aiSettings
        if (settings != null) {
            // AI Provider settings with its own edit button
            AdminSectionCard(
                title = "AI Provider",
                icon = Icons.Outlined.Psychology,
                action = {
                    IconButton(onClick = { showProviderDialog = true }) {
                        Icon(Icons.Outlined.Edit, contentDescription = "Edit", tint = SapphoInfo)
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

            // Recap Settings with its own edit button
            AdminSectionCard(
                title = "Recap Settings",
                icon = Icons.Outlined.AutoStories,
                action = {
                    IconButton(onClick = { showRecapDialog = true }) {
                        Icon(Icons.Outlined.Edit, contentDescription = "Edit", tint = SapphoInfo)
                    }
                }
            ) {
                InfoRow(
                    label = "Offensive Mode",
                    value = if (settings.recapOffensiveMode == true) "Enabled" else "Disabled"
                )
                if (settings.recapCustomPrompt != null) {
                    Text(
                        "Custom Prompt:",
                        color = SapphoIconDefault,
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
                colors = ButtonDefaults.buttonColors(containerColor = SapphoProgressTrack)
            ) {
                Icon(Icons.Outlined.Science, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Test AI Connection")
            }

            testResult?.let { result ->
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    color = if (result.error == null) SapphoSuccess.copy(alpha = 0.2f)
                    else SapphoError.copy(alpha = 0.2f)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = if (result.error == null) "Test Successful" else "Test Failed",
                            color = if (result.error == null) SapphoSuccess else SapphoError,
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
        } else if (isLoading) {
            // Still loading
            Box(
                modifier = Modifier.fillMaxWidth().padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = SapphoInfo, modifier = Modifier.size(32.dp))
            }
        } else if (hasLoaded) {
            // Loaded but no settings - show configuration option
            AdminSectionCard(
                title = "AI Not Configured",
                icon = Icons.Outlined.Psychology
            ) {
                Text(
                    "AI features are not configured. Configure AI to enable recap generation and other AI-powered features.",
                    color = SapphoIconDefault,
                    fontSize = 14.sp
                )
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = { showProviderDialog = true },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = SapphoInfo)
                ) {
                    Icon(Icons.Outlined.Add, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Configure AI")
                }
            }
        }
    }

    // AI Provider Edit Dialog
    if (showProviderDialog) {
        val settings = aiSettings ?: AiSettings(
            aiProvider = null,
            openaiApiKey = null,
            openaiModel = "gpt-3.5-turbo",
            geminiApiKey = null,
            geminiModel = "gemini-pro",
            recapCustomPrompt = null,
            recapOffensiveMode = false,
            recapDefaultPrompt = null
        )
        EditAiProviderDialog(
            settings = settings,
            onDismiss = { showProviderDialog = false },
            onSave = { update ->
                viewModel.updateAiSettings(update)
                showProviderDialog = false
            }
        )
    }

    // Recap Settings Edit Dialog
    if (showRecapDialog) {
        val settings = aiSettings ?: AiSettings(
            aiProvider = null,
            openaiApiKey = null,
            openaiModel = "gpt-3.5-turbo",
            geminiApiKey = null,
            geminiModel = "gemini-pro",
            recapCustomPrompt = null,
            recapOffensiveMode = false,
            recapDefaultPrompt = null
        )
        EditRecapSettingsDialog(
            settings = settings,
            onDismiss = { showRecapDialog = false },
            onSave = { update ->
                viewModel.updateAiSettings(update)
                showRecapDialog = false
            }
        )
    }
}

@Composable
private fun EditAiProviderDialog(
    settings: AiSettings,
    onDismiss: () -> Unit,
    onSave: (AiSettingsUpdate) -> Unit
) {
    var provider by remember { mutableStateOf(settings.aiProvider ?: "") }
    var openaiApiKey by remember { mutableStateOf(settings.openaiApiKey ?: "") }
    var openaiModel by remember { mutableStateOf(settings.openaiModel ?: "gpt-3.5-turbo") }
    var geminiApiKey by remember { mutableStateOf(settings.geminiApiKey ?: "") }
    var geminiModel by remember { mutableStateOf(settings.geminiModel ?: "gemini-pro") }
    var showApiKey by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit AI Provider", color = Color.White) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("AI Provider", color = SapphoIconDefault, fontSize = 12.sp)
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
                            color = if (provider == p) SapphoInfo else SapphoProgressTrack
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
                                    tint = SapphoIconDefault
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
                                    tint = SapphoIconDefault
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
                        recapCustomPrompt = settings.recapCustomPrompt,
                        recapOffensiveMode = settings.recapOffensiveMode
                    ))
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

@Composable
private fun EditRecapSettingsDialog(
    settings: AiSettings,
    onDismiss: () -> Unit,
    onSave: (AiSettingsUpdate) -> Unit
) {
    var customPrompt by remember { mutableStateOf(settings.recapCustomPrompt ?: "") }
    var offensiveMode by remember { mutableStateOf(settings.recapOffensiveMode ?: false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit Recap Settings", color = Color.White) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Checkbox(
                        checked = offensiveMode,
                        onCheckedChange = { offensiveMode = it },
                        colors = CheckboxDefaults.colors(
                            checkedColor = SapphoInfo,
                            uncheckedColor = SapphoTextMuted
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
                        aiProvider = settings.aiProvider,
                        openaiApiKey = settings.openaiApiKey,
                        openaiModel = settings.openaiModel,
                        geminiApiKey = settings.geminiApiKey,
                        geminiModel = settings.geminiModel,
                        recapCustomPrompt = customPrompt.ifBlank { null },
                        recapOffensiveMode = offensiveMode
                    ))
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

// ============ Users Tab ============
@Composable
private fun UsersTab(viewModel: AdminViewModel) {
    val users by viewModel.users.collectAsState()
    val loadingSection by viewModel.loadingSection.collectAsState()
    var showCreateDialog by remember { mutableStateOf(false) }
    var editingUser by remember { mutableStateOf<UserInfo?>(null) }
    var userToDelete by remember { mutableStateOf<UserInfo?>(null) }
    var isRefreshing by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.loadUsers()
    }

    LaunchedEffect(loadingSection) {
        if (loadingSection != "users") isRefreshing = false
    }

    val swipeRefreshState = rememberSwipeRefreshState(isRefreshing)

    SwipeRefresh(
        state = swipeRefreshState,
        onRefresh = {
            isRefreshing = true
            viewModel.refreshUsers()
        },
        indicator = { state, trigger ->
            SwipeRefreshIndicator(
                state = state,
                refreshTriggerDistance = trigger,
                backgroundColor = SapphoSurfaceLight,
                contentColor = SapphoInfo
            )
        },
        modifier = Modifier.fillMaxSize()
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
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
                        colors = ButtonDefaults.buttonColors(containerColor = SapphoInfo)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Add User")
                    }
                }
            }

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
            text = { Text("Are you sure you want to delete ${user.username}?", color = SapphoIconDefault) },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteUser(user.id)
                        userToDelete = null
                    }
                ) {
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
private fun UserCard(
    user: UserInfo,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = SapphoSurfaceLight
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
                            color = SapphoSuccess.copy(alpha = 0.2f)
                        ) {
                            Text(
                                "Admin",
                                color = SapphoSuccess,
                                fontSize = 11.sp,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
                user.email?.let { email ->
                    Text(
                        text = email,
                        color = SapphoIconDefault,
                        fontSize = 13.sp
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                IconButton(onClick = onEdit) {
                    Icon(Icons.Outlined.Edit, contentDescription = "Edit", tint = SapphoInfo)
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Outlined.Delete, contentDescription = "Delete", tint = SapphoError)
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
                                tint = SapphoIconDefault
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
                            checkedColor = SapphoInfo,
                            uncheckedColor = SapphoTextMuted
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
                Text("Create", color = SapphoInfo)
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
                                tint = SapphoIconDefault
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
                            checkedColor = SapphoInfo,
                            uncheckedColor = SapphoTextMuted
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
                Text("Update", color = SapphoInfo)
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

// ============ API Keys Tab ============
@Composable
private fun ApiKeysTab(viewModel: AdminViewModel) {
    val apiKeys by viewModel.apiKeys.collectAsState()
    val loadingSection by viewModel.loadingSection.collectAsState()
    var showCreateDialog by remember { mutableStateOf(false) }
    var keyToDelete by remember { mutableStateOf<ApiKey?>(null) }
    var newlyCreatedKey by remember { mutableStateOf<CreateApiKeyResponse?>(null) }
    var isRefreshing by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.loadApiKeys()
    }

    LaunchedEffect(loadingSection) {
        if (loadingSection != "apiKeys") isRefreshing = false
    }

    val swipeRefreshState = rememberSwipeRefreshState(isRefreshing)

    SwipeRefresh(
        state = swipeRefreshState,
        onRefresh = {
            isRefreshing = true
            viewModel.refreshApiKeys()
        },
        indicator = { state, trigger ->
            SwipeRefreshIndicator(
                state = state,
                refreshTriggerDistance = trigger,
                backgroundColor = SapphoSurfaceLight,
                contentColor = SapphoInfo
            )
        },
        modifier = Modifier.fillMaxSize()
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "API Key Management",
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Button(
                        onClick = { showCreateDialog = true },
                        colors = ButtonDefaults.buttonColors(containerColor = SapphoInfo)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Create Key")
                    }
                }
            }

            item {
                Text(
                    "API keys allow external applications to access your library. " +
                    "Keep your keys secure and revoke any that are compromised.",
                    color = SapphoIconDefault,
                    fontSize = 13.sp
                )
            }

            if (loadingSection == "apiKeys" && apiKeys.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = SapphoInfo, modifier = Modifier.size(32.dp))
                    }
                }
            } else if (apiKeys.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Outlined.Key,
                                contentDescription = null,
                                tint = SapphoTextMuted,
                                modifier = Modifier.size(48.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                "No API keys yet",
                                color = SapphoTextMuted,
                                fontSize = 14.sp
                            )
                        }
                    }
                }
            } else {
                items(apiKeys) { apiKey ->
                    ApiKeyCard(
                        apiKey = apiKey,
                        onToggleActive = { viewModel.toggleApiKeyActive(apiKey) },
                        onDelete = { keyToDelete = apiKey }
                    )
                }
            }
        }
    }

    // Create API Key Dialog
    if (showCreateDialog) {
        CreateApiKeyDialog(
            onDismiss = { showCreateDialog = false },
            onCreate = { name, permissions, expiresInDays ->
                viewModel.createApiKey(name, permissions, expiresInDays) { response ->
                    showCreateDialog = false
                    newlyCreatedKey = response
                }
            }
        )
    }

    // Show newly created key dialog (only time full key is visible)
    newlyCreatedKey?.let { response ->
        NewApiKeyDialog(
            response = response,
            onDismiss = { newlyCreatedKey = null }
        )
    }

    // Delete Confirmation Dialog
    keyToDelete?.let { apiKey ->
        AlertDialog(
            onDismissRequest = { keyToDelete = null },
            title = { Text("Delete API Key", color = Color.White) },
            text = { Text("Are you sure you want to delete '${apiKey.name}'? This action cannot be undone.", color = SapphoIconDefault) },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteApiKey(apiKey.id)
                        keyToDelete = null
                    }
                ) {
                    Text("Delete", color = SapphoError)
                }
            },
            dismissButton = {
                TextButton(onClick = { keyToDelete = null }) {
                    Text("Cancel", color = SapphoIconDefault)
                }
            },
            containerColor = SapphoSurfaceLight
        )
    }
}

@Composable
private fun ApiKeyCard(
    apiKey: ApiKey,
    onToggleActive: () -> Unit,
    onDelete: () -> Unit
) {
    val isActive = apiKey.isActive == 1
    val dateFormat = remember { SimpleDateFormat("MMM d, yyyy", Locale.getDefault()) }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = SapphoSurfaceLight
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = apiKey.name,
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = if (isActive) SapphoSuccess.copy(alpha = 0.2f) else SapphoError.copy(alpha = 0.2f)
                    ) {
                        Text(
                            if (isActive) "Active" else "Inactive",
                            color = if (isActive) SapphoSuccess else SapphoError,
                            fontSize = 11.sp,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    IconButton(onClick = onToggleActive) {
                        Icon(
                            if (isActive) Icons.Outlined.ToggleOn else Icons.Outlined.ToggleOff,
                            contentDescription = if (isActive) "Deactivate" else "Activate",
                            tint = if (isActive) SapphoSuccess else SapphoTextMuted
                        )
                    }
                    IconButton(onClick = onDelete) {
                        Icon(Icons.Outlined.Delete, contentDescription = "Delete", tint = SapphoError)
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Column {
                    Text("Key", color = SapphoTextMuted, fontSize = 11.sp)
                    Text(
                        "${apiKey.keyPrefix}...",
                        color = SapphoIconDefault,
                        fontSize = 13.sp,
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                    )
                }
                Column {
                    Text("Permissions", color = SapphoTextMuted, fontSize = 11.sp)
                    Text(
                        apiKey.permissions.replaceFirstChar { it.uppercase() },
                        color = SapphoIconDefault,
                        fontSize = 13.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Column {
                    Text("Created", color = SapphoTextMuted, fontSize = 11.sp)
                    Text(
                        try {
                            dateFormat.format(SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault()).parse(apiKey.createdAt) ?: Date())
                        } catch (e: Exception) { apiKey.createdAt },
                        color = SapphoIconDefault,
                        fontSize = 13.sp
                    )
                }
                apiKey.lastUsedAt?.let { lastUsed ->
                    Column {
                        Text("Last Used", color = SapphoTextMuted, fontSize = 11.sp)
                        Text(
                            try {
                                dateFormat.format(SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault()).parse(lastUsed) ?: Date())
                            } catch (e: Exception) { lastUsed },
                            color = SapphoIconDefault,
                            fontSize = 13.sp
                        )
                    }
                }
                apiKey.expiresAt?.let { expires ->
                    Column {
                        Text("Expires", color = SapphoTextMuted, fontSize = 11.sp)
                        Text(
                            try {
                                dateFormat.format(SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault()).parse(expires) ?: Date())
                            } catch (e: Exception) { expires },
                            color = SapphoIconDefault,
                            fontSize = 13.sp
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CreateApiKeyDialog(
    onDismiss: () -> Unit,
    onCreate: (name: String, permissions: String, expiresInDays: Int?) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var permissions by remember { mutableStateOf("read") }
    var expiresInDays by remember { mutableStateOf("") }
    var expanded by remember { mutableStateOf(false) }

    val permissionOptions = listOf("read", "write", "admin")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Create API Key", color = Color.White) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Key Name") },
                    placeholder = { Text("e.g., Android App") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = SapphoInfo,
                        unfocusedBorderColor = SapphoProgressTrack
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                ExposedDropdownMenuBox(
                    expanded = expanded,
                    onExpandedChange = { expanded = !expanded }
                ) {
                    OutlinedTextField(
                        value = permissions.replaceFirstChar { it.uppercase() },
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Permissions") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = SapphoInfo,
                            unfocusedBorderColor = SapphoProgressTrack
                        ),
                        modifier = Modifier.fillMaxWidth().menuAnchor()
                    )
                    ExposedDropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false },
                        modifier = Modifier.background(SapphoProgressTrack)
                    ) {
                        permissionOptions.forEach { option ->
                            DropdownMenuItem(
                                text = { Text(option.replaceFirstChar { it.uppercase() }, color = Color.White) },
                                onClick = {
                                    permissions = option
                                    expanded = false
                                }
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = expiresInDays,
                    onValueChange = { expiresInDays = it.filter { c -> c.isDigit() } },
                    label = { Text("Expires In (days)") },
                    placeholder = { Text("Leave empty for no expiration") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = SapphoInfo,
                        unfocusedBorderColor = SapphoProgressTrack
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (name.isNotBlank()) {
                        onCreate(name, permissions, expiresInDays.toIntOrNull())
                    }
                },
                enabled = name.isNotBlank()
            ) {
                Text("Create", color = SapphoInfo)
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

@Composable
private fun NewApiKeyDialog(
    response: CreateApiKeyResponse,
    onDismiss: () -> Unit
) {
    var copied by remember { mutableStateOf(false) }
    val clipboardManager = androidx.compose.ui.platform.LocalClipboardManager.current

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Default.Check, contentDescription = null, tint = SapphoSuccess)
                Text("API Key Created", color = Color.White)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(
                    "Your new API key has been created. Copy it now - you won't be able to see it again!",
                    color = SapphoStarFilled,
                    fontSize = 13.sp
                )

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = SapphoBackground
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            response.key,
                            color = Color.White,
                            fontSize = 12.sp,
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(
                            onClick = {
                                clipboardManager.setText(androidx.compose.ui.text.AnnotatedString(response.key))
                                copied = true
                            }
                        ) {
                            Icon(
                                if (copied) Icons.Default.Check else Icons.Default.ContentCopy,
                                contentDescription = "Copy",
                                tint = if (copied) SapphoSuccess else SapphoInfo
                            )
                        }
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Name: ${response.name}", color = SapphoIconDefault, fontSize = 12.sp)
                    Text("Permissions: ${response.permissions.replaceFirstChar { it.uppercase() }}", color = SapphoIconDefault, fontSize = 12.sp)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Done", color = SapphoInfo)
            }
        },
        containerColor = SapphoSurfaceLight
    )
}

// ============ Backup Tab ============
@Composable
private fun BackupTab(viewModel: AdminViewModel) {
    val backups by viewModel.backups.collectAsState()
    val loadingSection by viewModel.loadingSection.collectAsState()
    var backupToDelete by remember { mutableStateOf<BackupInfo?>(null) }
    var backupToRestore by remember { mutableStateOf<BackupInfo?>(null) }
    var isRefreshing by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.loadBackups()
    }

    LaunchedEffect(loadingSection) {
        if (loadingSection != "backups") isRefreshing = false
    }

    val swipeRefreshState = rememberSwipeRefreshState(isRefreshing)

    SwipeRefresh(
        state = swipeRefreshState,
        onRefresh = {
            isRefreshing = true
            viewModel.refreshBackups()
        },
        indicator = { state, trigger ->
            SwipeRefreshIndicator(
                state = state,
                refreshTriggerDistance = trigger,
                backgroundColor = SapphoSurfaceLight,
                contentColor = SapphoInfo
            )
        },
        modifier = Modifier.fillMaxSize()
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
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
                        colors = ButtonDefaults.buttonColors(containerColor = SapphoInfo)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Create Backup")
                    }
                }
            }

            if (backups.isEmpty()) {
                item {
                    Text(
                        "No backups found",
                        color = SapphoIconDefault,
                        fontSize = 14.sp,
                        modifier = Modifier.padding(vertical = 16.dp)
                    )
                }
            } else {
                items(backups) { backup ->
                    BackupCard(
                        backup = backup,
                        onRestore = { backupToRestore = backup },
                        onDelete = { backupToDelete = backup }
                    )
                }
            }
        }
    }

    // Delete Confirmation
    backupToDelete?.let { backup ->
        AlertDialog(
            onDismissRequest = { backupToDelete = null },
            title = { Text("Delete Backup", color = Color.White) },
            text = { Text("Are you sure you want to delete ${backup.filename}?", color = SapphoIconDefault) },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteBackup(backup.filename)
                        backupToDelete = null
                    }
                ) {
                    Text("Delete", color = SapphoError)
                }
            },
            dismissButton = {
                TextButton(onClick = { backupToDelete = null }) {
                    Text("Cancel", color = SapphoIconDefault)
                }
            },
            containerColor = SapphoSurfaceLight
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
                    color = SapphoIconDefault
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.restoreBackup(backup.filename)
                        backupToRestore = null
                    }
                ) {
                    Text("Restore", color = SapphoWarning)
                }
            },
            dismissButton = {
                TextButton(onClick = { backupToRestore = null }) {
                    Text("Cancel", color = SapphoIconDefault)
                }
            },
            containerColor = SapphoSurfaceLight
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
        color = SapphoSurfaceLight
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
                        color = SapphoIconDefault,
                        fontSize = 12.sp
                    )
                    Text(
                        text = backup.createdFormatted ?: backup.created ?: "Unknown",
                        color = SapphoIconDefault,
                        fontSize = 12.sp
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                IconButton(onClick = onRestore) {
                    Icon(Icons.Outlined.Restore, contentDescription = "Restore", tint = SapphoWarning)
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Outlined.Delete, contentDescription = "Delete", tint = SapphoError)
                }
            }
        }
    }
}

@Composable
private fun DuplicateGroupCard(
    group: DuplicateGroup,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(8.dp),
        color = SapphoProgressTrack.copy(alpha = 0.5f)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = group.books.firstOrNull()?.title ?: "Unknown",
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = SapphoWarning.copy(alpha = 0.2f)
                    ) {
                        Text(
                            "${group.books.size} copies",
                            color = SapphoWarning,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                    group.matchReason?.let { reason ->
                        Text(
                            text = reason,
                            color = SapphoTextMuted,
                            fontSize = 12.sp
                        )
                    }
                }
            }
            Icon(
                Icons.Default.ChevronRight,
                contentDescription = "View",
                tint = SapphoTextMuted,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun DuplicateMergeDialog(
    group: DuplicateGroup,
    onDismiss: () -> Unit,
    onMerge: (keepId: Int, deleteIds: List<Int>) -> Unit
) {
    var selectedKeepId by remember { mutableStateOf(group.suggestedKeep ?: group.books.firstOrNull()?.id) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text("Merge Duplicates", color = Color.White)
                Text(
                    "Select the copy to keep. Others will be deleted.",
                    color = SapphoIconDefault,
                    fontSize = 12.sp
                )
            }
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                group.books.forEach { book ->
                    val isSelected = selectedKeepId == book.id
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectedKeepId = book.id },
                        shape = RoundedCornerShape(8.dp),
                        color = if (isSelected) SapphoInfo.copy(alpha = 0.2f) else SapphoProgressTrack,
                        border = if (isSelected) androidx.compose.foundation.BorderStroke(
                            1.dp,
                            SapphoInfo
                        ) else null
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = isSelected,
                                onClick = { selectedKeepId = book.id },
                                colors = RadioButtonDefaults.colors(
                                    selectedColor = SapphoInfo,
                                    unselectedColor = SapphoTextMuted
                                )
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = book.title,
                                    color = Color.White,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                                book.author?.let { author ->
                                    Text(
                                        text = "by $author",
                                        color = SapphoIconDefault,
                                        fontSize = 12.sp
                                    )
                                }
                                book.filePath?.let { path ->
                                    Text(
                                        text = path.substringAfterLast("/"),
                                        color = SapphoTextMuted,
                                        fontSize = 11.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                book.createdAt?.let { created ->
                                    Text(
                                        text = "Added: ${formatBackupDate(created)}",
                                        color = SapphoTextMuted,
                                        fontSize = 10.sp
                                    )
                                }
                            }
                            if (group.suggestedKeep == book.id) {
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = SapphoSuccess.copy(alpha = 0.2f)
                                ) {
                                    Text(
                                        "Suggested",
                                        color = SapphoSuccess,
                                        fontSize = 9.sp,
                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                // Warning
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    color = SapphoError.copy(alpha = 0.1f)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Outlined.Warning,
                            contentDescription = null,
                            tint = SapphoError,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            "This will permanently delete ${group.books.size - 1} duplicate(s). Files will also be removed from disk.",
                            color = SapphoError,
                            fontSize = 12.sp
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    selectedKeepId?.let { keepId ->
                        val deleteIds = group.books.filter { it.id != keepId }.map { it.id }
                        onMerge(keepId, deleteIds)
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = SapphoError),
                enabled = selectedKeepId != null
            ) {
                Text("Merge & Delete")
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

// ============ Logs Tab ============
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LogsTab(viewModel: AdminViewModel) {
    val logs by viewModel.logs.collectAsState()
    val loadingSection by viewModel.loadingSection.collectAsState()
    val isLoading = loadingSection == "logs"
    var selectedLevel by remember { mutableStateOf<String?>(null) }
    var autoRefresh by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var showClearConfirmation by remember { mutableStateOf(false) }
    var isRefreshing by remember { mutableStateOf(false) }

    // Initial load
    LaunchedEffect(Unit) {
        viewModel.loadLogs(level = selectedLevel)
    }

    // Filter change - force refresh
    LaunchedEffect(selectedLevel) {
        viewModel.refreshLogs(level = selectedLevel)
    }

    // Reset refreshing when loading completes
    LaunchedEffect(loadingSection) {
        if (loadingSection != "logs") isRefreshing = false
    }

    // Auto-refresh every 5 seconds when enabled
    LaunchedEffect(autoRefresh) {
        if (autoRefresh) {
            while (true) {
                kotlinx.coroutines.delay(5000)
                viewModel.refreshLogs(level = selectedLevel)
            }
        }
    }

    // Filter logs by search query
    val filteredLogs = remember(logs, searchQuery) {
        if (searchQuery.isBlank()) logs
        else logs.filter { log ->
            log.message.contains(searchQuery, ignoreCase = true) ||
            log.source?.contains(searchQuery, ignoreCase = true) == true
        }
    }

    val swipeRefreshState = rememberSwipeRefreshState(isRefreshing)

    SwipeRefresh(
        state = swipeRefreshState,
        onRefresh = {
            isRefreshing = true
            viewModel.refreshLogs(level = selectedLevel)
        },
        indicator = { state, trigger ->
            SwipeRefreshIndicator(
                state = state,
                refreshTriggerDistance = trigger,
                backgroundColor = SapphoSurfaceLight,
                contentColor = SapphoInfo
            )
        },
        modifier = Modifier.fillMaxSize()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
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
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Auto-refresh toggle
                Surface(
                    modifier = Modifier.clickable { autoRefresh = !autoRefresh },
                    shape = RoundedCornerShape(8.dp),
                    color = if (autoRefresh) SapphoSuccess.copy(alpha = 0.2f) else SapphoProgressTrack
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            Icons.Outlined.Autorenew,
                            contentDescription = "Auto-refresh",
                            tint = if (autoRefresh) SapphoSuccess else SapphoIconDefault,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            "Auto",
                            color = if (autoRefresh) SapphoSuccess else SapphoIconDefault,
                            fontSize = 12.sp
                        )
                    }
                }
                IconButton(onClick = { viewModel.refreshLogs(level = selectedLevel) }) {
                    Icon(Icons.Outlined.Refresh, contentDescription = "Refresh", tint = SapphoInfo)
                }
                IconButton(onClick = { showClearConfirmation = true }) {
                    Icon(Icons.Outlined.Delete, contentDescription = "Clear", tint = SapphoError)
                }
            }
        }

        // Search field
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text("Search logs...", color = SapphoTextMuted) },
            leadingIcon = {
                Icon(Icons.Outlined.Search, contentDescription = null, tint = SapphoTextMuted)
            },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { searchQuery = "" }) {
                        Icon(Icons.Default.Clear, contentDescription = "Clear", tint = SapphoTextMuted)
                    }
                }
            },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            colors = adminTextFieldColors(),
            shape = RoundedCornerShape(8.dp)
        )

        // Level filter chips
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(null to "All", "info" to "Info", "warn" to "Warn", "error" to "Error").forEach { (level, label) ->
                FilterChip(
                    selected = selectedLevel == level,
                    onClick = { selectedLevel = level },
                    label = { Text(label) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = SapphoInfo,
                        selectedLabelColor = Color.White,
                        containerColor = SapphoProgressTrack,
                        labelColor = SapphoIconDefault
                    )
                )
            }
        }

        // Search results count
        if (searchQuery.isNotEmpty()) {
            Text(
                "${filteredLogs.size} of ${logs.size} logs match",
                color = SapphoIconDefault,
                fontSize = 12.sp
            )
        }

        if (isLoading && logs.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxWidth().padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = SapphoInfo, modifier = Modifier.size(32.dp))
            }
        } else if (filteredLogs.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxWidth().padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    if (searchQuery.isNotEmpty()) "No logs match your search" else "No logs available",
                    color = SapphoIconDefault
                )
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(filteredLogs) { log ->
                    LogEntryCard(log)
                }
            }
        }
        }
    }

    // Clear Logs Confirmation Dialog
    if (showClearConfirmation) {
        AlertDialog(
            onDismissRequest = { showClearConfirmation = false },
            title = { Text("Clear Logs", color = Color.White) },
            text = { Text("Are you sure you want to clear all logs? This action cannot be undone.", color = SapphoIconDefault) },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.clearLogs()
                        showClearConfirmation = false
                    }
                ) {
                    Text("Clear", color = SapphoError)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirmation = false }) {
                    Text("Cancel", color = SapphoIconDefault)
                }
            },
            containerColor = SapphoSurfaceLight
        )
    }
}

@Composable
private fun LogEntryCard(log: LogEntry) {
    val levelColor = when (log.level.lowercase()) {
        "error" -> SapphoError
        "warn" -> SapphoWarning
        "info" -> SapphoInfo
        else -> SapphoIconDefault
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = SapphoSurfaceLight
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
                    color = SapphoTextMuted,
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
    val loadingSection by viewModel.loadingSection.collectAsState()
    val isLoading = loadingSection == "statistics"
    var isRefreshing by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.loadStatistics()
    }

    // Reset refreshing when loading completes
    LaunchedEffect(isLoading) {
        if (!isLoading) isRefreshing = false
    }

    val swipeRefreshState = rememberSwipeRefreshState(isRefreshing)

    SwipeRefresh(
        state = swipeRefreshState,
        onRefresh = {
            isRefreshing = true
            viewModel.refreshStatistics()
        },
        indicator = { state, trigger ->
            SwipeRefreshIndicator(
                state = state,
                refreshTriggerDistance = trigger,
                backgroundColor = SapphoSurfaceLight,
                contentColor = SapphoInfo
            )
        },
        modifier = Modifier.fillMaxSize()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (isLoading && !isRefreshing) {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = SapphoInfo, modifier = Modifier.size(32.dp))
                }
            } else {
            statistics?.let { stats ->
                // Totals row
                stats.totals?.let { totals ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        StatCard(
                            modifier = Modifier.weight(1f),
                            value = totals.books.toString(),
                            label = "Audiobooks",
                            icon = Icons.Outlined.LibraryBooks,
                            color = SapphoInfo
                        )
                        StatCard(
                            modifier = Modifier.weight(1f),
                            value = formatFileSize(totals.size),
                            label = "Total Size",
                            icon = Icons.Outlined.Storage,
                            color = LegacyPurple
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        StatCard(
                            modifier = Modifier.weight(1f),
                            value = formatDuration(totals.duration),
                            label = "Total Duration",
                            icon = Icons.Outlined.Timer,
                            color = LibraryGradients.pink[0]
                        )
                        StatCard(
                            modifier = Modifier.weight(1f),
                            value = formatDuration(totals.avgDuration?.toLong() ?: 0),
                            label = "Avg Duration",
                            icon = Icons.Outlined.AvTimer,
                            color = LibraryGradients.cyan[0]
                        )
                    }
                }

                // Top Authors
                stats.topAuthors?.let { authors ->
                    if (authors.isNotEmpty()) {
                        AdminSectionCard(title = "Top Authors", icon = Icons.Outlined.Person) {
                            authors.take(10).forEach { author ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        author.author ?: "Unknown",
                                        color = Color.White,
                                        fontSize = 14.sp,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Text(
                                        "${author.count} books",
                                        color = SapphoIconDefault,
                                        fontSize = 14.sp
                                    )
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                            }
                        }
                    }
                }

                // Top Series
                stats.topSeries?.let { series ->
                    if (series.isNotEmpty()) {
                        AdminSectionCard(title = "Top Series", icon = Icons.Outlined.Collections) {
                            series.take(10).forEach { s ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        s.series ?: "Unknown",
                                        color = Color.White,
                                        fontSize = 14.sp,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Text(
                                        "${s.count} books",
                                        color = SapphoIconDefault,
                                        fontSize = 14.sp
                                    )
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                            }
                        }
                    }
                }

                // Storage by Format
                stats.byFormat?.let { formats ->
                    if (formats.isNotEmpty()) {
                        AdminSectionCard(title = "Storage by Format", icon = Icons.Outlined.PieChart) {
                            formats.forEach { format ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        format.format?.uppercase() ?: "Unknown",
                                        color = Color.White,
                                        fontSize = 14.sp
                                    )
                                    Text(
                                        "${format.count} (${formatFileSize(format.size)})",
                                        color = SapphoIconDefault,
                                        fontSize = 14.sp
                                    )
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                            }
                        }
                    }
                }

                // User Stats
                stats.userStats?.let { users ->
                    if (users.isNotEmpty()) {
                        AdminSectionCard(title = "User Activity", icon = Icons.Outlined.People) {
                            users.forEach { user ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        user.username ?: "Unknown",
                                        color = Color.White,
                                        fontSize = 14.sp,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Column(horizontalAlignment = Alignment.End) {
                                        Text(
                                            "${user.booksCompleted ?: 0}/${user.booksStarted ?: 0} finished",
                                            color = SapphoIconDefault,
                                            fontSize = 12.sp
                                        )
                                        Text(
                                            formatDuration(user.totalListenTime ?: 0),
                                            color = SapphoIconDefault,
                                            fontSize = 12.sp
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                            }
                        }
                    }
                }
            } ?: run {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("No statistics available", color = SapphoIconDefault)
                }
            }
        }
        }
    }
}

// ============ Helper Components ============
@Composable
private fun AdminSectionCard(
    title: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    action: @Composable (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = SapphoSurfaceLight
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
                    icon?.let {
                        Icon(
                            imageVector = it,
                            contentDescription = null,
                            tint = SapphoInfo,
                            modifier = Modifier.size(20.dp)
                        )
                    }
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
private fun InfoRow(label: String, value: String, locked: Boolean = false) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = SapphoIconDefault, fontSize = 14.sp)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            if (locked) {
                Icon(
                    Icons.Outlined.Lock,
                    contentDescription = "Locked",
                    tint = SapphoTextMuted,
                    modifier = Modifier.size(12.dp)
                )
            }
            Text(
                value,
                color = if (locked) SapphoTextMuted else Color.White,
                fontSize = 14.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = 200.dp)
            )
        }
    }
}

@Composable
private fun ActionButton(
    text: String,
    description: String,
    icon: ImageVector,
    color: Color = SapphoInfo,
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
                Text(description, color = SapphoTextMuted, fontSize = 13.sp)
            }
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = SapphoTextMuted,
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
                color = Color.White
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = label,
                fontSize = 12.sp,
                color = SapphoIconDefault
            )
        }
    }
}

@Composable
private fun adminTextFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = Color.White,
    unfocusedTextColor = Color.White,
    focusedBorderColor = SapphoInfo,
    unfocusedBorderColor = SapphoProgressTrack,
    focusedLabelColor = SapphoInfo,
    unfocusedLabelColor = SapphoIconDefault,
    cursorColor = SapphoInfo
)

// ============ Utility Functions ============
private fun formatFileSize(bytes: Long): String {
    return when {
        bytes >= 1_073_741_824 -> String.format(java.util.Locale.US, "%.1f GB", bytes / 1_073_741_824.0)
        bytes >= 1_048_576 -> String.format(java.util.Locale.US, "%.1f MB", bytes / 1_048_576.0)
        bytes >= 1_024 -> String.format(java.util.Locale.US, "%.1f KB", bytes / 1_024.0)
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

// ============ Upload Dialog ============
@Composable
private fun UploadDialog(
    viewModel: AdminViewModel,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
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
                    color = SapphoIconDefault,
                    fontSize = 12.sp
                )

                // File picker button
                Button(
                    onClick = { filePickerLauncher.launch(arrayOf("audio/*")) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = SapphoInfo),
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
                        color = SapphoSuccess,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )

                    selectedFiles.take(5).forEach { uri ->
                        val fileName = uri.lastPathSegment?.substringAfterLast("/") ?: "Unknown file"
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Outlined.AudioFile, contentDescription = null, tint = SapphoIconDefault, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(fileName, color = SapphoTextLight, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    if (selectedFiles.size > 5) {
                        Text("...and ${selectedFiles.size - 5} more", color = SapphoTextMuted, fontSize = 11.sp)
                    }

                    TextButton(onClick = { selectedFiles = emptyList() }) {
                        Text("Clear selection", color = SapphoError, fontSize = 12.sp)
                    }

                    // Optional metadata
                    Text("Optional metadata (leave blank to auto-detect)", color = SapphoTextMuted, fontSize = 11.sp)

                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it },
                        label = { Text("Title") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        colors = adminTextFieldColors()
                    )

                    OutlinedTextField(
                        value = author,
                        onValueChange = { author = it },
                        label = { Text("Author") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        colors = adminTextFieldColors()
                    )

                    OutlinedTextField(
                        value = narrator,
                        onValueChange = { narrator = it },
                        label = { Text("Narrator") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        colors = adminTextFieldColors()
                    )
                }

                // Upload progress
                if (uploadState == UploadState.UPLOADING) {
                    LinearProgressIndicator(
                        progress = uploadProgress,
                        modifier = Modifier.fillMaxWidth(),
                        color = SapphoInfo
                    )
                    Text(
                        "Uploading... ${(uploadProgress * 100).toInt()}%",
                        color = SapphoIconDefault,
                        fontSize = 12.sp
                    )
                }

                // Upload result
                uploadResult?.let { result ->
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        color = if (result.success) SapphoSuccess.copy(alpha = 0.1f) else SapphoError.copy(alpha = 0.1f)
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                if (result.success) Icons.Outlined.CheckCircle else Icons.Outlined.Error,
                                contentDescription = null,
                                tint = if (result.success) SapphoSuccess else SapphoError,
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                result.message ?: if (result.success) "Upload complete" else "Upload failed",
                                color = if (result.success) SapphoSuccess else SapphoError,
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
                    colors = ButtonDefaults.buttonColors(containerColor = SapphoSuccess)
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
                    colors = ButtonDefaults.buttonColors(containerColor = SapphoInfo)
                ) {
                    Text("Done")
                }
            }
        },
        dismissButton = {
            if (uploadState != UploadState.UPLOADING && uploadResult == null) {
                TextButton(onClick = onDismiss) {
                    Text("Cancel", color = SapphoIconDefault)
                }
            }
        },
        containerColor = SapphoSurfaceLight
    )
}

enum class UploadState {
    IDLE,
    UPLOADING,
    SUCCESS,
    ERROR
}
