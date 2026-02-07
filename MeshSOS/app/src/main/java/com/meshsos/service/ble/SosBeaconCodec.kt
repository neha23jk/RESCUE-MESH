package com.meshsos.service.ble

import android.util.Log
import com.meshsos.data.model.EmergencyType
import com.meshsos.data.model.SosPacket
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.UUID

/**
 * Encodes and decodes SOS beacons for BLE advertising
 * 
 * Optimized Beacon Structure (21 bytes - fits within BLE limits):
 * - SOS ID Prefix (4 bytes): First 4 bytes of UUID for deduplication
 * - Emergency Type (1 byte): Type of emergency
 * - Hop Count (1 byte): Number of hops so far
 * - TTL (1 byte): Time-to-live for relay limiting
 * - Flags (1 byte): Bit flags for device state
 * - Latitude (4 bytes): Fixed-point, scaled by 1e7 (~11mm precision)
 * - Longitude (4 bytes): Fixed-point, scaled by 1e7 (~11mm precision)
 * - Device ID Prefix (4 bytes): First 4 bytes of device ID hash
 * - Battery Percentage (1 byte): 0-100, or 255 for unknown
 */
object SosBeaconCodec {
    
    private const val TAG = "SosBeaconCodec"
    
    const val BEACON_SIZE = 21
    
    // Scale factor for GPS coordinates (1e7 gives ~11mm precision)
    private const val GPS_SCALE_FACTOR = 10_000_000.0
    
    // Unknown battery value
    private const val BATTERY_UNKNOWN: Byte = -1 // 255 unsigned
    
    // MeshSOS service UUID for BLE
    val SERVICE_UUID: UUID = UUID.fromString("0000FE50-0000-1000-8000-00805F9B34FB")
    
    // Characteristic UUID for full packet transfer (kept for compatibility)
    val PACKET_CHARACTERISTIC_UUID: UUID = UUID.fromString("0000FE51-0000-1000-8000-00805F9B34FB")
    
    /**
     * Encode SOS packet to byte array for BLE advertising (optimized 21 bytes)
     */
    fun encode(packet: SosPacket, hasInternet: Boolean, isResponder: Boolean): ByteArray {
        val buffer = ByteBuffer.allocate(BEACON_SIZE)
        buffer.order(ByteOrder.BIG_ENDIAN)
        
        // SOS ID prefix - first 4 bytes only (sufficient for local deduplication)
        buffer.putInt((packet.sosId.mostSignificantBits shr 32).toInt())
        
        // Emergency type (1 byte)
        buffer.put(packet.emergencyType.ordinal.toByte())
        
        // Hop count (1 byte)
        buffer.put(packet.hopCount.coerceIn(0, 255).toByte())
        
        // TTL (1 byte)
        buffer.put(packet.ttl.coerceIn(0, 255).toByte())
        
        // Flags (1 byte)
        var flags = 0
        if (hasInternet) flags = flags or FLAG_HAS_INTERNET
        if (isResponder) flags = flags or FLAG_IS_RESPONDER
        buffer.put(flags.toByte())
        
        // Latitude (4 bytes) - fixed-point encoding
        val latFixed = (packet.latitude * GPS_SCALE_FACTOR).toInt()
        buffer.putInt(latFixed)
        
        // Longitude (4 bytes) - fixed-point encoding
        val lonFixed = (packet.longitude * GPS_SCALE_FACTOR).toInt()
        buffer.putInt(lonFixed)
        
        // Device ID prefix (4 bytes) - hash first 4 bytes
        val deviceIdHash = packet.deviceId.hashCode()
        buffer.putInt(deviceIdHash)
        
        // Battery percentage (1 byte) - 0-100 or 255 for unknown
        val battery = packet.batteryPercentage?.coerceIn(0, 100)?.toByte() ?: BATTERY_UNKNOWN
        buffer.put(battery)
        
        return buffer.array()
    }
    
    /**
     * Decode byte array from BLE advertising to decoded beacon data
     * Returns a DecodedBeacon with all fields, including GPS coordinates
     */
    fun decode(data: ByteArray): DecodedBeacon? {
        if (data.size < BEACON_SIZE) {
            Log.w(TAG, "Beacon data too small: ${data.size} bytes, expected $BEACON_SIZE")
            return null
        }
        
        val buffer = ByteBuffer.wrap(data)
        buffer.order(ByteOrder.BIG_ENDIAN)
        
        try {
            // SOS ID prefix (4 bytes) - reconstruct partial UUID
            val uuidPrefix = buffer.int.toLong() and 0xFFFFFFFFL
            val sosId = UUID(uuidPrefix shl 32, 0L)
            
            // Emergency type (1 byte)
            val emergencyTypeOrdinal = buffer.get().toInt() and 0xFF
            val emergencyType = EmergencyType.entries.getOrNull(emergencyTypeOrdinal)
                ?: EmergencyType.GENERAL
            
            // Hop count (1 byte)
            val hopCount = buffer.get().toInt() and 0xFF
            
            // TTL (1 byte)
            val ttl = buffer.get().toInt() and 0xFF
            
            // Flags (1 byte)
            val flags = buffer.get().toInt() and 0xFF
            
            // Latitude (4 bytes) - decode fixed-point
            val latFixed = buffer.int
            val latitude = latFixed / GPS_SCALE_FACTOR
            
            // Longitude (4 bytes) - decode fixed-point
            val lonFixed = buffer.int
            val longitude = lonFixed / GPS_SCALE_FACTOR
            
            // Device ID prefix (4 bytes)
            val deviceIdHash = buffer.int
            
            // Battery percentage (1 byte)
            val batteryRaw = buffer.get().toInt() and 0xFF
            val batteryPercentage = if (batteryRaw == 255) null else batteryRaw
            
            return DecodedBeacon(
                sosId = sosId,
                emergencyType = emergencyType,
                hopCount = hopCount,
                ttl = ttl,
                flags = flags,
                latitude = latitude,
                longitude = longitude,
                deviceIdHash = deviceIdHash,
                batteryPercentage = batteryPercentage
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error decoding beacon", e)
            return null
        }
    }
    
    // Flag constants
    const val FLAG_HAS_INTERNET = 0x01
    const val FLAG_IS_RESPONDER = 0x02
}

/**
 * Decoded beacon data with full GPS coordinates and battery info
 */
data class DecodedBeacon(
    val sosId: UUID,
    val emergencyType: EmergencyType,
    val hopCount: Int,
    val ttl: Int,
    val flags: Int,
    val latitude: Double,
    val longitude: Double,
    val deviceIdHash: Int,
    val batteryPercentage: Int?  // null if unknown
) {
    fun hasInternet(): Boolean = (flags and SosBeaconCodec.FLAG_HAS_INTERNET) != 0
    fun isResponder(): Boolean = (flags and SosBeaconCodec.FLAG_IS_RESPONDER) != 0
    fun shouldRelay(): Boolean = ttl > 1
    
    /**
     * Convert device ID hash to a pseudo device ID string
     * Format: "device-XXXXXXXX" where X is hex of hash
     */
    fun getDeviceIdString(): String = "device-${Integer.toHexString(deviceIdHash).uppercase()}"
}
