package com.sappho.audiobooks.presentation.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel

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

    // Library settings fields
    var libraryPath by remember(librarySettings) { mutableStateOf(librarySettings?.libraryPath ?: "") }
    var uploadPath by remember(librarySettings) { mutableStateOf(librarySettings?.uploadPath ?: "") }

    // Confirmation dialogs
    var showForceRescanDialog by remember { mutableStateOf(false) }

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
                        "Server Settings",
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
                // Library Settings Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1e293b))
                ) {
                    Column(modifier = Modifier.padding(24.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Folder,
                                contentDescription = null,
                                tint = Color(0xFF3b82f6),
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = "Library Settings",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Configure where your audiobooks are stored. The library is automatically scanned every 5 minutes.",
                            fontSize = 14.sp,
                            color = Color(0xFF9ca3af)
                        )

                        Spacer(modifier = Modifier.height(24.dp))

                        // Library Path
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
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 4.dp, top = 4.dp)
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        // Upload Path
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
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 4.dp, top = 4.dp)
                        )

                        Spacer(modifier = Modifier.height(24.dp))

                        // Save Settings Button
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
                            Text(if (isSaving) "Saving..." else "Save Settings")
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Library Management Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1e293b))
                ) {
                    Column(modifier = Modifier.padding(24.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.LibraryBooks,
                                contentDescription = null,
                                tint = Color(0xFF3b82f6),
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = "Library Management",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Manually trigger a library scan to import new audiobooks immediately or refresh metadata.",
                            fontSize = 14.sp,
                            color = Color(0xFF9ca3af)
                        )

                        Spacer(modifier = Modifier.height(24.dp))

                        // Scan Library Button
                        Button(
                            onClick = { viewModel.scanLibrary(refresh = true) },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !isScanning,
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF3b82f6),
                                contentColor = Color.White
                            )
                        ) {
                            if (isScanning) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    color = Color.White,
                                    strokeWidth = 2.dp
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                            } else {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                            }
                            Text(if (isScanning) "Scanning..." else "Scan Library Now")
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Danger Zone Card
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, Color(0xFFdc2626), RoundedCornerShape(16.dp)),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF7f1d1d))
                ) {
                    Column(modifier = Modifier.padding(24.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = null,
                                tint = Color(0xFFfca5a5),
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = "Danger Zone",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Force rescan will clear the entire library database and reimport all audiobooks. Use this if you have duplicate entries or corrupted data.",
                            fontSize = 14.sp,
                            color = Color(0xFFfca5a5)
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFF450a0a))
                                .border(1.dp, Color(0xFF991b1b), RoundedCornerShape(8.dp))
                                .padding(12.dp)
                        ) {
                            Text(
                                text = "Warning: This will delete all audiobook entries and playback progress. Your audio files will not be deleted.",
                                fontSize = 13.sp,
                                color = Color(0xFFfca5a5)
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Button(
                            onClick = { showForceRescanDialog = true },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !isScanning,
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFFdc2626),
                                contentColor = Color.White
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Default.DeleteForever,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Force Rescan Library")
                        }
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
}
