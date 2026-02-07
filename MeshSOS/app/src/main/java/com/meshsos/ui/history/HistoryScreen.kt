package com.meshsos.ui.history

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.lifecycle.viewmodel.compose.viewModel
import com.meshsos.data.model.DeliveryStatus
import com.meshsos.data.model.EmergencyType
import com.meshsos.data.model.SosPacket
import com.meshsos.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*

/**
 * SOS History Screen - Shows sent and received SOS packets
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    viewModel: HistoryViewModel = viewModel(),
    onNavigateBack: () -> Unit = {}
) {
    val packets by viewModel.allPackets.collectAsState(initial = emptyList())
    
    Scaffold(
        containerColor = Background,
        topBar = {
            TopAppBar(
                title = { Text("SOS History", color = TextPrimary) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            Icons.Default.ArrowBack,
                            contentDescription = "Back",
                            tint = TextPrimary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Background
                )
            )
        }
    ) { padding ->
        if (packets.isEmpty()) {
            EmptyHistoryMessage(modifier = Modifier.padding(padding))
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(vertical = 16.dp)
            ) {
                items(packets) { packet ->
                    SosHistoryCard(packet = packet)
                }
            }
        }
    }
}

@Composable
fun EmptyHistoryMessage(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                Icons.Default.History,
                contentDescription = null,
                tint = TextDisabled,
                modifier = Modifier.size(64.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                "No SOS history",
                style = MaterialTheme.typography.titleMedium,
                color = TextSecondary
            )
            Text(
                "SOS signals will appear here",
                style = MaterialTheme.typography.bodyMedium,
                color = TextDisabled
            )
        }
    }
}

@Composable
fun SosHistoryCard(packet: SosPacket) {
    val dateFormat = remember { 
        SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault()) 
    }
    
    val (emergencyIcon, emergencyColor) = when (packet.emergencyType) {
        EmergencyType.MEDICAL -> Pair(Icons.Default.LocalHospital, EmergencyMedical)
        EmergencyType.FIRE -> Pair(Icons.Default.LocalFireDepartment, EmergencyFire)
        EmergencyType.FLOOD -> Pair(Icons.Default.Water, EmergencyFlood)
        EmergencyType.EARTHQUAKE -> Pair(Icons.Default.Landscape, EmergencyEarthquake)
        EmergencyType.GENERAL -> Pair(Icons.Default.Warning, EmergencyGeneral)
    }
    
    val (statusIcon, statusColor, statusText) = when (packet.status) {
        DeliveryStatus.PENDING -> Triple(Icons.Default.HourglassEmpty, StatusWarning, "Pending")
        DeliveryStatus.RELAYED -> Triple(Icons.Default.SwapHoriz, StatusWarning, "Relaying")
        DeliveryStatus.DELIVERED -> Triple(Icons.Default.CloudDone, StatusOnline, "Delivered")
        DeliveryStatus.RESPONDED -> Triple(Icons.Default.CheckCircle, StatusOnline, "Responded")
    }
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Surface),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Emergency type indicator
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(emergencyColor.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            emergencyIcon,
                            contentDescription = null,
                            tint = emergencyColor,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            packet.emergencyType.displayName,
                            style = MaterialTheme.typography.titleMedium,
                            color = TextPrimary,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            dateFormat.format(Date(packet.timestamp)),
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                    }
                }
                
                // Own/Received badge
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = if (packet.isOwnPacket) Primary.copy(alpha = 0.2f) 
                           else TextSecondary.copy(alpha = 0.2f)
                ) {
                    Text(
                        text = if (packet.isOwnPacket) "SENT" else "RECEIVED",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (packet.isOwnPacket) Primary else TextSecondary,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // Info row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Status
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        statusIcon,
                        contentDescription = null,
                        tint = statusColor,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        statusText,
                        style = MaterialTheme.typography.bodySmall,
                        color = statusColor
                    )
                }
                
                // Hop count
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Route,
                        contentDescription = null,
                        tint = TextSecondary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        "${packet.hopCount} hops",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                }
                
                // Location
                if (packet.latitude != 0.0 && packet.longitude != 0.0) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.LocationOn,
                            contentDescription = null,
                            tint = TextSecondary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            "%.4f, %.4f".format(packet.latitude, packet.longitude),
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                    }
                }
            }
            
            // Optional message
            packet.optionalMessage?.let { message ->
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )
            }
        }
    }
}
