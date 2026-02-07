package com.meshsos.data.repository

import com.meshsos.data.local.SosPacketDao
import com.meshsos.data.model.DeliveryStatus
import com.meshsos.data.model.SosPacket
import com.meshsos.data.remote.ApiClient
import com.meshsos.data.remote.UploadSosRequest
import kotlinx.coroutines.flow.Flow
import java.text.SimpleDateFormat
import java.util.*

/**
 * Repository for SOS packet operations
 * Handles local database and remote API interactions
 */
class SosRepository(
    private val sosPacketDao: SosPacketDao
) {
    
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }
    
    // ============ Local operations ============
    
    /**
     * Save a new SOS packet (own or received)
     */
    suspend fun saveSosPacket(packet: SosPacket) {
        sosPacketDao.insert(packet)
    }
    
    /**
     * Save packet only if it doesn't exist (deduplication)
     * Returns true if packet was inserted
     */
    suspend fun saveIfNotExists(packet: SosPacket): Boolean {
        return sosPacketDao.insertIfNotExists(packet) != -1L
    }
    
    /**
     * Check if packet exists (for deduplication)
     */
    suspend fun exists(sosId: UUID): Boolean {
        return sosPacketDao.exists(sosId)
    }
    
    /**
     * Update an existing packet if the new data is more complete
     * (e.g., has valid location, deviceId, etc.)
     */
    suspend fun updatePacketIfBetter(newPacket: SosPacket) {
        val existing = sosPacketDao.getById(newPacket.sosId) ?: return
        
        // Check if new packet has better data
        val hasValidLocation = newPacket.latitude != 0.0 || newPacket.longitude != 0.0
        val existingHasValidLocation = existing.latitude != 0.0 || existing.longitude != 0.0
        val hasValidDeviceId = newPacket.deviceId != "unknown"
        val existingHasValidDeviceId = existing.deviceId != "unknown"
        
        // Update if new packet has more complete information
        if ((hasValidLocation && !existingHasValidLocation) || 
            (hasValidDeviceId && !existingHasValidDeviceId)) {
            val updated = existing.copy(
                latitude = if (hasValidLocation) newPacket.latitude else existing.latitude,
                longitude = if (hasValidLocation) newPacket.longitude else existing.longitude,
                accuracy = newPacket.accuracy ?: existing.accuracy,
                deviceId = if (hasValidDeviceId) newPacket.deviceId else existing.deviceId,
                optionalMessage = newPacket.optionalMessage ?: existing.optionalMessage,
                batteryPercentage = newPacket.batteryPercentage ?: existing.batteryPercentage
            )
            sosPacketDao.update(updated)
        }
    }
    
    /**
     * Get all packets as Flow
     */
    fun getAllPackets(): Flow<List<SosPacket>> {
        return sosPacketDao.getAllPackets()
    }
    
    /**
     * Get own sent packets
     */
    fun getOwnPackets(): Flow<List<SosPacket>> {
        return sosPacketDao.getOwnPackets()
    }
    
    /**
     * Get received packets from other devices
     */
    fun getReceivedPackets(): Flow<List<SosPacket>> {
        return sosPacketDao.getReceivedPackets()
    }
    
    /**
     * Get active (non-responded) packets
     */
    fun getActivePackets(): Flow<List<SosPacket>> {
        return sosPacketDao.getActivePackets()
    }
    
    /**
     * Get recent packet IDs for deduplication cache
     */
    suspend fun getRecentIds(maxAgeMs: Long = 60 * 60 * 1000): List<UUID> {
        val minTimestamp = System.currentTimeMillis() - maxAgeMs
        return sosPacketDao.getRecentIds(minTimestamp)
    }
    
    // ============ Sync operations ============
    
    /**
     * Get packets that need to be uploaded
     */
    suspend fun getUndeliveredPackets(): List<SosPacket> {
        return sosPacketDao.getUndeliveredPackets()
    }
    
    /**
     * Upload a packet to the backend server
     * Returns true if successful
     */
    suspend fun uploadPacket(packet: SosPacket): Boolean {
        return try {
            val request = UploadSosRequest(
                sos_id = packet.sosId.toString(),
                device_id = packet.deviceId,
                timestamp = dateFormat.format(Date(packet.timestamp)),
                latitude = packet.latitude,
                longitude = packet.longitude,
                accuracy = packet.accuracy?.toDouble(),
                emergency_type = packet.emergencyType.name.lowercase(),
                optional_message = packet.optionalMessage,
                battery_percentage = packet.batteryPercentage,
                hop_count = packet.hopCount,
                ttl = packet.ttl,
                signature = packet.signature
            )
            
            val response = ApiClient.api.uploadSos(ApiClient.apiKey, request)
            
            if (response.isSuccessful && response.body()?.success == true) {
                sosPacketDao.markAsDelivered(packet.sosId)
                true
            } else {
                false
            }
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
    
    /**
     * Sync all undelivered packets to the server
     * Returns number of successfully uploaded packets
     */
    suspend fun syncUndeliveredPackets(): Int {
        val undelivered = getUndeliveredPackets()
        var successCount = 0
        
        for (packet in undelivered) {
            if (uploadPacket(packet)) {
                successCount++
            }
        }
        
        return successCount
    }
    
    /**
     * Mark packet as responded
     */
    suspend fun markAsResponded(sosId: UUID) {
        sosPacketDao.markAsResponded(sosId)
    }
    
    // ============ Cleanup ============
    
    /**
     * Delete old packets (older than specified hours)
     */
    suspend fun cleanupOldPackets(maxAgeHours: Int = 72) {
        val cutoff = System.currentTimeMillis() - (maxAgeHours * 60 * 60 * 1000L)
        sosPacketDao.deleteOldPackets(cutoff)
    }
}
