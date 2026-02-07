package com.meshsos.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverters
import com.meshsos.data.local.Converters
import java.util.UUID

/**
 * SOS Packet entity for Room database
 * 
 * Represents an emergency SOS signal with location, emergency type,
 * and mesh routing information.
 */
@Entity(tableName = "sos_packets")
@TypeConverters(Converters::class)
data class SosPacket(
    @PrimaryKey
    val sosId: UUID = UUID.randomUUID(),
    
    // Device info (SHA-256 hashed for privacy)
    val deviceId: String,
    
    // Timestamp in milliseconds since epoch
    val timestamp: Long = System.currentTimeMillis(),
    
    // Location
    val latitude: Double,
    val longitude: Double,
    val accuracy: Float? = null,
    
    // Emergency details
    val emergencyType: EmergencyType = EmergencyType.GENERAL,
    val optionalMessage: String? = null,
    
    // Battery info
    val batteryPercentage: Int? = null,
    
    // Mesh routing info
    val hopCount: Int = 0,
    val ttl: Int = 10,
    
    // Security
    val signature: String? = null,
    
    // Tracking
    val status: DeliveryStatus = DeliveryStatus.PENDING,
    val isOwnPacket: Boolean = false,
    val receivedAt: Long = System.currentTimeMillis(),
    val uploadedAt: Long? = null
) {
    /**
     * Check if packet should be relayed (TTL > 0)
     */
    fun shouldRelay(): Boolean = ttl > 0
    
    /**
     * Create a copy with incremented hop count and decremented TTL
     */
    fun forRelay(): SosPacket = copy(
        hopCount = hopCount + 1,
        ttl = ttl - 1
    )
    
    /**
     * Check if packet is too old (older than 1 hour)
     */
    fun isTooOld(): Boolean {
        val oneHourAgo = System.currentTimeMillis() - (60 * 60 * 1000)
        return timestamp < oneHourAgo
    }
}

/**
 * Lightweight SOS beacon for BLE advertising (25 bytes)
 */
data class SosBeacon(
    val sosId: UUID,           // 16 bytes
    val emergencyType: EmergencyType, // 1 byte
    val hopCount: Int,         // 1 byte
    val ttl: Int,              // 1 byte
    val flags: Int,            // 1 byte (hasInternet, isResponder, etc.)
    val checksum: Int          // 2 bytes
) {
    companion object {
        const val FLAG_HAS_INTERNET = 0x01
        const val FLAG_IS_RESPONDER = 0x02
        const val FLAG_HAS_FULL_PACKET = 0x04
        
        const val BEACON_SIZE = 22 // Actual encoded size
    }
    
    fun hasInternet(): Boolean = (flags and FLAG_HAS_INTERNET) != 0
    fun isResponder(): Boolean = (flags and FLAG_IS_RESPONDER) != 0
    fun hasFullPacket(): Boolean = (flags and FLAG_HAS_FULL_PACKET) != 0
}
