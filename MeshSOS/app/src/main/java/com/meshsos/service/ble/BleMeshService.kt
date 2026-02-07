package com.meshsos.service.ble

import android.annotation.SuppressLint
import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.bluetooth.*
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.*
import android.util.Log
import androidx.core.app.NotificationCompat
import com.meshsos.MainActivity
import com.meshsos.MeshSOSApplication
import com.meshsos.R
import com.meshsos.data.model.DeliveryStatus
import com.meshsos.data.model.NodeRole
import com.meshsos.data.model.SosBeacon
import com.meshsos.data.model.SosPacket
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.UUID

/**
 * Foreground service for BLE mesh networking
 * 
 * Responsibilities:
 * - Manage BLE advertising and scanning
 * - Relay received SOS beacons
 * - Upload packets when internet is available
 * - Monitor battery and connectivity
 */
@SuppressLint("MissingPermission")
class BleMeshService : Service() {
    
    companion object {
        private const val TAG = "BleMeshService"
        private const val NOTIFICATION_ID = 1001
        
        // Actions
        const val ACTION_START = "com.meshsos.action.START_MESH"
        const val ACTION_STOP = "com.meshsos.action.STOP_MESH"
        const val ACTION_SEND_SOS = "com.meshsos.action.SEND_SOS"
        
        // Extras
        const val EXTRA_SOS_PACKET = "sos_packet"
        
        // Singleton state for UI access
        private val _serviceState = MutableStateFlow(ServiceState())
        val serviceState: StateFlow<ServiceState> = _serviceState
    }
    
    data class ServiceState(
        val isRunning: Boolean = false,
        val isScanning: Boolean = false,
        val isAdvertising: Boolean = false,
        val hasInternet: Boolean = false,
        val nearbyNodeCount: Int = 0,
        val nodeRole: NodeRole = NodeRole.USER
    )
    
    private val serviceScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    
    private lateinit var bleMeshManager: BleMeshManager
    private lateinit var gattServer: BleGattServer
    private lateinit var connectivityManager: ConnectivityManager
    private lateinit var batteryManager: BatteryManager
    
    private val app: MeshSOSApplication by lazy { 
        application as MeshSOSApplication 
    }
    
    private var hasInternet = false
    private var nodeRole = NodeRole.USER
    
    // ============ Service Lifecycle ============
    
    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Service created")
        
        bleMeshManager = BleMeshManager(this) { beacon, deviceAddress ->
            handleReceivedBeacon(beacon, deviceAddress)
        }
        
        gattServer = BleGattServer(this)
        connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        batteryManager = getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        
        registerConnectivityCallback()
        registerBatteryMonitor()
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startMeshService()
            ACTION_STOP -> stopMeshService()
            ACTION_SEND_SOS -> {
                // SOS packet would be passed via intent extras
                // For now, handled via repository
            }
        }
        
        return START_STICKY
    }
    
    override fun onBind(intent: Intent?): IBinder? = null
    
    override fun onDestroy() {
        super.onDestroy()
        cleanup()
    }
    
    // ============ Service Control ============
    
    private fun startMeshService() {
        Log.d(TAG, "Starting mesh service")
        
        startForeground(NOTIFICATION_ID, createNotification())
        
        if (!bleMeshManager.isBluetoothEnabled()) {
            Log.e(TAG, "Bluetooth not enabled")
            return
        }
        
        // Start GATT server for full packet transfer
        gattServer.start()
        
        // Start scanning for beacons
        bleMeshManager.startScanning()
        
        // Start observing states
        serviceScope.launch {
            bleMeshManager.isScanning.collect { isScanning ->
                updateState { it.copy(isScanning = isScanning) }
            }
        }
        
        serviceScope.launch {
            bleMeshManager.isAdvertising.collect { isAdvertising ->
                updateState { it.copy(isAdvertising = isAdvertising) }
            }
        }
        
        serviceScope.launch {
            bleMeshManager.nearbyDevices.collect { devices ->
                updateState { it.copy(nearbyNodeCount = devices.size) }
            }
        }
        
        updateState { 
            it.copy(isRunning = true, nodeRole = nodeRole) 
        }
        
        // Start periodic sync
        startPeriodicSync()
    }
    
    private fun stopMeshService() {
        Log.d(TAG, "Stopping mesh service")
        cleanup()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }
    
    private fun cleanup() {
        bleMeshManager.cleanup()
        gattServer.stop()
        serviceScope.cancel()
        updateState { ServiceState() }
    }
    
    // ============ SOS Sending ============
    
    /**
     * Send a new SOS from this device
     */
    fun sendSos(packet: SosPacket) {
        serviceScope.launch {
            // Save to local database
            app.sosRepository.saveSosPacket(packet.copy(isOwnPacket = true))
            
            // Add to deduplication cache
            bleMeshManager.addToDeduplicationCache(packet.sosId)
            
            // Add to GATT server for clients
            gattServer.addPacket(packet)
            
            // Start advertising beacon
            bleMeshManager.advertiseBeacon(packet, hasInternet, nodeRole == NodeRole.RESPONDER)
            
            // If we have internet, upload immediately
            if (hasInternet) {
                app.sosRepository.uploadPacket(packet)
            }
            
            Log.d(TAG, "SOS sent: ${packet.sosId}")
        }
    }
    
    // ============ Beacon Handling ============
    
    private fun handleReceivedBeacon(beacon: SosBeacon, deviceAddress: String) {
        serviceScope.launch {
            Log.d(TAG, "Processing beacon: ${beacon.sosId}")
            
            // Check if already in local DB
            if (app.sosRepository.exists(beacon.sosId)) {
                Log.d(TAG, "Beacon already in database, ignoring")
                return@launch
            }
            
            // Check TTL
            if (beacon.ttl <= 0) {
                Log.d(TAG, "Beacon TTL expired, not relaying")
                return@launch
            }
            
            // TODO: Connect via GATT to fetch full packet
            // For now, create a minimal packet from beacon data
            val packet = SosPacket(
                sosId = beacon.sosId,
                deviceId = "unknown", // Will be filled from GATT
                latitude = 0.0,       // Will be filled from GATT
                longitude = 0.0,      // Will be filled from GATT
                emergencyType = beacon.emergencyType,
                hopCount = beacon.hopCount,
                ttl = beacon.ttl,
                status = DeliveryStatus.RELAYED,
                isOwnPacket = false
            )
            
            // Save to database
            app.sosRepository.saveIfNotExists(packet)
            
            // Add to GATT server for other devices
            gattServer.addPacket(packet)
            
            // Relay the beacon with incremented hop count
            if (beacon.shouldRelay()) {
                val relayPacket = packet.forRelay()
                bleMeshManager.advertiseBeacon(relayPacket, hasInternet, nodeRole == NodeRole.RESPONDER)
            }
            
            // Upload if we have internet
            if (hasInternet) {
                app.sosRepository.uploadPacket(packet)
            }
        }
    }
    
    private fun SosBeacon.shouldRelay(): Boolean = ttl > 1
    
    // ============ Connectivity ============
    
    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            Log.d(TAG, "Internet available")
            hasInternet = true
            updateState { it.copy(hasInternet = true) }
            
            // Sync pending packets
            serviceScope.launch {
                val count = app.sosRepository.syncUndeliveredPackets()
                Log.d(TAG, "Synced $count packets")
            }
        }
        
        override fun onLost(network: Network) {
            Log.d(TAG, "Internet lost")
            hasInternet = false
            updateState { it.copy(hasInternet = false) }
        }
    }
    
    private fun registerConnectivityCallback() {
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        
        connectivityManager.registerNetworkCallback(request, networkCallback)
        
        // Check initial state
        hasInternet = connectivityManager.activeNetwork?.let { network ->
            connectivityManager.getNetworkCapabilities(network)
                ?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
        } == true
        
        updateState { it.copy(hasInternet = hasInternet) }
    }
    
    // ============ Battery Monitoring ============
    
    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
            val percentage = (level * 100 / scale.toFloat()).toInt()
            
            // Enable low power mode below 20%
            bleMeshManager.setLowPowerMode(percentage < 20)
        }
    }
    
    private fun registerBatteryMonitor() {
        registerReceiver(batteryReceiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
    }
    
    // ============ Periodic Sync ============
    
    private fun startPeriodicSync() {
        serviceScope.launch {
            while (isActive) {
                delay(5 * 60 * 1000) // 5 minutes
                
                if (hasInternet) {
                    val count = app.sosRepository.syncUndeliveredPackets()
                    if (count > 0) {
                        Log.d(TAG, "Periodic sync: $count packets uploaded")
                    }
                }
                
                // Cleanup old packets
                app.sosRepository.cleanupOldPackets(72)
            }
        }
    }
    
    // ============ Notification ============
    
    private fun createNotification(): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        
        return NotificationCompat.Builder(this, MeshSOSApplication.CHANNEL_MESH_SERVICE)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(getString(R.string.notification_text))
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }
    
    // ============ State Management ============
    
    private fun updateState(update: (ServiceState) -> ServiceState) {
        _serviceState.value = update(_serviceState.value)
    }
    
    /**
     * Set node role (User/Relay/Responder)
     */
    fun setNodeRole(role: NodeRole) {
        nodeRole = role
        updateState { it.copy(nodeRole = role) }
    }
}
