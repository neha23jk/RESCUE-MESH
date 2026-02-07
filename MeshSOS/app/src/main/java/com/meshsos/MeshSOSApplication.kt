package com.meshsos

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import com.meshsos.data.local.MeshSOSDatabase
import com.meshsos.data.remote.ApiSettings
import com.meshsos.data.repository.SosRepository

/**
 * MeshSOS Application class
 * Initializes database and notification channels
 */
class MeshSOSApplication : Application() {
    
    // Lazy initialization of database
    val database: MeshSOSDatabase by lazy {
        MeshSOSDatabase.getInstance(this)
    }
    
    // Repository
    val sosRepository: SosRepository by lazy {
        SosRepository(database.sosPacketDao())
    }
    
    override fun onCreate() {
        super.onCreate()
        instance = this
        ApiSettings.init(this)
        createNotificationChannels()
    }
    
    /**
     * Create notification channels for Android 8.0+
     */
    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            
            // Mesh service channel
            val meshChannel = NotificationChannel(
                CHANNEL_MESH_SERVICE,
                "Mesh Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Notifications for mesh network service"
                setShowBadge(false)
            }
            
            // SOS alert channel
            val sosChannel = NotificationChannel(
                CHANNEL_SOS_ALERT,
                "SOS Alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Urgent SOS alerts from nearby users"
                enableVibration(true)
                enableLights(true)
            }
            
            notificationManager.createNotificationChannel(meshChannel)
            notificationManager.createNotificationChannel(sosChannel)
        }
    }
    
    companion object {
        const val CHANNEL_MESH_SERVICE = "mesh_service"
        const val CHANNEL_SOS_ALERT = "sos_alert"
        
        @Volatile
        private var instance: MeshSOSApplication? = null
        
        fun getInstance(): MeshSOSApplication {
            return instance ?: throw IllegalStateException("Application not initialized")
        }
    }
}
