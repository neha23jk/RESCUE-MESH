package com.meshsos.service.ble

import android.util.Log
import com.meshsos.data.model.EmergencyType
import com.meshsos.data.model.MinimalSosPacket
import com.meshsos.data.model.SosPacket
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.UUID

/**
 * Binary encoder/decoder for SOS packets.
 * 
 * Provides ~70% size reduction compared to JSON encoding.
 * Maintains backward compatibility with JSON-based transfers.
 * 
 * Wire Format (32-42+ bytes):
 * | Field         | Size  | Description                    |
 * |---------------|-------|--------------------------------|
 * | Magic         | 2     | 0x534F ("SO" for SOS)          |
 * | Version       | 1     | Protocol version (0x01)        |
 * | Flags         | 1     | hasMessage, hasAccuracy, etc.  |
 * | EmergencyType | 1     | Ordinal value                  |
 * | TTL           | 1     | Time-to-live                   |
 * | HopCount      | 1     | Number of hops                 |
 * | Battery       | 1     | Battery percentage (0-100)     |
 * | Timestamp     | 8     | Milliseconds since epoch       |
 * | UUID (high)   | 8     | Most significant bits          |
 * | UUID (low)    | 8     | Least significant bits         |
 * | Latitude      | 4     | Int32 (lat × 1e6)              |
 * | Longitude     | 4     | Int32 (lon × 1e6)              |
 * | Accuracy      | 2     | Optional, meters (if flag set) |
 * | MsgLength     | 1     | Optional, message length       |
 * | Message       | N     | Optional, UTF-8 bytes          |
 */
object BinaryPacketCodec {
    
    private const val TAG = "BinaryPacketCodec"
    
    // Magic bytes to identify binary format: "SO" (0x53 0x4F)
    private const val MAGIC_BYTE_1: Byte = 0x53
    private const val MAGIC_BYTE_2: Byte = 0x4F
    
    // Current protocol version
    private const val VERSION: Byte = 0x01
    
    // Flag bits
    private const val FLAG_HAS_ACCURACY: Int = 0x01
    private const val FLAG_HAS_MESSAGE: Int = 0x02
    private const val FLAG_HAS_SIGNATURE: Int = 0x04
    
    // Header sizes
    private const val FIXED_HEADER_SIZE = 40 // Magic(2) + Version(1) + Flags(1) + Type(1) + TTL(1) + Hop(1) + Battery(1) + Timestamp(8) + UUID(16) + Lat(4) + Lon(4)
    
    // Maximum message length (255 bytes max due to 1-byte length field)
    private const val MAX_MESSAGE_LENGTH = 255
    
    /**
     * Encode an SOS packet to compact binary format.
     * 
     * @param packet The SOS packet to encode
     * @return Binary encoded data, or null if encoding failed
     */
    fun encode(packet: SosPacket): ByteArray? {
        return try {
            val hasAccuracy = packet.accuracy != null && packet.accuracy > 0
            val hasMessage = !packet.optionalMessage.isNullOrEmpty()
            val messageBytes = packet.optionalMessage?.take(MAX_MESSAGE_LENGTH)?.toByteArray(Charsets.UTF_8)
            
            // Calculate total size
            var size = FIXED_HEADER_SIZE
            if (hasAccuracy) size += 2  // Int16 for accuracy
            if (hasMessage && messageBytes != null) {
                size += 1 + messageBytes.size  // 1 byte length + message
            }
            
            val buffer = ByteBuffer.allocate(size)
            buffer.order(ByteOrder.BIG_ENDIAN)
            
            // Magic bytes
            buffer.put(MAGIC_BYTE_1)
            buffer.put(MAGIC_BYTE_2)
            
            // Version
            buffer.put(VERSION)
            
            // Flags
            var flags = 0
            if (hasAccuracy) flags = flags or FLAG_HAS_ACCURACY
            if (hasMessage) flags = flags or FLAG_HAS_MESSAGE
            if (packet.signature != null) flags = flags or FLAG_HAS_SIGNATURE
            buffer.put(flags.toByte())
            
            // Emergency type (1 byte)
            buffer.put(packet.emergencyType.ordinal.toByte())
            
            // TTL (1 byte)
            buffer.put(packet.ttl.coerceIn(0, 255).toByte())
            
            // Hop count (1 byte)
            buffer.put(packet.hopCount.coerceIn(0, 255).toByte())
            
            // Battery (1 byte)
            buffer.put((packet.batteryPercentage ?: 0).coerceIn(0, 100).toByte())
            
            // Timestamp (8 bytes)
            buffer.putLong(packet.timestamp)
            
            // UUID (16 bytes)
            buffer.putLong(packet.sosId.mostSignificantBits)
            buffer.putLong(packet.sosId.leastSignificantBits)
            
            // Latitude (4 bytes as Int32 × 1e6)
            buffer.putInt((packet.latitude * 1_000_000).toInt())
            
            // Longitude (4 bytes as Int32 × 1e6)
            buffer.putInt((packet.longitude * 1_000_000).toInt())
            
            // Optional: Accuracy (2 bytes)
            if (hasAccuracy) {
                buffer.putShort(packet.accuracy!!.toInt().coerceIn(0, 65535).toShort())
            }
            
            // Optional: Message (1 byte length + N bytes)
            if (hasMessage && messageBytes != null) {
                buffer.put(messageBytes.size.toByte())
                buffer.put(messageBytes)
            }
            
            Log.d(TAG, "Encoded packet ${packet.sosId}: ${size} bytes (vs ~${packet.optionalMessage?.length ?: 0 + 150} JSON)")
            
            buffer.array()
        } catch (e: Exception) {
            Log.e(TAG, "Error encoding packet", e)
            null
        }
    }
    
    /**
     * Decode binary data to an SOS packet.
     * 
     * @param data Binary encoded data
     * @param deviceId Device ID to use for the packet
     * @return Decoded SOS packet, or null if decoding failed (try JSON fallback)
     */
    fun decode(data: ByteArray, deviceId: String = "unknown"): SosPacket? {
        return try {
            if (data.size < FIXED_HEADER_SIZE) {
                Log.d(TAG, "Data too small for binary format: ${data.size} bytes")
                return null
            }
            
            val buffer = ByteBuffer.wrap(data)
            buffer.order(ByteOrder.BIG_ENDIAN)
            
            // Check magic bytes
            val magic1 = buffer.get()
            val magic2 = buffer.get()
            if (magic1 != MAGIC_BYTE_1 || magic2 != MAGIC_BYTE_2) {
                Log.d(TAG, "Invalid magic bytes, not binary format")
                return null
            }
            
            // Version check
            val version = buffer.get()
            if (version != VERSION) {
                Log.w(TAG, "Unknown version: $version")
                // Continue anyway for forward compatibility
            }
            
            // Flags
            val flags = buffer.get().toInt() and 0xFF
            val hasAccuracy = (flags and FLAG_HAS_ACCURACY) != 0
            val hasMessage = (flags and FLAG_HAS_MESSAGE) != 0
            
            // Emergency type
            val emergencyTypeOrdinal = buffer.get().toInt() and 0xFF
            val emergencyType = EmergencyType.entries.getOrElse(emergencyTypeOrdinal) { EmergencyType.GENERAL }
            
            // TTL
            val ttl = buffer.get().toInt() and 0xFF
            
            // Hop count
            val hopCount = buffer.get().toInt() and 0xFF
            
            // Battery
            val battery = buffer.get().toInt() and 0xFF
            
            // Timestamp
            val timestamp = buffer.getLong()
            
            // UUID
            val uuidHigh = buffer.getLong()
            val uuidLow = buffer.getLong()
            val sosId = UUID(uuidHigh, uuidLow)
            
            // Latitude
            val latitudeInt = buffer.getInt()
            val latitude = latitudeInt / 1_000_000.0
            
            // Longitude
            val longitudeInt = buffer.getInt()
            val longitude = longitudeInt / 1_000_000.0
            
            // Optional: Accuracy
            val accuracy: Float? = if (hasAccuracy && buffer.remaining() >= 2) {
                (buffer.getShort().toInt() and 0xFFFF).toFloat()
            } else null
            
            // Optional: Message
            val message: String? = if (hasMessage && buffer.remaining() >= 1) {
                val msgLength = buffer.get().toInt() and 0xFF
                if (buffer.remaining() >= msgLength) {
                    val msgBytes = ByteArray(msgLength)
                    buffer.get(msgBytes)
                    String(msgBytes, Charsets.UTF_8)
                } else null
            } else null
            
            Log.d(TAG, "Decoded packet $sosId from ${data.size} bytes")
            
            SosPacket(
                sosId = sosId,
                deviceId = deviceId,
                timestamp = timestamp,
                latitude = latitude,
                longitude = longitude,
                accuracy = accuracy,
                emergencyType = emergencyType,
                optionalMessage = message,
                batteryPercentage = battery,
                hopCount = hopCount,
                ttl = ttl
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error decoding binary packet", e)
            null
        }
    }
    
    /**
     * Check if data appears to be in binary format (starts with magic bytes).
     */
    fun isBinaryFormat(data: ByteArray): Boolean {
        return data.size >= 2 && data[0] == MAGIC_BYTE_1 && data[1] == MAGIC_BYTE_2
    }
    
    /**
     * Try to decode data as binary, falling back to JSON if binary fails.
     * This ensures backward compatibility with older app versions.
     */
    fun decodeWithFallback(data: ByteArray, deviceId: String = "unknown"): SosPacket? {
        // Try binary first
        if (isBinaryFormat(data)) {
            val packet = decode(data, deviceId)
            if (packet != null) return packet
        }
        
        // Fall back to JSON
        return try {
            val json = String(data, Charsets.UTF_8)
            MinimalSosPacket.fromJson(json)?.toSosPacket(deviceId)
        } catch (e: Exception) {
            Log.e(TAG, "JSON fallback also failed", e)
            null
        }
    }
}
