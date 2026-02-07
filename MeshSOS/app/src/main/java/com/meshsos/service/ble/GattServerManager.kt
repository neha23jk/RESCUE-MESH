package com.meshsos.service.ble

import android.annotation.SuppressLint
import android.bluetooth.*
import android.content.Context
import android.util.Log
import com.meshsos.data.model.MinimalSosPacket
import com.meshsos.data.model.SosPacket
import com.meshsos.data.model.toMinimal
import java.util.*

/**
 * Manages BLE GATT server for exposing full SOS packet data
 * 
 * This server allows connected clients to read the full SOS packet
 * data in minimal JSON format via a GATT characteristic.
 */
@SuppressLint("MissingPermission")
class GattServerManager(private val context: Context) {
    
    companion object {
        private const val TAG = "GattServerManager"
        
        // Max GATT MTU size (typically 512 bytes, but using conservative 500)
        private const val MAX_PACKET_SIZE = 500
    }
    
    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private var gattServer: BluetoothGattServer? = null
    private var currentPacket: SosPacket? = null
    private var packetCharacteristic: BluetoothGattCharacteristic? = null
    
    private val gattServerCallback = object : BluetoothGattServerCallback() {
        
        override fun onConnectionStateChange(device: BluetoothDevice, status: Int, newState: Int) {
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    Log.d(TAG, "GATT client connected: ${device.address}")
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    Log.d(TAG, "GATT client disconnected: ${device.address}")
                }
            }
        }
        
        override fun onCharacteristicReadRequest(
            device: BluetoothDevice,
            requestId: Int,
            offset: Int,
            characteristic: BluetoothGattCharacteristic
        ) {
                if (characteristic.uuid == SosBeaconCodec.PACKET_CHARACTERISTIC_UUID) {
                val packet = currentPacket
                if (packet != null) {
                    try {
                        // Encode as binary format (~70% smaller than JSON)
                        val data = BinaryPacketCodec.encode(packet)
                        
                        if (data == null) {
                            Log.e(TAG, "Failed to encode packet as binary")
                            gattServer?.sendResponse(
                                device,
                                requestId,
                                BluetoothGatt.GATT_FAILURE,
                                offset,
                                null
                            )
                            return
                        }
                        
                        // Check size limit
                        if (data.size > MAX_PACKET_SIZE) {
                            Log.w(TAG, "Packet too large: ${data.size} bytes")
                            gattServer?.sendResponse(
                                device,
                                requestId,
                                BluetoothGatt.GATT_FAILURE,
                                offset,
                                null
                            )
                            return
                        }
                        
                        // Send response with offset support for large packets
                        val responseData = if (offset >= data.size) {
                            ByteArray(0)
                        } else {
                            data.copyOfRange(offset, data.size)
                        }
                        
                        gattServer?.sendResponse(
                            device,
                            requestId,
                            BluetoothGatt.GATT_SUCCESS,
                            offset,
                            responseData
                        )
                        
                        Log.d(TAG, "Sent binary packet data to ${device.address}: ${data.size} bytes")
                    } catch (e: Exception) {
                        Log.e(TAG, "Error sending packet data", e)
                        gattServer?.sendResponse(
                            device,
                            requestId,
                            BluetoothGatt.GATT_FAILURE,
                            offset,
                            null
                        )
                    }
                } else {
                    // No packet available
                    gattServer?.sendResponse(
                        device,
                        requestId,
                        BluetoothGatt.GATT_FAILURE,
                        offset,
                        null
                    )
                }
            } else {
                // Unknown characteristic
                gattServer?.sendResponse(
                    device,
                    requestId,
                    BluetoothGatt.GATT_FAILURE,
                    offset,
                    null
                )
            }
        }
    }
    
    /**
     * Start GATT server with SOS packet service
     */
    fun start(packet: SosPacket) {
        if (gattServer != null) {
            Log.d(TAG, "GATT server already running, updating packet")
            currentPacket = packet
            return
        }
        
        currentPacket = packet
        
        try {
            // Create GATT server
            gattServer = bluetoothManager.openGattServer(context, gattServerCallback)
            
            // Create characteristic for packet data
            packetCharacteristic = BluetoothGattCharacteristic(
                SosBeaconCodec.PACKET_CHARACTERISTIC_UUID,
                BluetoothGattCharacteristic.PROPERTY_READ,
                BluetoothGattCharacteristic.PERMISSION_READ
            )
            
            // Create service
            val service = BluetoothGattService(
                SosBeaconCodec.SERVICE_UUID,
                BluetoothGattService.SERVICE_TYPE_PRIMARY
            )
            
            service.addCharacteristic(packetCharacteristic)
            
            // Add service to server
            val success = gattServer?.addService(service) ?: false
            
            if (success) {
                Log.d(TAG, "✅ GATT server started successfully")
            } else {
                Log.e(TAG, "❌ Failed to add GATT service")
                stop()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error starting GATT server", e)
            stop()
        }
    }
    
    /**
     * Update the current packet being served
     */
    fun updatePacket(packet: SosPacket) {
        currentPacket = packet
        Log.d(TAG, "Updated current packet: ${packet.sosId}")
    }
    
    /**
     * Stop GATT server
     */
    fun stop() {
        try {
            gattServer?.clearServices()
            gattServer?.close()
            gattServer = null
            packetCharacteristic = null
            currentPacket = null
            Log.d(TAG, "GATT server stopped")
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping GATT server", e)
        }
    }
    
    /**
     * Check if server is running
     */
    fun isRunning(): Boolean = gattServer != null
}
