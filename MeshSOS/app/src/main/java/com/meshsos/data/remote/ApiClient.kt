package com.meshsos.data.remote

import com.meshsos.BuildConfig
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

/**
 * Retrofit client singleton for API communication
 * Supports dynamic URL switching via ApiSettings
 */
object ApiClient {
    
    private const val TIMEOUT_SECONDS = 30L
    
    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = if (BuildConfig.DEBUG) {
            HttpLoggingInterceptor.Level.BODY
        } else {
            HttpLoggingInterceptor.Level.NONE
        }
    }
    
    private val okHttpClient = OkHttpClient.Builder()
        .addInterceptor(loggingInterceptor)
        .connectTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .readTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .writeTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .build()
    
    @Volatile
    private var currentBaseUrl: String = ""
    
    @Volatile
    private var _api: MeshSOSApi? = null
    
    /**
     * Get the API instance, rebuilding if URL has changed
     */
    val api: MeshSOSApi
        get() {
            val newBaseUrl = ApiSettings.getBaseUrl()
            if (_api == null || currentBaseUrl != newBaseUrl) {
                synchronized(this) {
                    if (_api == null || currentBaseUrl != newBaseUrl) {
                        currentBaseUrl = newBaseUrl
                        val retrofit = Retrofit.Builder()
                            .baseUrl(newBaseUrl + "/")
                            .client(okHttpClient)
                            .addConverterFactory(GsonConverterFactory.create())
                            .build()
                        _api = retrofit.create(MeshSOSApi::class.java)
                    }
                }
            }
            return _api!!
        }
    
    val apiKey: String = BuildConfig.API_KEY
    
    /**
     * Force rebuild of API client (call after URL change)
     */
    fun refresh() {
        synchronized(this) {
            _api = null
        }
    }
}
