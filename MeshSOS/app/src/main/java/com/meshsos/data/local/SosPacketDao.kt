package com.meshsos.data.local

import androidx.room.*
import com.meshsos.data.model.DeliveryStatus
import com.meshsos.data.model.SosPacket
import kotlinx.coroutines.flow.Flow
import java.util.UUID

/**
 * Data Access Object for SOS packets
 */
@Dao
interface SosPacketDao {
    
    // ============ Insert/Update ============
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(packet: SosPacket)
    
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIfNotExists(packet: SosPacket): Long
    
    @Update
    suspend fun update(packet: SosPacket)
    
    // ============ Queries ============
    
    @Query("SELECT * FROM sos_packets WHERE sosId = :sosId")
    suspend fun getById(sosId: UUID): SosPacket?
    
    @Query("SELECT * FROM sos_packets ORDER BY timestamp DESC")
    fun getAllPackets(): Flow<List<SosPacket>>
    
    @Query("SELECT * FROM sos_packets WHERE isOwnPacket = 1 ORDER BY timestamp DESC")
    fun getOwnPackets(): Flow<List<SosPacket>>
    
    @Query("SELECT * FROM sos_packets WHERE isOwnPacket = 0 ORDER BY timestamp DESC")
    fun getReceivedPackets(): Flow<List<SosPacket>>
    
    @Query("SELECT * FROM sos_packets WHERE status != :responded ORDER BY timestamp DESC")
    fun getActivePackets(responded: DeliveryStatus = DeliveryStatus.RESPONDED): Flow<List<SosPacket>>
    
    // ============ Sync queries ============
    
    @Query("SELECT * FROM sos_packets WHERE status = :pending OR status = :relayed")
    suspend fun getUndeliveredPackets(
        pending: DeliveryStatus = DeliveryStatus.PENDING,
        relayed: DeliveryStatus = DeliveryStatus.RELAYED
    ): List<SosPacket>
    
    @Query("UPDATE sos_packets SET status = :delivered, uploadedAt = :uploadedAt WHERE sosId = :sosId")
    suspend fun markAsDelivered(
        sosId: UUID, 
        delivered: DeliveryStatus = DeliveryStatus.DELIVERED,
        uploadedAt: Long = System.currentTimeMillis()
    )
    
    @Query("UPDATE sos_packets SET status = :responded WHERE sosId = :sosId")
    suspend fun markAsResponded(
        sosId: UUID,
        responded: DeliveryStatus = DeliveryStatus.RESPONDED
    )
    
    // ============ Deduplication ============
    
    @Query("SELECT EXISTS(SELECT 1 FROM sos_packets WHERE sosId = :sosId)")
    suspend fun exists(sosId: UUID): Boolean
    
    @Query("SELECT sosId FROM sos_packets WHERE timestamp > :minTimestamp")
    suspend fun getRecentIds(minTimestamp: Long): List<UUID>
    
    // ============ Cleanup ============
    
    @Query("DELETE FROM sos_packets WHERE timestamp < :olderThan")
    suspend fun deleteOldPackets(olderThan: Long)
    
    @Query("DELETE FROM sos_packets")
    suspend fun deleteAll()
    
    // ============ Stats ============
    
    @Query("SELECT COUNT(*) FROM sos_packets")
    fun getTotalCount(): Flow<Int>
    
    @Query("SELECT COUNT(*) FROM sos_packets WHERE status = :delivered OR status = :responded")
    fun getDeliveredCount(
        delivered: DeliveryStatus = DeliveryStatus.DELIVERED,
        responded: DeliveryStatus = DeliveryStatus.RESPONDED
    ): Flow<Int>
}
