package com.meshsos.service.location

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.os.Looper
import android.util.Log
import com.google.android.gms.location.*
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Location service using FusedLocationProviderClient
 * Provides current location with fallback to last known location
 */
@SuppressLint("MissingPermission")
class LocationService(context: Context) {
    
    companion object {
        private const val TAG = "LocationService"
        private const val UPDATE_INTERVAL_MS = 10000L // 10 seconds
        private const val FASTEST_INTERVAL_MS = 5000L // 5 seconds
    }
    
    private val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)
    
    private val _currentLocation = MutableStateFlow<Location?>(null)
    val currentLocation: StateFlow<Location?> = _currentLocation
    
    private val _isLocationAvailable = MutableStateFlow(false)
    val isLocationAvailable: StateFlow<Boolean> = _isLocationAvailable
    
    private var locationCallback: LocationCallback? = null
    
    /**
     * Get location as a one-shot request
     * First tries current location, falls back to last known
     */
    suspend fun getLocation(): Location? {
        return try {
            getCurrentLocation() ?: getLastKnownLocation()
        } catch (e: Exception) {
            Log.e(TAG, "Error getting location", e)
            getLastKnownLocation()
        }
    }
    
    /**
     * Get current location with high accuracy
     */
    @SuppressLint("MissingPermission")
    private suspend fun getCurrentLocation(): Location? = suspendCancellableCoroutine { cont ->
        val request = CurrentLocationRequest.Builder()
            .setPriority(Priority.PRIORITY_HIGH_ACCURACY)
            .setMaxUpdateAgeMillis(30000) // 30 seconds
            .setDurationMillis(10000) // Wait up to 10 seconds
            .build()
        
        fusedLocationClient.getCurrentLocation(request, null)
            .addOnSuccessListener { location ->
                if (cont.isActive) {
                    _currentLocation.value = location
                    _isLocationAvailable.value = location != null
                    cont.resume(location)
                }
            }
            .addOnFailureListener { e ->
                if (cont.isActive) {
                    cont.resumeWithException(e)
                }
            }
    }
    
    /**
     * Get last known location (may be stale)
     */
    @SuppressLint("MissingPermission")
    private suspend fun getLastKnownLocation(): Location? = suspendCancellableCoroutine { cont ->
        fusedLocationClient.lastLocation
            .addOnSuccessListener { location ->
                if (cont.isActive) {
                    _currentLocation.value = location
                    _isLocationAvailable.value = location != null
                    cont.resume(location)
                }
            }
            .addOnFailureListener { e ->
                if (cont.isActive) {
                    cont.resume(null)
                }
            }
    }
    
    /**
     * Start continuous location updates
     */
    fun startLocationUpdates(): Flow<Location> = callbackFlow {
        val request = LocationRequest.Builder(UPDATE_INTERVAL_MS)
            .setPriority(Priority.PRIORITY_HIGH_ACCURACY)
            .setMinUpdateIntervalMillis(FASTEST_INTERVAL_MS)
            .build()
        
        val callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.lastLocation?.let { location ->
                    _currentLocation.value = location
                    _isLocationAvailable.value = true
                    trySend(location)
                }
            }
            
            override fun onLocationAvailability(availability: LocationAvailability) {
                _isLocationAvailable.value = availability.isLocationAvailable
            }
        }
        
        locationCallback = callback
        fusedLocationClient.requestLocationUpdates(request, callback, Looper.getMainLooper())
        
        awaitClose {
            fusedLocationClient.removeLocationUpdates(callback)
            locationCallback = null
        }
    }
    
    /**
     * Stop location updates
     */
    fun stopLocationUpdates() {
        locationCallback?.let {
            fusedLocationClient.removeLocationUpdates(it)
            locationCallback = null
        }
    }
    
    /**
     * Get accuracy of current location in meters
     */
    fun getAccuracy(): Float? = _currentLocation.value?.accuracy
    
    /**
     * Check if current location is recent (within 5 minutes)
     */
    fun isLocationRecent(): Boolean {
        val location = _currentLocation.value ?: return false
        val ageMs = System.currentTimeMillis() - location.time
        return ageMs < 5 * 60 * 1000 // 5 minutes
    }
}
