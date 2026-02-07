package com.meshsos.data.remote

import android.content.Context
import android.content.SharedPreferences
import com.meshsos.BuildConfig

/**
 * Manages API settings including custom server URL
 */
object ApiSettings {
    
    private const val PREFS_NAME = "meshsos_api_settings"
    private const val KEY_CUSTOM_URL = "custom_api_url"
    private const val KEY_USE_CUSTOM_URL = "use_custom_url"
    
    private var prefs: SharedPreferences? = null
    
    /**
     * Initialize settings with application context
     */
    fun init(context: Context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }
    
    /**
     * Get the current API base URL
     */
    fun getBaseUrl(): String {
        val useCustom = prefs?.getBoolean(KEY_USE_CUSTOM_URL, false) ?: false
        return if (useCustom) {
            prefs?.getString(KEY_CUSTOM_URL, BuildConfig.API_BASE_URL) ?: BuildConfig.API_BASE_URL
        } else {
            BuildConfig.API_BASE_URL
        }
    }
    
    /**
     * Set a custom API URL
     */
    fun setCustomUrl(url: String) {
        prefs?.edit()?.apply {
            putString(KEY_CUSTOM_URL, url)
            putBoolean(KEY_USE_CUSTOM_URL, true)
            apply()
        }
    }
    
    /**
     * Get the custom URL (may be empty)
     */
    fun getCustomUrl(): String {
        return prefs?.getString(KEY_CUSTOM_URL, "") ?: ""
    }
    
    /**
     * Check if using custom URL
     */
    fun isUsingCustomUrl(): Boolean {
        return prefs?.getBoolean(KEY_USE_CUSTOM_URL, false) ?: false
    }
    
    /**
     * Clear custom URL and use default
     */
    fun useDefaultUrl() {
        prefs?.edit()?.apply {
            putBoolean(KEY_USE_CUSTOM_URL, false)
            apply()
        }
    }
    
    /**
     * Get the default URL from build config
     */
    fun getDefaultUrl(): String = BuildConfig.API_BASE_URL
}
