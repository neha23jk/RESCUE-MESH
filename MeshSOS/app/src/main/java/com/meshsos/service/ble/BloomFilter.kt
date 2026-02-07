package com.meshsos.service.ble

import java.util.*
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.ln
import kotlin.math.roundToInt

/**
 * Memory-efficient Bloom filter for SOS beacon deduplication.
 * 
 * A Bloom filter is a probabilistic data structure that:
 * - Has zero false negatives (if it says "not seen", it wasn't seen)
 * - May have false positives (1% rate with default config)
 * - Uses constant O(1) memory regardless of items added
 * 
 * Configuration:
 * - Expected items: 10,000 SOS beacons
 * - False positive rate: 1%
 * - Memory usage: ~12KB
 * 
 * Usage:
 * - Call `mightContain(sosId)` to check if a beacon was likely seen
 * - Call `add(sosId)` to mark a beacon as seen
 * - Call `reset()` periodically (e.g., every hour) to clear old entries
 */
class BloomFilter(
    expectedItems: Int = 10_000,
    falsePositiveRate: Double = 0.01
) {
    companion object {
        private const val TAG = "BloomFilter"
    }
    
    // Calculate optimal bit array size: m = -n * ln(p) / (ln(2)^2)
    private val bitArraySize: Int = run {
        val m = ceil(-expectedItems * ln(falsePositiveRate) / (ln(2.0) * ln(2.0)))
        m.toInt().coerceAtLeast(64)
    }
    
    // Calculate optimal number of hash functions: k = (m/n) * ln(2)
    private val numHashFunctions: Int = run {
        val k = (bitArraySize.toDouble() / expectedItems) * ln(2.0)
        k.roundToInt().coerceIn(1, 10)
    }
    
    // Bit array for filter state
    private val bitSet = BitSet(bitArraySize)
    
    // Counter for approximate item count
    private var itemCount = 0
    
    // Timestamp of last reset
    private var lastResetTime = System.currentTimeMillis()
    
    /**
     * Check if a UUID might be in the filter.
     * 
     * @return true if the UUID was possibly added before (may have 1% false positive rate)
     *         false if the UUID was definitely never added
     */
    fun mightContain(uuid: UUID): Boolean {
        val hash1 = uuid.mostSignificantBits
        val hash2 = uuid.leastSignificantBits
        
        for (i in 0 until numHashFunctions) {
            // Double hashing: h(i) = h1 + i * h2
            val combinedHash = hash1 + i * hash2
            val index = abs((combinedHash % bitArraySize).toInt())
            
            if (!bitSet.get(index)) {
                return false // Definitely not in the filter
            }
        }
        
        return true // Probably in the filter
    }
    
    /**
     * Add a UUID to the filter.
     * 
     * @return true if the UUID was added (wasn't seen before based on filter)
     *         false if the UUID was probably already in the filter
     */
    fun add(uuid: UUID): Boolean {
        val wasSeen = mightContain(uuid)
        
        val hash1 = uuid.mostSignificantBits
        val hash2 = uuid.leastSignificantBits
        
        for (i in 0 until numHashFunctions) {
            val combinedHash = hash1 + i * hash2
            val index = abs((combinedHash % bitArraySize).toInt())
            bitSet.set(index)
        }
        
        if (!wasSeen) {
            itemCount++
        }
        
        return !wasSeen
    }
    
    /**
     * Reset the filter, clearing all entries.
     * Call this periodically (e.g., every hour) to prevent saturation.
     */
    fun reset() {
        bitSet.clear()
        itemCount = 0
        lastResetTime = System.currentTimeMillis()
    }
    
    /**
     * Get the approximate number of items in the filter.
     */
    fun approximateCount(): Int = itemCount
    
    /**
     * Get time since last reset in milliseconds.
     */
    fun timeSinceReset(): Long = System.currentTimeMillis() - lastResetTime
    
    /**
     * Get estimated memory usage in bytes.
     */
    fun memoryUsageBytes(): Int = bitArraySize / 8
    
    /**
     * Check if filter should be reset based on age.
     * @param maxAgeMs Maximum age before reset (default: 1 hour)
     */
    fun shouldReset(maxAgeMs: Long = 60 * 60 * 1000): Boolean {
        return timeSinceReset() > maxAgeMs
    }
    
    /**
     * Get the saturation ratio (0.0 to 1.0).
     * Higher values indicate more false positives.
     */
    fun saturationRatio(): Double {
        return bitSet.cardinality().toDouble() / bitArraySize
    }
}
