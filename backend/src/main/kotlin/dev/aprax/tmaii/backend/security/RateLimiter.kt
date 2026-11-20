package dev.aprax.tmaii.backend.security

import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap

/**
 * In-memory token bucket keyed by arbitrary string (client IP, user id, ...).
 * Suitable for a single backend instance; swap for Redis if scaling horizontally.
 */
class RateLimiter(
    private val permitsPerSecond: Int,
    private val burst: Int = permitsPerSecond.coerceAtLeast(5),
) {
    private class Bucket(var tokens: Double, var lastRefill: Long)

    private val buckets = ConcurrentHashMap<String, Bucket>()
    private val mutex = Mutex()

    suspend fun tryAcquire(key: String): Boolean = mutex.withLock {
        val now = System.currentTimeMillis()
        val bucket = buckets.getOrPut(key) { Bucket(burst.toDouble(), now) }
        val elapsedSec = (now - bucket.lastRefill) / 1000.0
        bucket.tokens = (bucket.tokens + elapsedSec * permitsPerSecond).coerceAtMost(burst.toDouble())
        bucket.lastRefill = now
        if (bucket.tokens >= 1.0) {
            bucket.tokens -= 1.0
            true
        } else {
            false
        }
    }
}

/**
 * Serializes outbound calls per provider with a minimum spacing so we respect
 * upstream per-second limits (FreeCustom free plan = 1 req/s, Tmailor free = 30/min).
 */
class ProviderThrottler {
    private val nextAllowedAt = ConcurrentHashMap<String, Long>()
    private val mutexes = ConcurrentHashMap<String, Mutex>()

    suspend fun <T> withPermit(providerId: String, minIntervalMs: Long, block: suspend () -> T): T {
        val mutex = mutexes.getOrPut(providerId) { Mutex() }
        mutex.lock()
        try {
            val now = System.currentTimeMillis()
            val next = nextAllowedAt[providerId] ?: 0L
            if (next > now) delay(next - now)
            return try {
                block()
            } finally {
                nextAllowedAt[providerId] = System.currentTimeMillis() + minIntervalMs
            }
        } finally {
            mutex.unlock()
        }
    }
}
