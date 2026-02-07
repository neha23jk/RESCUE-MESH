package com.meshsos.service.ble

import android.annotation.SuppressLint
import android.bluetooth.*
import android.bluetooth.le.*
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.ParcelUuid
import android.util.Log
import com.meshsos.data.model.EmergencyType
import com.meshsos.data.model.SosBeacon
import com.meshsos.data.model.SosPacket
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.*
import java.util.concurrent.ConcurrentHashMap

/**
 * Manages BLE advertising and scanning for SOS beacons
 */
@SuppressLint("MissingPermission")
class BleMeshManager(
    private val context: Context,
    private val onBeaconReceived: (SosBeacon, String) -> Unit
) {
    
    companion object {
        private const val TAG = "BleMeshManager"
        
        // Scan settings
        private const val SCAN_PERIOD_MS = 10000L // 10 seconds
        private const val SCAN_INTERVAL_NORMAL_MS = 5000L // 5 seconds between scans
        private const val SCAN_INTERVAL_LOW_POWER_MS = 15000L // 15 seconds when low battery
        
        // Advertise settings
        private const val ADVERTISE_DURATION_MS = 30000 // 30 seconds per beacon
    }
    
    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val bluetoothAdapter = bluetoothManager.adapter
    private val advertiser: BluetoothLeAdvertiser? = bluetoothAdapter?.bluetoothLeAdvertiser
    private val scanner: BluetoothLeScanner? = bluetoothAdapter?.bluetoothLeScanner
    
    private val handler = Handler(Looper.getMainLooper())
    
    // State
    private val _isAdvertising = MutableStateFlow(false)
    val isAdvertising: StateFlow<Boolean> = _isAdvertising
    
    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning
    
    private val _nearbyDevices = MutableStateFlow<Set<String>>(emptySet())
    val nearbyDevices: StateFlow<Set<String>> = _nearbyDevices
    
    // Deduplication cache for received beacons
    private val recentBeacons = ConcurrentHashMap<UUID, Long>()
    private val BEACON_CACHE_DURATION_MS = 60_000L // 1 minute
    
    // Currently advertising beacons
    private val activeAdvertisements = ConcurrentHashMap<UUID, AdvertisingSetCallback>()
    
    // Low power mode flag
    private var lowPowerMode = false
    
    // ============ Advertising ============
    
    /**
     * Start advertising an SOS beacon
     */
    fun advertiseBeacon(packet: SosPacket, hasInternet: Boolean, isResponder: Boolean) {
        if (advertiser == null) {
            Log.e(TAG, "BLE advertiser not available")
            return
        }
        
        if (activeAdvertisements.containsKey(packet.sosId)) {
            Log.d(TAG, "Already advertising beacon: ${packet.sosId}")
            return
        }
        
        val beacon = SosBeaconCodec.createBeacon(
            sosId = packet.sosId,
            emergencyType = packet.emergencyType,
            hopCount = packet.hopCount,
            ttl = packet.ttl,
            hasInternet = hasInternet,
            isResponder = isResponder
        )
        
        val beaconData = SosBeaconCodec.encode(beacon)
        
        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
            .setConnectable(true) // Allow GATT connections
            .setTimeout(ADVERTISE_DURATION_MS)
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
            .build()
        
        val data = AdvertiseData.Builder()
            .addServiceUuid(ParcelUuid(SosBeaconCodec.SERVICE_UUID))
            .addServiceData(ParcelUuid(SosBeaconCodec.SERVICE_UUID), beaconData)
            .setIncludeDeviceName(false)
            .setIncludeTxPowerLevel(false)
            .build()
        
        val callback = object : AdvertiseCallback() {
            override fun onStartSuccess(settingsInEffect: AdvertiseSettings?) {
                Log.d(TAG, "Started advertising beacon: ${packet.sosId}")
                _isAdvertising.value = true
                
                // Schedule stop after duration
                handler.postDelayed({
                    stopAdvertisingBeacon(packet.sosId)
                }, ADVERTISE_DURATION_MS.toLong())
            }
            
            override fun onStartFailure(errorCode: Int) {
                Log.e(TAG, "Failed to start advertising: $errorCode")
                activeAdvertisements.remove(packet.sosId)
            }
        }
        
        activeAdvertisements[packet.sosId] = object : AdvertisingSetCallback() {}
        advertiser.startAdvertising(settings, data, callback)
    }
    
    /**
     * Stop advertising a specific beacon
     */
    fun stopAdvertisingBeacon(sosId: UUID) {
        activeAdvertisements.remove(sosId)
        _isAdvertising.value = activeAdvertisements.isNotEmpty()
    }
    
    /**
     * Stop all advertising
     */
    fun stopAllAdvertising() {
        activeAdvertisements.clear()
        _isAdvertising.value = false
    }
    
    // ============ Scanning ============
    
    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            processScanResult(result)
        }
        
        override fun onBatchScanResults(results: List<ScanResult>) {
            results.forEach { processScanResult(it) }
        }
        
        override fun onScanFailed(errorCode: Int) {
            Log.e(TAG, "Scan failed: $errorCode")
            _isScanning.value = false
        }
    }
    
    private fun processScanResult(result: ScanResult) {
        val serviceData = result.scanRecord?.getServiceData(ParcelUuid(SosBeaconCodec.SERVICE_UUID))
            ?: return
        
        val beacon = SosBeaconCodec.decode(serviceData) ?: return
        
        // Update nearby devices
        val deviceAddress = result.device.address
        _nearbyDevices.value = _nearbyDevices.value + deviceAddress
        
        // Deduplication - check if we've seen this recently
        val now = System.currentTimeMillis()
        val lastSeen = recentBeacons[beacon.sosId]
        
        if (lastSeen != null && now - lastSeen < BEACON_CACHE_DURATION_MS) {
            return // Already processed recently
        }
        
        recentBeacons[beacon.sosId] = now
        
        // Clean old entries
        cleanupBeaconCache()
        
        // Notify listener
        Log.d(TAG, "Received beacon: ${beacon.sosId}, hop=${beacon.hopCount}")
        onBeaconReceived(beacon, deviceAddress)
    }
    
    /**
     * Start scanning for SOS beacons
     */
    fun startScanning() {
        if (scanner == null) {
            Log.e(TAG, "BLE scanner not available")
            return
        }
        
        if (_isScanning.value) {
            return
        }
        
        val filters = listOf(
            ScanFilter.Builder()
                .setServiceUuid(ParcelUuid(SosBeaconCodec.SERVICE_UUID))
                .build()
        )
        
        val settings = ScanSettings.Builder()
            .setScanMode(
                if (lowPowerMode) ScanSettings.SCAN_MODE_LOW_POWER
                else ScanSettings.SCAN_MODE_LOW_LATENCY
            )
            .setReportDelay(0)
            .build()
        
        scanner.startScan(filters, settings, scanCallback)
        _isScanning.value = true
        
        Log.d(TAG, "Started BLE scanning")
        
        // Stop after scan period
        handler.postDelayed({
            stopScanning()
            
            // Schedule next scan
            val nextScanDelay = if (lowPowerMode) SCAN_INTERVAL_LOW_POWER_MS else SCAN_INTERVAL_NORMAL_MS
            handler.postDelayed({ startScanning() }, nextScanDelay)
        }, SCAN_PERIOD_MS)
    }
    
    /**
     * Stop scanning
     */
    fun stopScanning() {
        scanner?.stopScan(scanCallback)
        _isScanning.value = false
        handler.removeCallbacksAndMessages(null)
    }
    
    /**
     * Set low power mode (reduces scan frequency)
     */
    fun setLowPowerMode(enabled: Boolean) {
        lowPowerMode = enabled
    }
    
    /**
     * Check if Bluetooth is enabled
     */
    fun isBluetoothEnabled(): Boolean = bluetoothAdapter?.isEnabled == true
    
    /**
     * Clean up old entries from beacon cache
     */
    private fun cleanupBeaconCache() {
        val now = System.currentTimeMillis()
        recentBeacons.entries.removeIf { now - it.value > BEACON_CACHE_DURATION_MS }
    }
    
    /**
     * Add beacon ID to deduplication cache
     */
    fun addToDeduplicationCache(sosId: UUID) {
        recentBeacons[sosId] = System.currentTimeMillis()
    }
    
    /**
     * Check if beacon is in deduplication cache
     */
    fun isInDeduplicationCache(sosId: UUID): Boolean {
        val lastSeen = recentBeacons[sosId] ?: return false
        return System.currentTimeMillis() - lastSeen < BEACON_CACHE_DURATION_MS
    }
    
    /**
     * Clean up resources
     */
    fun cleanup() {
        stopScanning()
        stopAllAdvertising()
        handler.removeCallbacksAndMessages(null)
    }
}
