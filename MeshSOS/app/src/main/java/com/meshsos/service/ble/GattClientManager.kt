package com.meshsos.service.ble

import android.annotation.SuppressLint
import android.bluetooth.*
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.meshsos.data.model.MinimalSosPacket
import com.meshsos.data.model.SosPacket
import java.util.*
import java.util.concurrent.ConcurrentHashMap

/**
 * Manages BLE GATT client connections to fetch full SOS packet data
 * 
 * This client connects to advertising devices to read full packet details
 * from their GATT characteristic.
 */
@SuppressLint("MissingPermission")
class GattClientManager(
    private val context: Context,
    private val onPacketReceived: (SosPacket, String) -> Unit
) {
    
    companion object {
        private const val TAG = "GattClientManager"
        private const val CONNECTION_TIMEOUT_MS = 10_000L
        private const val MAX_CONCURRENT_CONNECTIONS = 2
    }
    
    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val bluetoothAdapter = bluetoothManager.adapter
    private val handler = Handler(Looper.getMainLooper())
    
    // Track active connections
    private val activeConnections = ConcurrentHashMap<String, BluetoothGatt>()
    private val connectionQueue = LinkedList<PendingConnection>()
    private val connectedDevices = mutableSetOf<String>()
    
    private data class PendingConnection(
        val deviceAddress: String,
        val sosId: UUID,
        val timestamp: Long = System.currentTimeMillis()
    )
    
    private val gattCallback = object : BluetoothGattCallback() {
        
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            val deviceAddress = gatt.device.address
            
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    Log.d(TAG, "Connected to GATT server: $deviceAddress")
                    connectedDevices.add(deviceAddress)
                    // Discover services
                    gatt.discoverServices()
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    Log.d(TAG, "Disconnected from GATT server: $deviceAddress")
                    connectedDevices.remove(deviceAddress)
                    activeConnections.remove(deviceAddress)
                    gatt.close()
                    processQueue()
                }
            }
        }
        
        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.d(TAG, "Services discovered: ${gatt.device.address}")
                
                // Find the SOS packet service and characteristic
                val service = gatt.getService(SosBeaconCodec.SERVICE_UUID)
                if (service != null) {
                    val characteristic = service.getCharacteristic(SosBeaconCodec.PACKET_CHARACTERISTIC_UUID)
                    if (characteristic != null) {
                        // Read the characteristic
                        gatt.readCharacteristic(characteristic)
                    } else {
                        Log.w(TAG, "Packet characteristic not found")
                        disconnect(gatt.device.address)
                    }
                } else {
                    Log.w(TAG, "SOS service not found")
                    disconnect(gatt.device.address)
                }
            } else {
                Log.e(TAG, "Service discovery failed: $status")
                disconnect(gatt.device.address)
            }
        }
        
        override fun onCharacteristicRead(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int
        ) {
            @Suppress("DEPRECATION")
            val value = characteristic.value
            onCharacteristicRead(gatt, characteristic, value, status)
        }
        
        override fun onCharacteristicRead(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray,
            status: Int
        ) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                try {
                    Log.d(TAG, "Received packet data (${value.size} bytes)")
                    
                    // Try binary format first, fall back to JSON for backward compatibility
                    val sosPacket = BinaryPacketCodec.decodeWithFallback(
                        data = value,
                        deviceId = gatt.device.address
                    )
                    
                    if (sosPacket != null) {
                        Log.d(TAG, "✅ Successfully retrieved full packet: ${sosPacket.sosId}")
                        onPacketReceived(sosPacket, gatt.device.address)
                    } else {
                        Log.e(TAG, "Failed to decode packet (tried binary + JSON fallback)")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error processing packet data", e)
                }
            } else {
                Log.e(TAG, "Characteristic read failed: $status")
            }
            
            // Disconnect after reading
            disconnect(gatt.device.address)
        }
    }
    
    /**
     * Request to fetch full packet data from a device
     */
    fun fetchPacketFromDevice(deviceAddress: String, sosId: UUID) {
        // Avoid duplicate connections
        if (activeConnections.containsKey(deviceAddress) || connectedDevices.contains(deviceAddress)) {
            Log.d(TAG, "Already connected or connecting to $deviceAddress")
            return
        }
        
        // Add to queue
        val pending = PendingConnection(deviceAddress, sosId)
        connectionQueue.offer(pending)
        
        Log.d(TAG, "Queued connection to $deviceAddress, queue size: ${connectionQueue.size}")
        
        // Process queue
        processQueue()
    }
    
    /**
     * Process connection queue
     */
    private fun processQueue() {
        // Limit concurrent connections
        if (activeConnections.size >= MAX_CONCURRENT_CONNECTIONS) {
            return
        }
        
        val pending = connectionQueue.poll() ?: return
        
        try {
            val device = bluetoothAdapter?.getRemoteDevice(pending.deviceAddress)
            if (device == null) {
                Log.e(TAG, "Failed to get remote device: ${pending.deviceAddress}")
                processQueue() // Try next in queue
                return
            }
            
            Log.d(TAG, "Connecting to ${pending.deviceAddress}...")
            
            val gatt = device.connectGatt(
                context,
                false, // autoConnect = false for faster connection
                gattCallback,
                BluetoothDevice.TRANSPORT_LE
            )
            
            if (gatt != null) {
                activeConnections[pending.deviceAddress] = gatt
                
                // Set timeout
                handler.postDelayed({
                    if (activeConnections.containsKey(pending.deviceAddress)) {
                        Log.w(TAG, "Connection timeout for ${pending.deviceAddress}")
                        disconnect(pending.deviceAddress)
                    }
                }, CONNECTION_TIMEOUT_MS)
            } else {
                Log.e(TAG, "Failed to create GATT connection")
                processQueue() // Try next in queue
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error connecting to device", e)
            processQueue() // Try next in queue
        }
    }
    
    /**
     * Disconnect from a device
     */
    private fun disconnect(deviceAddress: String) {
        activeConnections[deviceAddress]?.let { gatt ->
            try {
                gatt.disconnect()
                // Note: actual cleanup happens in onConnectionStateChange
            } catch (e: Exception) {
                Log.e(TAG, "Error disconnecting from $deviceAddress", e)
            }
        }
    }
    
    /**
     * Clean up all connections
     */
    fun cleanup() {
        handler.removeCallbacksAndMessages(null)
        
        activeConnections.values.forEach { gatt ->
            try {
                gatt.disconnect()
                gatt.close()
            } catch (e: Exception) {
                Log.e(TAG, "Error closing GATT connection", e)
            }
        }
        
        activeConnections.clear()
        connectedDevices.clear()
        connectionQueue.clear()
        
        Log.d(TAG, "GATT client cleaned up")
    }
}
