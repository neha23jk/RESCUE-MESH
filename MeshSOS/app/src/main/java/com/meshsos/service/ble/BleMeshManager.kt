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
    private val onBeaconReceived: (DecodedBeacon, String) -> Unit
) {
    
    companion object {
        private const val TAG = "BleMeshManager"
        
        // Scan settings
        private const val SCAN_PERIOD_MS = 10000L // 10 seconds
        private const val SCAN_INTERVAL_NORMAL_MS = 5000L // 5 seconds between scans
        private const val SCAN_INTERVAL_LOW_POWER_MS = 15000L // 15 seconds when low battery
        
        // Advertise settings
        private const val ADVERTISE_DURATION_MS = 30000 // 30 seconds per beacon
        
        // Presence beacon UUID - for device discovery (separate from SOS beacons)
        val PRESENCE_SERVICE_UUID: UUID = UUID.fromString("0000FE52-0000-1000-8000-00805F9B34FB")
        
        // Device timeout for nearby devices tracking
        private const val DEVICE_TIMEOUT_MS = 60_000L // 60 seconds
        private const val DEVICE_CLEANUP_INTERVAL_MS = 10_000L // 10 seconds
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
    
    // Bloom filter for memory-efficient deduplication
    private val bloomFilter = BloomFilter(expectedItems = 10_000, falsePositiveRate = 0.01)
    
    // LRU backup cache for recent beacons (prevents false-positive rejections)
    private val recentBeaconsLru = object : LinkedHashMap<UUID, Long>(100, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<UUID, Long>): Boolean {
            return size > 100
        }
    }
    private val BEACON_CACHE_DURATION_MS = 60_000L // 1 minute
    private val BLOOM_FILTER_RESET_INTERVAL_MS = 60 * 60 * 1000L // 1 hour
    
    // Currently advertising beacons
    private val activeAdvertisements = ConcurrentHashMap<UUID, AdvertisingSetCallback>()
    
    // Presence advertising callback
    private var presenceAdvertiseCallback: AdvertiseCallback? = null
    private var isPresenceAdvertising = false
    
    // Low power mode flag
    private var lowPowerMode = false
    
    // Track device last-seen timestamps for timeout-based cleanup
    private val nearbyDeviceTimestamps = ConcurrentHashMap<String, Long>()
    private var deviceCleanupRunnable: Runnable? = null
    
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
        
        // Encode packet directly (includes GPS coordinates)
        val beaconData = SosBeaconCodec.encode(packet, hasInternet, isResponder)
        
        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
            .setConnectable(false) // No GATT connections needed
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
                Log.d(TAG, "✅ Started advertising SOS beacon: ${packet.sosId}")
                Log.d(TAG, "   Beacon data size: ${beaconData.size} bytes")
                _isAdvertising.value = true
                
                // Schedule stop after duration
                handler.postDelayed({
                    stopAdvertisingBeacon(packet.sosId)
                }, ADVERTISE_DURATION_MS.toLong())
            }
            
            override fun onStartFailure(errorCode: Int) {
                val errorMsg = when(errorCode) {
                    ADVERTISE_FAILED_DATA_TOO_LARGE -> "DATA_TOO_LARGE"
                    ADVERTISE_FAILED_TOO_MANY_ADVERTISERS -> "TOO_MANY_ADVERTISERS"
                    ADVERTISE_FAILED_ALREADY_STARTED -> "ALREADY_STARTED"
                    ADVERTISE_FAILED_INTERNAL_ERROR -> "INTERNAL_ERROR"
                    ADVERTISE_FAILED_FEATURE_UNSUPPORTED -> "FEATURE_UNSUPPORTED"
                    else -> "UNKNOWN($errorCode)"
                }
                Log.e(TAG, "❌ Failed to start SOS advertising: $errorMsg (code=$errorCode)")
                Log.e(TAG, "   Beacon data size was: ${beaconData.size} bytes")
                activeAdvertisements.remove(packet.sosId)
            }
        }
        
        Log.d(TAG, "Starting SOS beacon advertisement for: ${packet.sosId}")
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
    
    // ============ Presence Advertising ============
    
    /**
     * Start advertising presence so other devices can discover us
     */
    private fun startPresenceAdvertising() {
        if (advertiser == null || isPresenceAdvertising) return
        
        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_POWER)
            .setConnectable(false)
            .setTimeout(0) // Continuous advertising
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_MEDIUM)
            .build()
        
        val data = AdvertiseData.Builder()
            .addServiceUuid(ParcelUuid(PRESENCE_SERVICE_UUID))
            .setIncludeDeviceName(false)
            .setIncludeTxPowerLevel(false)
            .build()
        
        presenceAdvertiseCallback = object : AdvertiseCallback() {
            override fun onStartSuccess(settingsInEffect: AdvertiseSettings?) {
                Log.d(TAG, "Presence advertising started")
                isPresenceAdvertising = true
            }
            
            override fun onStartFailure(errorCode: Int) {
                Log.e(TAG, "Presence advertising failed: $errorCode")
                isPresenceAdvertising = false
            }
        }
        
        advertiser.startAdvertising(settings, data, presenceAdvertiseCallback)
    }
    
    /**
     * Stop presence advertising
     */
    private fun stopPresenceAdvertising() {
        presenceAdvertiseCallback?.let {
            advertiser?.stopAdvertising(it)
        }
        presenceAdvertiseCallback = null
        isPresenceAdvertising = false
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
        val deviceAddress = result.device.address
        val scanRecord = result.scanRecord ?: return
        
        // Check for presence beacon first (device discovery)
        val hasPresenceService = scanRecord.serviceUuids?.any { 
            it.uuid == PRESENCE_SERVICE_UUID 
        } == true
        
        if (hasPresenceService) {
            // Update nearby devices count with timestamp
            nearbyDeviceTimestamps[deviceAddress] = System.currentTimeMillis()
            _nearbyDevices.value = nearbyDeviceTimestamps.keys.toSet()
            Log.d(TAG, "Discovered mesh node: $deviceAddress, total: ${_nearbyDevices.value.size}")
        }
        
        // Check for SOS beacon
        val serviceData = scanRecord.getServiceData(ParcelUuid(SosBeaconCodec.SERVICE_UUID))
        if (serviceData != null) {
            val beacon = SosBeaconCodec.decode(serviceData)
            if (beacon != null) {
                // Update nearby devices count with timestamp
                nearbyDeviceTimestamps[deviceAddress] = System.currentTimeMillis()
                _nearbyDevices.value = nearbyDeviceTimestamps.keys.toSet()
                
                // Deduplication - check if we've seen this recently
                if (isInDeduplicationCache(beacon.sosId)) {
                    return // Already processed recently
                }
                
                addToDeduplicationCache(beacon.sosId)
                
                // Clean old entries
                cleanupBeaconCache()
                
                // Notify listener with full beacon data (includes GPS coordinates)
                Log.d(TAG, "Received SOS beacon: ${beacon.sosId}, hop=${beacon.hopCount}, lat=${beacon.latitude}, lon=${beacon.longitude}")
                onBeaconReceived(beacon, deviceAddress)
            }
        }
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
        
        // Scan for both presence beacons and SOS beacons
        val filters = listOf(
            ScanFilter.Builder()
                .setServiceUuid(ParcelUuid(SosBeaconCodec.SERVICE_UUID))
                .build(),
            ScanFilter.Builder()
                .setServiceUuid(ParcelUuid(PRESENCE_SERVICE_UUID))
                .build()
        )
        
        val settings = ScanSettings.Builder()
            .setScanMode(
                if (lowPowerMode) ScanSettings.SCAN_MODE_LOW_POWER
                else ScanSettings.SCAN_MODE_LOW_LATENCY
            )
            .setReportDelay(0)
            .build()
        
        // Start presence advertising so others can find us
        startPresenceAdvertising()
        
        scanner.startScan(filters, settings, scanCallback)
        _isScanning.value = true
        
        Log.d(TAG, "Started BLE scanning and presence advertising")
        
        // Start periodic cleanup of nearby devices
        startDeviceCleanup()
        
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
        stopDeviceCleanup()
        nearbyDeviceTimestamps.clear()
        _nearbyDevices.value = emptySet()
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
     * Clean up old entries from beacon cache and reset bloom filter if needed
     */
    private fun cleanupBeaconCache() {
        val now = System.currentTimeMillis()
        
        // Clean LRU cache
        synchronized(recentBeaconsLru) {
            recentBeaconsLru.entries.removeIf { now - it.value > BEACON_CACHE_DURATION_MS }
        }
        
        // Reset bloom filter if it's been too long
        if (bloomFilter.shouldReset(BLOOM_FILTER_RESET_INTERVAL_MS)) {
            Log.d(TAG, "Resetting bloom filter (saturation: ${(bloomFilter.saturationRatio() * 100).toInt()}%)")
            bloomFilter.reset()
        }
    }
    
    /**
     * Add beacon ID to deduplication cache (Bloom filter + LRU backup)
     */
    fun addToDeduplicationCache(sosId: UUID) {
        bloomFilter.add(sosId)
        synchronized(recentBeaconsLru) {
            recentBeaconsLru[sosId] = System.currentTimeMillis()
        }
        Log.d(TAG, "Added to dedup cache: $sosId (bloom items: ${bloomFilter.approximateCount()})")
    }
    
    /**
     * Check if beacon is in deduplication cache
     * Uses LRU cache first (no false positives), then Bloom filter
     */
    fun isInDeduplicationCache(sosId: UUID): Boolean {
        // Check LRU cache first (recent items, no false positives)
        synchronized(recentBeaconsLru) {
            val lastSeen = recentBeaconsLru[sosId]
            if (lastSeen != null && System.currentTimeMillis() - lastSeen < BEACON_CACHE_DURATION_MS) {
                return true
            }
        }
        
        // Check Bloom filter (may have false positives, but that's acceptable)
        return bloomFilter.mightContain(sosId)
    }
    
    /**
     * Start periodic cleanup of nearby devices
     */
    private fun startDeviceCleanup() {
        stopDeviceCleanup() // Stop any existing cleanup
        
        deviceCleanupRunnable = object : Runnable {
            override fun run() {
                cleanupNearbyDevices()
                handler.postDelayed(this, DEVICE_CLEANUP_INTERVAL_MS)
            }
        }
        
        handler.post(deviceCleanupRunnable!!)
    }
    
    /**
     * Stop periodic cleanup of nearby devices
     */
    private fun stopDeviceCleanup() {
        deviceCleanupRunnable?.let {
            handler.removeCallbacks(it)
        }
        deviceCleanupRunnable = null
    }
    
    /**
     * Clean up nearby devices that haven't been seen recently
     */
    private fun cleanupNearbyDevices() {
        val now = System.currentTimeMillis()
        val initialSize = nearbyDeviceTimestamps.size
        
        nearbyDeviceTimestamps.entries.removeIf { (deviceAddress, lastSeen) ->
            val isStale = now - lastSeen > DEVICE_TIMEOUT_MS
            if (isStale) {
                Log.d(TAG, "Removing stale device: $deviceAddress (not seen for ${(now - lastSeen) / 1000}s)")
            }
            isStale
        }
        
        val removed = initialSize - nearbyDeviceTimestamps.size
        if (removed > 0) {
            _nearbyDevices.value = nearbyDeviceTimestamps.keys.toSet()
            Log.d(TAG, "Cleaned up $removed stale devices, ${nearbyDeviceTimestamps.size} remain")
        }
    }
    
    /**
     * Clean up resources
     */
    fun cleanup() {
        stopScanning()
        stopAllAdvertising()
        stopPresenceAdvertising()
        nearbyDeviceTimestamps.clear()
        _nearbyDevices.value = emptySet()
        handler.removeCallbacksAndMessages(null)
    }
}
