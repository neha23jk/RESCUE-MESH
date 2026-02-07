package com.meshsos.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * MeshSOS Color Palette
 * Dark theme with emergency-focused red accent
 */

// Background colors
val Background = Color(0xFF000000)
val Surface = Color(0xFF1F1F1F)
val SurfaceVariant = Color(0xFF2A2A2A)

// Primary (Emergency Red)
val Primary = Color(0xFFE50914)
val PrimaryVariant = Color(0xFFB30710)
val OnPrimary = Color.White

// Secondary
val Secondary = Color(0xFF1F1F1F)
val SecondaryVariant = Color(0xFF383838)

// Text colors
val TextPrimary = Color(0xFFFFFFFF)
val TextSecondary = Color(0xFFAAAAAA)
val TextDisabled = Color(0xFF666666)

// Status colors
val StatusOnline = Color(0xFF4CAF50)  // Green
val StatusOffline = Color(0xFFFF5722) // Orange-red
val StatusWarning = Color(0xFFFFC107) // Amber
val StatusError = Color(0xFFE50914)   // Emergency red

// Emergency type colors
val EmergencyMedical = Color(0xFFE91E63)   // Pink
val EmergencyFire = Color(0xFFFF5722)      // Orange
val EmergencyFlood = Color(0xFF2196F3)     // Blue
val EmergencyEarthquake = Color(0xFF795548) // Brown
val EmergencyGeneral = Color(0xFFE50914)   // Red
