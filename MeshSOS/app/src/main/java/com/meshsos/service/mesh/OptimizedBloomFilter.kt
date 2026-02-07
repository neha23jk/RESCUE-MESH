package com.meshsos.service.mesh

import java.util.*
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.ln

/**
 * Optimized Bloom Filter for efficient SOS beacon deduplication
 * 
 * Uses multiple hash functions on a BitSet to provide probabilistic
 * set membership testing with minimal memory footprint.
 * 
 * Based on Bitchat's implementation for mesh message deduplication.
 */
class OptimizedBloomFilter(
    private val capacity: Int = 1000,
    private val falsePositiveRate: Double = 0.01
) {
    companion object {
        private const val TAG = "BloomFilter"
    }
    
    // Calculate optimal bit array size and number of hash functions
    private val bitSize: Int = calculateOptimalBitSize(capacity, falsePositiveRate)
    private val numHashFunctions: Int = calculateOptimalHashCount(capacity, bitSize)
    
    // BitSet for storage (thread-safe via synchronized access)
    private val bitSet = BitSet(bitSize)
    
    // Timestamp tracking for automatic cleanup
    private val timestamps = ConcurrentHashMap<UUID, Long>()
    
    // Cleanup configuration
    private val maxAge = 60_000L // 1 minute
    
    /**
     * Add an item to the Bloom filter
     */
    @Synchronized
    fun add(item: UUID) {
        val hashes = getHashes(item)
        hashes.forEach { hash ->
            bitSet.set(hash % bitSize)
        }
        timestamps[item] = System.currentTimeMillis()
    }
    
    /**
     * Check if an item might be in the set
     * 
     * Returns:
     * - true: Item might be in the set (could be false positive)
     * - false: Item is definitely NOT in the set (no false negatives)
     */
    @Synchronized
    fun mightContain(item: UUID): Boolean {
        val hashes = getHashes(item)
        return hashes.all { hash ->
            bitSet.get(hash % bitSize)
        }
    }
    
    /**
     * Clean up old entries to prevent unbounded growth
     */
    @Synchronized
    fun cleanup() {
        val now = System.currentTimeMillis()
        val toRemove = timestamps.entries.filter { (_, timestamp) ->
            now - timestamp > maxAge
        }
        
        val removedCount = toRemove.size
        toRemove.forEach { timestamps.remove(it.key) }
        
        if (removedCount > 0) {
            android.util.Log.d(TAG, "Cleaned up $removedCount old entries from Bloom filter")
        }
    }
    
    /**
     * Clear all entries
     */
    @Synchronized
    fun clear() {
        bitSet.clear()
        timestamps.clear()
    }
    
    /**
     * Get current size (approximate due to probabilistic nature)
     */
    fun size(): Int = timestamps.size
    
    /**
     * Get memory usage estimate in bytes
     */
    fun getMemoryUsage(): Int {
        val bitSetSize = bitSize / 8 // bits to bytes
        val timestampSize = timestamps.size * 24 // UUID (16) + Long (8) per entry
        return bitSetSize + timestampSize
    }
    
    /**
     * Generate multiple hash values for an item using different hash functions
     */
    private fun getHashes(item: UUID): IntArray {
        val hashes = IntArray(numHashFunctions)
        
        // Use combination of hashCode and murmur hash variants
        val baseHash = item.hashCode()
        val mostSigBits = item.mostSignificantBits
        val leastSigBits = item.leastSignificantBits
        
        for (i in 0 until numHashFunctions) {
            // Combine different parts of UUID with iteration index for different hashes
            hashes[i] = when (i) {
                0 -> baseHash
                1 -> (mostSigBits xor (i.toLong() * 0x9e3779b9)).toInt()
                2 -> (leastSigBits xor (i.toLong() * 0x7f4a7c15)).toInt()
                3 -> ((mostSigBits shr 32) xor (i.toLong() * 0x27d4eb2d)).toInt()
                else -> {
                    // Additional hash functions using rotating bit patterns
                    val combined = (mostSigBits xor leastSigBits) + i.toLong()
                    ((combined * 0x85ebca6b) xor (combined ushr 13)).toInt()
                }
            }.let { hash ->
                // Ensure positive hash value
                if (hash < 0) -hash else hash
            }
        }
        
        return hashes
    }
    
    /**
     * Calculate optimal bit array size based on capacity and desired false positive rate
     * 
     * Formula: m = -n * ln(p) / (ln(2)^2)
     * where:
     *   m = bit array size
     *   n = expected number of elements
     *   p = false positive rate
     */
    private fun calculateOptimalBitSize(n: Int, p: Double): Int {
        val m = -(n * ln(p)) / (ln(2.0) * ln(2.0))
        return m.toInt().coerceAtLeast(64) // Minimum 64 bits
    }
    
    /**
     * Calculate optimal number of hash functions
     * 
     * Formula: k = (m/n) * ln(2)
     * where:
     *   k = number of hash functions
     *   m = bit array size
     *   n = expected number of elements
     */
    private fun calculateOptimalHashCount(n: Int, m: Int): Int {
        val k = (m.toDouble() / n.toDouble()) * ln(2.0)
        return k.toInt().coerceIn(3, 5) // Keep between 3-5 hash functions
    }
}
