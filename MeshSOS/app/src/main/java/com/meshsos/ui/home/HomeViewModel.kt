package com.meshsos.ui.home

import android.app.Application
import android.content.Intent
import android.location.Location
import android.os.BatteryManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.meshsos.MeshSOSApplication
import com.meshsos.data.model.EmergencyType
import com.meshsos.data.model.NodeRole
import com.meshsos.data.model.SosPacket
import com.meshsos.service.ble.BleMeshService
import com.meshsos.service.location.LocationService
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.security.MessageDigest

/**
 * ViewModel for Home Screen
 */
class HomeViewModel(application: Application) : AndroidViewModel(application) {
    
    private val app = application as MeshSOSApplication
    private val locationService = LocationService(application)
    
    // UI State
    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()
    
    // Service state
    val serviceState = BleMeshService.serviceState
    
    // Location updates
    val currentLocation = locationService.currentLocation
    val isLocationAvailable = locationService.isLocationAvailable
    
    init {
        // Observe service state
        viewModelScope.launch {
            serviceState.collect { state ->
                _uiState.update { 
                    it.copy(
                        isMeshActive = state.isRunning,
                        isScanning = state.isScanning,
                        hasInternet = state.hasInternet,
                        nearbyNodeCount = state.nearbyNodeCount,
                        nodeRole = state.nodeRole
                    )
                }
            }
        }
        
        // Start location updates
        viewModelScope.launch {
            locationService.startLocationUpdates().collect { location ->
                _uiState.update { 
                    it.copy(
                        hasLocation = true,
                        locationAccuracy = location.accuracy
                    )
                }
            }
        }
        
        // Update battery level
        updateBatteryLevel()
    }
    
    /**
     * Start the mesh service
     */
    fun startMeshService() {
        val intent = Intent(getApplication(), BleMeshService::class.java).apply {
            action = BleMeshService.ACTION_START
        }
        getApplication<Application>().startForegroundService(intent)
    }
    
    /**
     * Stop the mesh service
     */
    fun stopMeshService() {
        val intent = Intent(getApplication(), BleMeshService::class.java).apply {
            action = BleMeshService.ACTION_STOP
        }
        getApplication<Application>().startService(intent)
    }
    
    /**
     * Send SOS with the selected emergency type
     */
    fun sendSos(emergencyType: EmergencyType, message: String? = null) {
        viewModelScope.launch {
            _uiState.update { it.copy(isSending = true) }
            
            val location = locationService.getLocation()
            
            val packet = SosPacket(
                deviceId = getDeviceIdHash(),
                latitude = location?.latitude ?: 0.0,
                longitude = location?.longitude ?: 0.0,
                accuracy = location?.accuracy,
                emergencyType = emergencyType,
                optionalMessage = message,
                batteryPercentage = getBatteryPercentage(),
                isOwnPacket = true
            )
            
            // Save to database
            app.sosRepository.saveSosPacket(packet)
            
            // Broadcast via service (will be picked up by foreground service)
            val intent = Intent(getApplication(), BleMeshService::class.java).apply {
                action = BleMeshService.ACTION_SEND_SOS
            }
            getApplication<Application>().startService(intent)
            
            _uiState.update { 
                it.copy(
                    isSending = false,
                    lastSosSent = System.currentTimeMillis()
                )
            }
        }
    }
    
    /**
     * Get SHA-256 hash of device ID for privacy
     */
    private fun getDeviceIdHash(): String {
        val androidId = android.provider.Settings.Secure.getString(
            getApplication<Application>().contentResolver,
            android.provider.Settings.Secure.ANDROID_ID
        )
        
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(androidId.toByteArray())
        return hash.joinToString("") { "%02x".format(it) }
    }
    
    /**
     * Get current battery percentage
     */
    private fun getBatteryPercentage(): Int {
        val batteryManager = getApplication<Application>()
            .getSystemService(Application.BATTERY_SERVICE) as BatteryManager
        return batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
    }
    
    /**
     * Update battery level in UI state
     */
    private fun updateBatteryLevel() {
        _uiState.update { it.copy(batteryLevel = getBatteryPercentage()) }
    }
    
    /**
     * Set node role
     */
    fun setNodeRole(role: NodeRole) {
        _uiState.update { it.copy(nodeRole = role) }
    }
}

/**
 * UI State for Home Screen
 */
data class HomeUiState(
    val isMeshActive: Boolean = false,
    val isScanning: Boolean = false,
    val hasInternet: Boolean = false,
    val hasLocation: Boolean = false,
    val locationAccuracy: Float = 0f,
    val nearbyNodeCount: Int = 0,
    val batteryLevel: Int = 100,
    val nodeRole: NodeRole = NodeRole.USER,
    val isSending: Boolean = false,
    val lastSosSent: Long? = null
)
