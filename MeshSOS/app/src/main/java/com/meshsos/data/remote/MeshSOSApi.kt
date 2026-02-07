package com.meshsos.data.remote

import com.meshsos.data.model.EmergencyType
import retrofit2.Response
import retrofit2.http.*
import java.util.UUID

/**
 * API response models
 */
data class UploadResponse(
    val success: Boolean,
    val sos_id: String,
    val message: String
)

data class SosPacketResponse(
    val sos_id: String,
    val device_id: String,
    val timestamp: String,
    val latitude: Double,
    val longitude: Double,
    val accuracy: Double?,
    val emergency_type: String,
    val optional_message: String?,
    val battery_percentage: Int?,
    val hop_count: Int,
    val status: String,
    val received_at: String,
    val responded_at: String?
)

data class ActiveSosResponse(
    val count: Int,
    val sos_packets: List<SosPacketResponse>
)

data class UploadSosRequest(
    val sos_id: String,
    val device_id: String,
    val timestamp: String,
    val latitude: Double,
    val longitude: Double,
    val accuracy: Double?,
    val emergency_type: String,
    val optional_message: String?,
    val battery_percentage: Int?,
    val hop_count: Int,
    val ttl: Int,
    val signature: String?
)

data class MarkRespondedRequest(
    val sos_id: String,
    val responder_id: String
)

/**
 * Retrofit API interface for MeshSOS backend
 */
interface MeshSOSApi {
    
    @POST("api/v1/upload-sos")
    suspend fun uploadSos(
        @Header("X-API-Key") apiKey: String,
        @Body request: UploadSosRequest
    ): Response<UploadResponse>
    
    @GET("api/v1/active-sos")
    suspend fun getActiveSos(
        @Header("X-API-Key") apiKey: String,
        @Query("emergency_type") emergencyType: String? = null,
        @Query("hours") hours: Int = 24,
        @Query("limit") limit: Int = 100
    ): Response<ActiveSosResponse>
    
    @POST("api/v1/mark-responded")
    suspend fun markResponded(
        @Header("X-API-Key") apiKey: String,
        @Body request: MarkRespondedRequest
    ): Response<UploadResponse>
    
    @GET("api/v1/sos/{sosId}")
    suspend fun getSosById(
        @Header("X-API-Key") apiKey: String,
        @Path("sosId") sosId: String
    ): Response<SosPacketResponse>
    
    @GET("health")
    suspend fun healthCheck(): Response<Map<String, String>>
}
