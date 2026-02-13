package com.sappho.audiobooks.cast.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cast
import androidx.compose.material.icons.filled.DesktopWindows
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Speaker
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sappho.audiobooks.cast.CastDevice
import com.sappho.audiobooks.cast.CastDeviceType
import com.sappho.audiobooks.cast.CastManager
import com.sappho.audiobooks.cast.CastProtocol
import com.sappho.audiobooks.presentation.theme.LegacyBlueLight
import com.sappho.audiobooks.presentation.theme.LegacyPurpleLight
import com.sappho.audiobooks.presentation.theme.SapphoError
import com.sappho.audiobooks.presentation.theme.SapphoIconDefault
import com.sappho.audiobooks.presentation.theme.SapphoInfo
import com.sappho.audiobooks.presentation.theme.SapphoSuccess
import com.sappho.audiobooks.presentation.theme.SapphoSurface
import com.sappho.audiobooks.presentation.theme.SapphoSurfaceLight
import com.sappho.audiobooks.presentation.theme.SapphoWarning
import com.sappho.audiobooks.presentation.theme.Timing

/**
 * Unified cast device picker dialog showing devices from all supported protocols.
 * Extracted from PlayerActivity's inline cast dialog and extended for multi-protocol support.
 */
@Composable
fun CastDialog(
    castManager: CastManager,
    onDeviceSelected: (CastDevice) -> Unit,
    onDisconnect: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val discoveredDevices by castManager.discoveredDevices.collectAsState()
    val isCastConnected by castManager.isConnected.collectAsState()
    val connectedDeviceName by castManager.connectedDeviceName.collectAsState()
    val activeProtocol by castManager.activeProtocol.collectAsState()

    var isScanning by remember { mutableStateOf(true) }

    // Start/stop discovery with dialog lifecycle
    DisposableEffect(Unit) {
        castManager.startDiscovery(context)
        onDispose {
            castManager.stopDiscovery()
        }
    }

    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(Timing.FEEDBACK_MEDIUM_MS)
        isScanning = false
    }

    // Pulsing animation for scanning
    val pulseTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by pulseTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )
    val pulseAlpha by pulseTransition.animateFloat(
        initialValue = 0.6f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.linearGradient(
                                colors = listOf(SapphoInfo, LegacyPurpleLight)
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Cast,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(22.dp)
                    )
                }
                Column {
                    Text(
                        "Cast Audio",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                    Text(
                        if (isCastConnected && connectedDeviceName != null)
                            "Connected to $connectedDeviceName"
                        else "Select a device",
                        color = if (isCastConnected) SapphoSuccess else SapphoIconDefault,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (isCastConnected) {
                    ConnectedState(
                        deviceName = connectedDeviceName,
                        protocol = activeProtocol,
                        onDisconnect = onDisconnect
                    )
                } else if (isScanning && discoveredDevices.isEmpty()) {
                    ScanningState(pulseScale, pulseAlpha)
                } else if (discoveredDevices.isEmpty()) {
                    NoDevicesState()
                } else {
                    DeviceList(
                        devices = discoveredDevices,
                        onDeviceSelected = onDeviceSelected
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.White.copy(alpha = 0.05f))
                    .padding(horizontal = 8.dp)
            ) {
                Text("Close", color = SapphoIconDefault)
            }
        },
        containerColor = SapphoSurfaceLight,
        shape = RoundedCornerShape(24.dp)
    )
}

@Composable
private fun ConnectedState(
    deviceName: String?,
    protocol: CastProtocol?,
    onDisconnect: () -> Unit
) {
    // Connected device card
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = SapphoSuccess.copy(alpha = 0.12f),
        border = BorderStroke(1.dp, SapphoSuccess.copy(alpha = 0.3f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(SapphoSuccess.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = iconForProtocol(protocol),
                    contentDescription = null,
                    tint = SapphoSuccess,
                    modifier = Modifier.size(24.dp)
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    deviceName ?: "Currently Casting",
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    labelForProtocol(protocol),
                    color = SapphoSuccess,
                    fontSize = 12.sp
                )
            }
        }
    }

    // Disconnect button
    Surface(
        onClick = onDisconnect,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = SapphoError.copy(alpha = 0.12f)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Cast,
                contentDescription = null,
                tint = SapphoError,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                "Disconnect",
                color = SapphoError,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun ScanningState(pulseScale: Float, pulseAlpha: Float) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            // Outer pulse ring
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .scale(pulseScale)
                    .clip(CircleShape)
                    .background(SapphoInfo.copy(alpha = 0.1f * pulseAlpha))
            )
            // Inner circle
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.linearGradient(
                            colors = listOf(
                                SapphoInfo.copy(alpha = 0.3f),
                                LegacyPurpleLight.copy(alpha = 0.3f)
                            )
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Cast,
                    contentDescription = null,
                    tint = SapphoInfo,
                    modifier = Modifier.size(28.dp)
                )
            }
        }
        Text(
            "Scanning for devices...",
            color = SapphoIconDefault,
            fontSize = 14.sp
        )
    }
}

@Composable
private fun NoDevicesState() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(SapphoSurface),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Cast,
                contentDescription = null,
                tint = SapphoIconDefault.copy(alpha = 0.5f),
                modifier = Modifier.size(28.dp)
            )
        }
        Text(
            "No devices found",
            color = SapphoIconDefault,
            fontWeight = FontWeight.Medium
        )
        Text(
            "Make sure your Cast, Roku, Kodi, or\nAirPlay device is on the same network",
            color = SapphoIconDefault.copy(alpha = 0.7f),
            fontSize = 12.sp,
            textAlign = TextAlign.Center,
            lineHeight = 18.sp
        )
    }
}

@Composable
private fun DeviceList(
    devices: List<CastDevice>,
    onDeviceSelected: (CastDevice) -> Unit
) {
    // Group by protocol for visual organization
    val grouped = devices.groupBy { it.protocol }
    val protocolOrder = listOf(
        CastProtocol.CHROMECAST,
        CastProtocol.ROKU,
        CastProtocol.KODI,
        CastProtocol.AIRPLAY
    )

    Column(
        modifier = Modifier
            .heightIn(max = 400.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        protocolOrder.forEach { protocol ->
            val protocolDevices = grouped[protocol] ?: return@forEach
            protocolDevices.forEach { device ->
                DeviceRow(
                    device = device,
                    onClick = { onDeviceSelected(device) }
                )
            }
        }
    }
}

@Composable
private fun DeviceRow(
    device: CastDevice,
    onClick: () -> Unit
) {
    val (icon, gradientColors, tint, typeLabel) = deviceVisuals(device)

    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = SapphoSurface
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Device icon with gradient background
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Brush.linearGradient(colors = gradientColors)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = tint,
                    modifier = Modifier.size(24.dp)
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    device.name,
                    color = Color.White,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        typeLabel,
                        color = SapphoIconDefault,
                        fontSize = 12.sp
                    )
                    if (device.protocol == CastProtocol.AIRPLAY) {
                        Text(
                            "experimental",
                            color = SapphoWarning.copy(alpha = 0.8f),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            // Arrow indicator
            Icon(
                imageVector = Icons.Default.PlayArrow,
                contentDescription = null,
                tint = SapphoIconDefault,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

// -- Visual helpers --

private fun iconForProtocol(protocol: CastProtocol?): ImageVector {
    return when (protocol) {
        CastProtocol.CHROMECAST -> Icons.Default.Cast
        CastProtocol.ROKU -> Icons.Default.Tv
        CastProtocol.KODI -> Icons.Default.DesktopWindows
        CastProtocol.AIRPLAY -> Icons.Default.Speaker
        null -> Icons.Default.Cast
    }
}

private fun labelForProtocol(protocol: CastProtocol?): String {
    return when (protocol) {
        CastProtocol.CHROMECAST -> "Casting via Chromecast"
        CastProtocol.ROKU -> "Casting via Roku"
        CastProtocol.KODI -> "Casting via Kodi"
        CastProtocol.AIRPLAY -> "Casting via AirPlay"
        null -> "Casting audio to this device"
    }
}

private data class DeviceVisuals(
    val icon: ImageVector,
    val gradientColors: List<Color>,
    val tint: Color,
    val typeLabel: String
)

private fun deviceVisuals(device: CastDevice): DeviceVisuals {
    return when (device.protocol) {
        CastProtocol.CHROMECAST -> {
            DeviceVisuals(
                icon = when (device.deviceType) {
                    CastDeviceType.TV -> Icons.Default.Tv
                    CastDeviceType.SPEAKER -> Icons.Default.Speaker
                    CastDeviceType.UNKNOWN -> Icons.Default.Cast
                },
                gradientColors = when (device.deviceType) {
                    CastDeviceType.TV -> listOf(LegacyPurpleLight.copy(alpha = 0.3f), SapphoInfo.copy(alpha = 0.2f))
                    CastDeviceType.SPEAKER -> listOf(SapphoSuccess.copy(alpha = 0.3f), LegacyBlueLight.copy(alpha = 0.2f))
                    CastDeviceType.UNKNOWN -> listOf(SapphoInfo.copy(alpha = 0.3f), LegacyPurpleLight.copy(alpha = 0.2f))
                },
                tint = when (device.deviceType) {
                    CastDeviceType.TV -> LegacyPurpleLight
                    CastDeviceType.SPEAKER -> SapphoSuccess
                    CastDeviceType.UNKNOWN -> SapphoInfo
                },
                typeLabel = when (device.deviceType) {
                    CastDeviceType.TV -> "Smart TV"
                    CastDeviceType.SPEAKER -> "Smart Speaker"
                    CastDeviceType.UNKNOWN -> "Cast Device"
                }
            )
        }
        CastProtocol.ROKU -> DeviceVisuals(
            icon = Icons.Default.Tv,
            gradientColors = listOf(LegacyPurpleLight.copy(alpha = 0.3f), SapphoInfo.copy(alpha = 0.2f)),
            tint = LegacyPurpleLight,
            typeLabel = "Roku"
        )
        CastProtocol.KODI -> DeviceVisuals(
            icon = Icons.Default.DesktopWindows,
            gradientColors = listOf(SapphoInfo.copy(alpha = 0.3f), SapphoSuccess.copy(alpha = 0.2f)),
            tint = SapphoInfo,
            typeLabel = "Kodi / Fire TV"
        )
        CastProtocol.AIRPLAY -> DeviceVisuals(
            icon = Icons.Default.Speaker,
            gradientColors = listOf(SapphoSuccess.copy(alpha = 0.3f), LegacyBlueLight.copy(alpha = 0.2f)),
            tint = SapphoSuccess,
            typeLabel = "AirPlay"
        )
    }
}
