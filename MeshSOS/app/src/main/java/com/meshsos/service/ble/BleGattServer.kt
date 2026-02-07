package com.meshsos.service.ble

import android.annotation.SuppressLint
import android.bluetooth.*
import android.content.Context
import android.util.Log
import com.meshsos.data.model.SosPacket
import com.google.gson.Gson
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * GATT Server for serving full SOS packet data to connecting clients
 * 
 * When a device receives an SOS beacon via advertising, it connects
 * to this GATT server to download the complete SOS packet data.
 */
@SuppressLint("MissingPermission")
class BleGattServer(private val context: Context) {
    
    companion object {
        private const val TAG = "BleGattServer"
    }
    
    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private var gattServer: BluetoothGattServer? = null
    private val gson = Gson()
    
    // Cache of packets available for download
    private val packetCache = ConcurrentHashMap<UUID, SosPacket>()
    
    // Connected devices
    private val connectedDevices = mutableSetOf<BluetoothDevice>()
    
    private val gattServerCallback = object : BluetoothGattServerCallback() {
        
        override fun onConnectionStateChange(device: BluetoothDevice, status: Int, newState: Int) {
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    Log.d(TAG, "Device connected: ${device.address}")
                    connectedDevices.add(device)
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    Log.d(TAG, "Device disconnected: ${device.address}")
                    connectedDevices.remove(device)
                }
            }
        }
        
        override fun onCharacteristicReadRequest(
            device: BluetoothDevice,
            requestId: Int,
            offset: Int,
            characteristic: BluetoothGattCharacteristic
        ) {
            Log.d(TAG, "Read request from ${device.address}, offset=$offset")
            
            if (characteristic.uuid == SosBeaconCodec.PACKET_CHARACTERISTIC_UUID) {
                // Get the packet data
                val packetData = getPacketData()
                
                if (packetData != null && offset < packetData.size) {
                    val responseData = packetData.copyOfRange(
                        offset,
                        minOf(offset + 512, packetData.size)
                    )
                    
                    gattServer?.sendResponse(
                        device,
                        requestId,
                        BluetoothGatt.GATT_SUCCESS,
                        offset,
                        responseData
                    )
                } else {
                    gattServer?.sendResponse(
                        device,
                        requestId,
                        BluetoothGatt.GATT_FAILURE,
                        0,
                        null
                    )
                }
            } else {
                gattServer?.sendResponse(
                    device,
                    requestId,
                    BluetoothGatt.GATT_REQUEST_NOT_SUPPORTED,
                    0,
                    null
                )
            }
        }
        
        override fun onDescriptorReadRequest(
            device: BluetoothDevice,
            requestId: Int,
            offset: Int,
            descriptor: BluetoothGattDescriptor
        ) {
            gattServer?.sendResponse(
                device,
                requestId,
                BluetoothGatt.GATT_SUCCESS,
                0,
                descriptor.value
            )
        }
    }
    
    /**
     * Start the GATT server
     */
    fun start() {
        gattServer = bluetoothManager.openGattServer(context, gattServerCallback)
        
        gattServer?.let { server ->
            // Create MeshSOS service
            val service = BluetoothGattService(
                SosBeaconCodec.SERVICE_UUID,
                BluetoothGattService.SERVICE_TYPE_PRIMARY
            )
            
            // Add packet characteristic (readable)
            val packetCharacteristic = BluetoothGattCharacteristic(
                SosBeaconCodec.PACKET_CHARACTERISTIC_UUID,
                BluetoothGattCharacteristic.PROPERTY_READ,
                BluetoothGattCharacteristic.PERMISSION_READ
            )
            
            service.addCharacteristic(packetCharacteristic)
            server.addService(service)
            
            Log.d(TAG, "GATT server started")
        }
    }
    
    /**
     * Stop the GATT server
     */
    fun stop() {
        connectedDevices.forEach { device ->
            gattServer?.cancelConnection(device)
        }
        connectedDevices.clear()
        gattServer?.close()
        gattServer = null
        packetCache.clear()
        Log.d(TAG, "GATT server stopped")
    }
    
    /**
     * Add a packet to the cache for serving
     */
    fun addPacket(packet: SosPacket) {
        packetCache[packet.sosId] = packet
        
        // Limit cache size
        if (packetCache.size > 100) {
            val oldestKey = packetCache.keys.firstOrNull()
            oldestKey?.let { packetCache.remove(it) }
        }
    }
    
    /**
     * Remove a packet from the cache
     */
    fun removePacket(sosId: UUID) {
        packetCache.remove(sosId)
    }
    
    /**
     * Get serialized packet data for GATT response
     */
    private fun getPacketData(): ByteArray? {
        // For simplicity, return all packets as JSON
        // In production, could use a more efficient binary format
        val packets = packetCache.values.toList()
        return if (packets.isNotEmpty()) {
            gson.toJson(packets.map { it.toTransferModel() }).toByteArray()
        } else {
            null
        }
    }
    
    /**
     * Convert SosPacket to a transfer-friendly model
     */
    private fun SosPacket.toTransferModel() = mapOf(
        "sosId" to sosId.toString(),
        "deviceId" to deviceId,
        "timestamp" to timestamp,
        "latitude" to latitude,
        "longitude" to longitude,
        "accuracy" to accuracy,
        "emergencyType" to emergencyType.name,
        "optionalMessage" to optionalMessage,
        "batteryPercentage" to batteryPercentage,
        "hopCount" to hopCount,
        "ttl" to ttl,
        "signature" to signature
    )
}
