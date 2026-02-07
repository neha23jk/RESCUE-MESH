package com.meshsos.data.model

import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import java.util.UUID

/**
 * Minimal SOS packet for space-efficient JSON transmission
 * 
 * This compact representation uses abbreviated field names to reduce
 * JSON payload size by ~60-70% compared to the full SosPacket format.
 * Ideal for bandwidth-constrained scenarios like GATT transfers.
 * 
 * Field Mapping:
 * - i  = sosId (UUID)
 * - la = latitude × 1e6 (Int)
 * - lo = longitude × 1e6 (Int)
 * - b  = battery percentage
 * - t  = emergency type (ordinal)
 * - h  = hop count
 * - tl = TTL
 * - a  = accuracy (optional)
 * - m  = message (optional)
 * - s  = signature (optional)
 */
data class MinimalSosPacket(
    @SerializedName("i")
    val sosId: String,           // UUID as string
    
    @SerializedName("la")
    val latitude: Int,           // Latitude × 1e6
    
    @SerializedName("lo")
    val longitude: Int,          // Longitude × 1e6
    
    @SerializedName("b")
    val batteryPercentage: Int,  // 0-100
    
    @SerializedName("t")
    val emergencyType: Int,      // EmergencyType.ordinal
    
    @SerializedName("h")
    val hopCount: Int,           // Number of hops
    
    @SerializedName("tl")
    val ttl: Int,                // Time-to-live
    
    @SerializedName("a")
    val accuracy: Int? = null,   // Accuracy in meters (optional)
    
    @SerializedName("m")
    val message: String? = null, // Optional message
    
    @SerializedName("s")
    val signature: String? = null // Signature (optional)
) {
    companion object {
        private val gson = Gson()
        
        /**
         * Create minimal packet from full SosPacket
         */
        fun fromSosPacket(packet: SosPacket): MinimalSosPacket {
            return MinimalSosPacket(
                sosId = packet.sosId.toString(),
                latitude = (packet.latitude * 1_000_000).toInt(),
                longitude = (packet.longitude * 1_000_000).toInt(),
                batteryPercentage = packet.batteryPercentage ?: 0,
                emergencyType = packet.emergencyType.ordinal,
                hopCount = packet.hopCount,
                ttl = packet.ttl,
                accuracy = packet.accuracy?.toInt(),
                message = packet.optionalMessage,
                signature = packet.signature
            )
        }
        
        /**
         * Parse minimal packet from JSON string
         */
        fun fromJson(json: String): MinimalSosPacket? {
            return try {
                gson.fromJson(json, MinimalSosPacket::class.java)
            } catch (e: Exception) {
                null
            }
        }
    }
    
    /**
     * Convert minimal packet to full SosPacket
     */
    fun toSosPacket(deviceId: String, timestamp: Long = System.currentTimeMillis()): SosPacket {
        return SosPacket(
            sosId = UUID.fromString(sosId),
            deviceId = deviceId,
            timestamp = timestamp,
            latitude = latitude / 1_000_000.0,
            longitude = longitude / 1_000_000.0,
            accuracy = accuracy?.toFloat(),
            emergencyType = EmergencyType.entries.getOrElse(emergencyType) { EmergencyType.GENERAL },
            optionalMessage = message,
            batteryPercentage = batteryPercentage,
            hopCount = hopCount,
            ttl = ttl,
            signature = signature
        )
    }
    
    /**
     * Serialize to JSON string
     */
    fun toJson(): String {
        return gson.toJson(this)
    }
    
    /**
     * Get estimated JSON size in bytes
     */
    fun estimatedSize(): Int {
        return toJson().toByteArray().size
    }
}

/**
 * Extension function to convert SosPacket to minimal format
 */
fun SosPacket.toMinimal(): MinimalSosPacket {
    return MinimalSosPacket.fromSosPacket(this)
}
