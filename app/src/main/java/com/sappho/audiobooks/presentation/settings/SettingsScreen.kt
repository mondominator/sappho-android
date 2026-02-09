package com.sappho.audiobooks.presentation.settings

import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
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
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.sappho.audiobooks.data.repository.UserPreferencesRepository

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBackClick: () -> Unit,
    viewModel: UserSettingsViewModel = hiltViewModel()
) {
    val isLoading by viewModel.isLoading.collectAsState()

    // Playback settings
    val skipForwardSeconds by viewModel.userPreferences.skipForwardSeconds.collectAsState()
    val skipBackwardSeconds by viewModel.userPreferences.skipBackwardSeconds.collectAsState()
    val defaultPlaybackSpeed by viewModel.userPreferences.defaultPlaybackSpeed.collectAsState()
    val rewindOnResumeSeconds by viewModel.userPreferences.rewindOnResumeSeconds.collectAsState()
    val defaultSleepTimerMinutes by viewModel.userPreferences.defaultSleepTimerMinutes.collectAsState()
    val bufferSizeSeconds by viewModel.userPreferences.bufferSizeSeconds.collectAsState()
    val showChapterProgress by viewModel.userPreferences.showChapterProgress.collectAsState()

    // Handle system back button
    BackHandler { onBackClick() }

    Scaffold(
        containerColor = SapphoBackground,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Playback Settings",
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
                    // Skip Forward Duration
                    SettingsDropdown(
                        label = "Skip Forward",
                        value = "${skipForwardSeconds}s",
                        options = UserPreferencesRepository.SKIP_FORWARD_OPTIONS.map { "${it}s" to it },
                        onOptionSelected = { viewModel.userPreferences.setSkipForwardSeconds(it) }
                    )

                    HorizontalDivider(color = SapphoProgressTrack, modifier = Modifier.padding(vertical = 8.dp))

                    // Skip Backward Duration
                    SettingsDropdown(
                        label = "Skip Backward",
                        value = "${skipBackwardSeconds}s",
                        options = UserPreferencesRepository.SKIP_BACKWARD_OPTIONS.map { "${it}s" to it },
                        onOptionSelected = { viewModel.userPreferences.setSkipBackwardSeconds(it) }
                    )

                    HorizontalDivider(color = SapphoProgressTrack, modifier = Modifier.padding(vertical = 8.dp))

                    // Default Playback Speed
                    SettingsDropdown(
                        label = "Default Speed",
                        value = "${defaultPlaybackSpeed}x",
                        options = UserPreferencesRepository.PLAYBACK_SPEED_OPTIONS.map { "${it}x" to it },
                        onOptionSelected = { viewModel.userPreferences.setDefaultPlaybackSpeed(it) }
                    )

                    HorizontalDivider(color = SapphoProgressTrack, modifier = Modifier.padding(vertical = 8.dp))

                    // Rewind on Resume
                    SettingsDropdown(
                        label = "Rewind on Resume",
                        value = if (rewindOnResumeSeconds == 0) "Off" else "${rewindOnResumeSeconds}s",
                        options = UserPreferencesRepository.REWIND_ON_RESUME_OPTIONS.map {
                            (if (it == 0) "Off" else "${it}s") to it
                        },
                        onOptionSelected = { viewModel.userPreferences.setRewindOnResumeSeconds(it) }
                    )

                    HorizontalDivider(color = SapphoProgressTrack, modifier = Modifier.padding(vertical = 8.dp))

                    // Default Sleep Timer
                    SettingsDropdown(
                        label = "Default Sleep Timer",
                        value = if (defaultSleepTimerMinutes == 0) "Off" else "${defaultSleepTimerMinutes} min",
                        options = UserPreferencesRepository.SLEEP_TIMER_OPTIONS.map {
                            (if (it == 0) "Off" else "$it min") to it
                        },
                        onOptionSelected = { viewModel.userPreferences.setDefaultSleepTimerMinutes(it) }
                    )

                    HorizontalDivider(color = SapphoProgressTrack, modifier = Modifier.padding(vertical = 8.dp))

                    // Buffer Size
                    SettingsDropdown(
                        label = "Buffer Size",
                        value = UserPreferencesRepository.formatBufferSize(bufferSizeSeconds),
                        options = UserPreferencesRepository.BUFFER_SIZE_OPTIONS.map {
                            UserPreferencesRepository.formatBufferSize(it) to it
                        },
                        onOptionSelected = { viewModel.userPreferences.setBufferSizeSeconds(it) }
                    )

                    HorizontalDivider(color = SapphoProgressTrack, modifier = Modifier.padding(vertical = 8.dp))

                    // Show Chapter Progress Toggle
                    SettingsToggle(
                        label = "Show Chapter Progress",
                        subtitle = "Display progress within chapter instead of whole book",
                        checked = showChapterProgress,
                        onCheckedChange = { viewModel.userPreferences.setShowChapterProgress(it) }
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))
            }
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
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White
                )
            }
            content()
        }
    }
}

@Composable
private fun <T> SettingsDropdown(
    label: String,
    value: String,
    options: List<Pair<String, T>>,
    onOptionSelected: (T) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { expanded = true }
            .padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            color = Color.White,
            style = MaterialTheme.typography.titleSmall
        )
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = value,
                color = SapphoInfo,
                style = MaterialTheme.typography.bodyMedium
            )
            Icon(
                imageVector = Icons.Default.ArrowDropDown,
                contentDescription = "Select $label",
                tint = SapphoIconDefault,
                modifier = Modifier.size(24.dp)
            )
        }
    }

    DropdownMenu(
        expanded = expanded,
        onDismissRequest = { expanded = false },
        modifier = Modifier.background(SapphoSurfaceLight)
    ) {
        options.forEach { (displayText, optionValue) ->
            DropdownMenuItem(
                text = {
                    Text(
                        displayText,
                        color = if (displayText == value) SapphoInfo else Color.White
                    )
                },
                onClick = {
                    onOptionSelected(optionValue)
                    expanded = false
                },
                leadingIcon = if (displayText == value) {
                    {
                        Icon(
                            Icons.Default.Check,
                            contentDescription = null,
                            tint = SapphoInfo,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                } else null
            )
        }
    }
}

@Composable
private fun SettingsToggle(
    label: String,
    subtitle: String? = null,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                color = Color.White,
                style = MaterialTheme.typography.titleSmall
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    color = SapphoTextMuted,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = SapphoInfo,
                uncheckedThumbColor = SapphoIconDefault,
                uncheckedTrackColor = SapphoProgressTrack
            )
        )
    }
}
