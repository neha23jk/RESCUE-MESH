package com.meshsos.service.ble

import com.meshsos.data.model.EmergencyType
import com.meshsos.data.model.SosBeacon
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.UUID

/**
 * Encodes and decodes SOS beacons for BLE advertising
 * 
 * Beacon Structure (22 bytes):
 * - UUID (16 bytes): SOS ID for deduplication
 * - Emergency Type (1 byte): Type of emergency
 * - Hop Count (1 byte): Number of hops so far
 * - TTL (1 byte): Time-to-live for relay limiting
 * - Flags (1 byte): Bit flags for device state
 * - Checksum (2 bytes): CRC-16 for integrity
 */
object SosBeaconCodec {
    
    const val BEACON_SIZE = 22
    
    // MeshSOS service UUID for BLE
    val SERVICE_UUID: UUID = UUID.fromString("0000FE50-0000-1000-8000-00805F9B34FB")
    
    // Characteristic UUID for full packet transfer
    val PACKET_CHARACTERISTIC_UUID: UUID = UUID.fromString("0000FE51-0000-1000-8000-00805F9B34FB")
    
    /**
     * Encode SOS beacon to byte array for BLE advertising
     */
    fun encode(beacon: SosBeacon): ByteArray {
        val buffer = ByteBuffer.allocate(BEACON_SIZE)
        buffer.order(ByteOrder.LITTLE_ENDIAN)
        
        // UUID (16 bytes)
        buffer.putLong(beacon.sosId.mostSignificantBits)
        buffer.putLong(beacon.sosId.leastSignificantBits)
        
        // Emergency type (1 byte)
        buffer.put(beacon.emergencyType.ordinal.toByte())
        
        // Hop count (1 byte)
        buffer.put(beacon.hopCount.coerceIn(0, 255).toByte())
        
        // TTL (1 byte)
        buffer.put(beacon.ttl.coerceIn(0, 255).toByte())
        
        // Flags (1 byte)
        buffer.put(beacon.flags.toByte())
        
        // Calculate checksum over first 20 bytes
        val data = buffer.array()
        val checksum = calculateCRC16(data, 0, 20)
        
        // Checksum (2 bytes)
        buffer.putShort(checksum.toShort())
        
        return buffer.array()
    }
    
    /**
     * Decode byte array from BLE advertising to SOS beacon
     * Returns null if invalid
     */
    fun decode(data: ByteArray): SosBeacon? {
        if (data.size < BEACON_SIZE) {
            return null
        }
        
        val buffer = ByteBuffer.wrap(data)
        buffer.order(ByteOrder.LITTLE_ENDIAN)
        
        try {
            // UUID (16 bytes)
            val msb = buffer.long
            val lsb = buffer.long
            val sosId = UUID(msb, lsb)
            
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
            
            // Checksum (2 bytes)
            val receivedChecksum = buffer.short.toInt() and 0xFFFF
            
            // Verify checksum
            val calculatedChecksum = calculateCRC16(data, 0, 20)
            if (receivedChecksum != calculatedChecksum) {
                return null
            }
            
            return SosBeacon(
                sosId = sosId,
                emergencyType = emergencyType,
                hopCount = hopCount,
                ttl = ttl,
                flags = flags,
                checksum = receivedChecksum
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
        
        val beacon = SosBeacon(
            sosId = sosId,
            emergencyType = emergencyType,
            hopCount = hopCount,
            ttl = ttl,
            flags = flags,
            checksum = 0 // Will be calculated during encode
        )
        
        // Calculate actual checksum
        val encoded = encode(beacon)
        val actualChecksum = ByteBuffer.wrap(encoded, 20, 2)
            .order(ByteOrder.LITTLE_ENDIAN)
            .short.toInt() and 0xFFFF
        
        return beacon.copy(checksum = actualChecksum)
    }
    
    /**
     * CRC-16-CCITT calculation
     */
    private fun calculateCRC16(data: ByteArray, offset: Int, length: Int): Int {
        var crc = 0xFFFF
        
        for (i in offset until (offset + length)) {
            val b = data[i].toInt() and 0xFF
            crc = crc xor (b shl 8)
            
            for (j in 0 until 8) {
                crc = if ((crc and 0x8000) != 0) {
                    (crc shl 1) xor 0x1021
                } else {
                    crc shl 1
                }
                crc = crc and 0xFFFF
            }
        }
        
        return crc
    }
}
