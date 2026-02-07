package com.meshsos.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.meshsos.data.model.SosPacket

/**
 * Room database for MeshSOS application
 */
@Database(
    entities = [SosPacket::class],
    version = 1,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class MeshSOSDatabase : RoomDatabase() {
    
    abstract fun sosPacketDao(): SosPacketDao
    
    companion object {
        private const val DATABASE_NAME = "meshsos_database"
        
        @Volatile
        private var INSTANCE: MeshSOSDatabase? = null
        
        fun getInstance(context: Context): MeshSOSDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    MeshSOSDatabase::class.java,
                    DATABASE_NAME
                )
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
