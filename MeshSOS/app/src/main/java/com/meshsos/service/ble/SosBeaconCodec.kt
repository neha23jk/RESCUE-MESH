package com.meshsos.service.ble

import com.meshsos.data.model.EmergencyType
import com.meshsos.data.model.SosBeacon
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.UUID

/**
 * Encodes and decodes SOS beacons for BLE advertising
 * 
 * Beacon Structure (8 bytes - compact for BLE limits):
 * - UUID Prefix (4 bytes): First 4 bytes of SOS ID for deduplication
 * - Emergency Type (1 byte): Type of emergency
 * - Hop Count (1 byte): Number of hops so far
 * - TTL (1 byte): Time-to-live for relay limiting
 * - Flags (1 byte): Bit flags for device state
 */
object SosBeaconCodec {
    
    const val BEACON_SIZE = 8
    
    // MeshSOS service UUID for BLE
    val SERVICE_UUID: UUID = UUID.fromString("0000FE50-0000-1000-8000-00805F9B34FB")
    
    // Characteristic UUID for full packet transfer
    val PACKET_CHARACTERISTIC_UUID: UUID = UUID.fromString("0000FE51-0000-1000-8000-00805F9B34FB")
    
    /**
     * Encode SOS beacon to byte array for BLE advertising (compact 8 bytes)
     */
    fun encode(beacon: SosBeacon): ByteArray {
        val buffer = ByteBuffer.allocate(BEACON_SIZE)
        buffer.order(ByteOrder.BIG_ENDIAN)
        
        // UUID prefix - first 4 bytes only (sufficient for local deduplication)
        buffer.putInt((beacon.sosId.mostSignificantBits shr 32).toInt())
        
        // Emergency type (1 byte)
        buffer.put(beacon.emergencyType.ordinal.toByte())
        
        // Hop count (1 byte)
        buffer.put(beacon.hopCount.coerceIn(0, 255).toByte())
        
        // TTL (1 byte)
        buffer.put(beacon.ttl.coerceIn(0, 255).toByte())
        
        // Flags (1 byte)
        buffer.put(beacon.flags.toByte())
        
        return buffer.array()
    }
    
    /**
     * Decode byte array from BLE advertising to SOS beacon (compact format)
     * Returns null if invalid
     */
    fun decode(data: ByteArray): SosBeacon? {
        if (data.size < BEACON_SIZE) {
            return null
        }
        
        val buffer = ByteBuffer.wrap(data)
        buffer.order(ByteOrder.BIG_ENDIAN)
        
        try {
            // UUID prefix (4 bytes) - reconstruct partial UUID
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
            
            return SosBeacon(
                sosId = sosId,
                emergencyType = emergencyType,
                hopCount = hopCount,
                ttl = ttl,
                flags = flags,
                checksum = 0
            )
        } catch (e: Exception) {
            return null
        }
    }
    
    /**
     * Create beacon from SOS packet info
     */
    fun createBeacon(
        sosId: UUID,
        emergencyType: EmergencyType,
        hopCount: Int,
        ttl: Int,
        hasInternet: Boolean,
        isResponder: Boolean
    ): SosBeacon {
        var flags = SosBeacon.FLAG_HAS_FULL_PACKET
        if (hasInternet) flags = flags or SosBeacon.FLAG_HAS_INTERNET
        if (isResponder) flags = flags or SosBeacon.FLAG_IS_RESPONDER
        
        return SosBeacon(
            sosId = sosId,
            emergencyType = emergencyType,
            hopCount = hopCount,
            ttl = ttl,
            flags = flags,
            checksum = 0
        )
    }
    
}
