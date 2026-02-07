package com.meshsos.data.local

import androidx.room.TypeConverter
import com.meshsos.data.model.DeliveryStatus
import com.meshsos.data.model.EmergencyType
import java.util.UUID

/**
 * Type converters for Room database
 */
class Converters {
    
    @TypeConverter
    fun fromUUID(uuid: UUID?): String? = uuid?.toString()
    
    @TypeConverter
    fun toUUID(value: String?): UUID? = value?.let { UUID.fromString(it) }
    
    @TypeConverter
    fun fromEmergencyType(type: EmergencyType): String = type.name
    
    @TypeConverter
    fun toEmergencyType(value: String): EmergencyType = 
        EmergencyType.valueOf(value)
    
    @TypeConverter
    fun fromDeliveryStatus(status: DeliveryStatus): String = status.name
    
    @TypeConverter
    fun toDeliveryStatus(value: String): DeliveryStatus = 
        DeliveryStatus.valueOf(value)
}
