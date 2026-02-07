package com.meshsos.ui.home

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.meshsos.data.model.EmergencyType
import com.meshsos.data.model.NodeRole
import com.meshsos.ui.theme.*

/**
 * Home Screen - Main SOS Interface
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel = viewModel(),
    onNavigateToHistory: () -> Unit = {},
    onNavigateToResponder: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()
    val haptic = LocalHapticFeedback.current
    
    var showEmergencyTypeDialog by remember { mutableStateOf(false) }
    
    Scaffold(
        containerColor = Background,
        topBar = {
            TopAppBar(
                title = { 
                    Text(
                        "MeshSOS",
                        fontWeight = FontWeight.Bold,
                        color = Primary
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Background
                ),
                actions = {
                    // Node role badge
                    NodeRoleBadge(role = uiState.nodeRole)
                    
                    IconButton(onClick = onNavigateToHistory) {
                        Icon(
                            Icons.Default.History,
                            contentDescription = "History",
                            tint = TextPrimary
                        )
                    }
                    
                    if (uiState.nodeRole == NodeRole.RESPONDER) {
                        IconButton(onClick = onNavigateToResponder) {
                            Icon(
                                Icons.Default.Map,
                                contentDescription = "Responder Dashboard",
                                tint = TextPrimary
                            )
                        }
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Status indicators row
            StatusIndicatorsRow(uiState)
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // Mesh info card
            MeshInfoCard(uiState)
            
            Spacer(modifier = Modifier.weight(1f))
            
            // SOS Button
            SosButton(
                isSending = uiState.isSending,
                onLongPress = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    showEmergencyTypeDialog = true
                }
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Text(
                "Long press to send SOS",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary
            )
            
            Spacer(modifier = Modifier.weight(1f))
            
            // Quick actions
            QuickActionsRow(
                isMeshActive = uiState.isMeshActive,
                onToggleMesh = {
                    if (uiState.isMeshActive) {
                        viewModel.stopMeshService()
                    } else {
                        viewModel.startMeshService()
                    }
                }
            )
        }
    }
    
    // Emergency type selection dialog
    if (showEmergencyTypeDialog) {
        EmergencyTypeDialog(
            onDismiss = { showEmergencyTypeDialog = false },
            onSelect = { type ->
                showEmergencyTypeDialog = false
                viewModel.sendSos(type)
            }
        )
    }
}

@Composable
fun StatusIndicatorsRow(state: HomeUiState) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        StatusIndicator(
            icon = Icons.Default.Bluetooth,
            label = if (state.isMeshActive) "Mesh Active" else "Mesh Off",
            isActive = state.isMeshActive,
            activeColor = StatusOnline
        )
        
        StatusIndicator(
            icon = Icons.Default.Wifi,
            label = if (state.hasInternet) "Online" else "Offline",
            isActive = state.hasInternet,
            activeColor = StatusOnline
        )
        
        StatusIndicator(
            icon = Icons.Default.LocationOn,
            label = if (state.hasLocation) "GPS" else "No GPS",
            isActive = state.hasLocation,
            activeColor = StatusOnline
        )
        
        StatusIndicator(
            icon = Icons.Default.BatteryFull,
            label = "${state.batteryLevel}%",
            isActive = state.batteryLevel > 20,
            activeColor = if (state.batteryLevel > 50) StatusOnline else StatusWarning
        )
    }
}

@Composable
fun StatusIndicator(
    icon: ImageVector,
    label: String,
    isActive: Boolean,
    activeColor: Color
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(8.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = if (isActive) activeColor else TextDisabled,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = if (isActive) TextPrimary else TextDisabled
        )
    }
}

@Composable
fun MeshInfoCard(state: HomeUiState) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Surface),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    "Nearby Nodes",
                    style = MaterialTheme.typography.labelMedium,
                    color = TextSecondary
                )
                Text(
                    "${state.nearbyNodeCount}",
                    style = MaterialTheme.typography.headlineMedium,
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold
                )
            }
            
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    "Location Accuracy",
                    style = MaterialTheme.typography.labelMedium,
                    color = TextSecondary
                )
                Text(
                    if (state.hasLocation) "±${state.locationAccuracy.toInt()}m" else "N/A",
                    style = MaterialTheme.typography.headlineMedium,
                    color = if (state.hasLocation) TextPrimary else TextDisabled,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
fun SosButton(
    isSending: Boolean,
    onLongPress: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )
    
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )
    
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.size(220.dp)
    ) {
        // Pulse effect
        Box(
            modifier = Modifier
                .size(200.dp)
                .scale(pulseScale)
                .clip(CircleShape)
                .background(Primary.copy(alpha = pulseAlpha))
        )
        
        // Main button
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(180.dp)
                .clip(CircleShape)
                .background(
                    brush = Brush.radialGradient(
                        colors = listOf(Primary, PrimaryVariant)
                    )
                )
                .border(4.dp, Primary.copy(alpha = 0.5f), CircleShape)
                .clickable(
                    enabled = !isSending,
                    onClick = { } // Short click does nothing, long press required
                )
        ) {
            // Long press detection using pointer input
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.pointerInput(Unit) {
                    detectTapGestures(
                        onLongPress = { onLongPress() }
                    )
                }
            ) {
                if (isSending) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(48.dp),
                        color = Color.White
                    )
                } else {
                    Icon(
                        Icons.Default.Warning,
                        contentDescription = "SOS",
                        tint = Color.White,
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "SOS",
                        style = MaterialTheme.typography.headlineLarge,
                        color = Color.White,
                        fontWeight = FontWeight.Black
                    )
                }
            }
        }
    }
}

@Composable
fun NodeRoleBadge(role: NodeRole) {
    val (color, icon) = when (role) {
        NodeRole.USER -> Pair(TextSecondary, Icons.Default.Person)
        NodeRole.RELAY -> Pair(StatusWarning, Icons.Default.SwapHoriz)
        NodeRole.RESPONDER -> Pair(StatusOnline, Icons.Default.LocalHospital)
    }
    
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = color.copy(alpha = 0.2f),
        modifier = Modifier.padding(end = 8.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = role.displayName,
                tint = color,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = role.displayName,
                style = MaterialTheme.typography.labelSmall,
                color = color
            )
        }
    }
}

@Composable
fun QuickActionsRow(
    isMeshActive: Boolean,
    onToggleMesh: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        OutlinedButton(
            onClick = onToggleMesh,
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = if (isMeshActive) StatusOnline else TextSecondary
            )
        ) {
            Icon(
                if (isMeshActive) Icons.Default.BluetoothConnected else Icons.Default.BluetoothDisabled,
                contentDescription = "Toggle Mesh"
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(if (isMeshActive) "Stop Mesh" else "Start Mesh")
        }
    }
}

@Composable
fun EmergencyTypeDialog(
    onDismiss: () -> Unit,
    onSelect: (EmergencyType) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Surface,
        title = {
            Text(
                "Select Emergency Type",
                color = TextPrimary
            )
        },
        text = {
            Column {
                EmergencyType.entries.forEach { type ->
                    val (icon, color) = when (type) {
                        EmergencyType.MEDICAL -> Pair(Icons.Default.LocalHospital, EmergencyMedical)
                        EmergencyType.FIRE -> Pair(Icons.Default.LocalFireDepartment, EmergencyFire)
                        EmergencyType.FLOOD -> Pair(Icons.Default.Water, EmergencyFlood)
                        EmergencyType.EARTHQUAKE -> Pair(Icons.Default.Landscape, EmergencyEarthquake)
                        EmergencyType.GENERAL -> Pair(Icons.Default.Warning, EmergencyGeneral)
                    }
                    
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .clickable { onSelect(type) },
                        shape = RoundedCornerShape(8.dp),
                        color = color.copy(alpha = 0.1f)
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = icon,
                                contentDescription = type.displayName,
                                tint = color,
                                modifier = Modifier.size(32.dp)
                            )
                            Spacer(modifier = Modifier.width(16.dp))
                            Text(
                                text = type.displayName,
                                style = MaterialTheme.typography.titleMedium,
                                color = TextPrimary
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = TextSecondary)
            }
        }
    )
}


