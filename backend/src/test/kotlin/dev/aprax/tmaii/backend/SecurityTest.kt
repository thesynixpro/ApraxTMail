package dev.aprax.tmaii.backend

import dev.aprax.tmaii.backend.config.AppConfig
import dev.aprax.tmaii.backend.security.JwtService
import dev.aprax.tmaii.backend.security.PasswordHasher
import dev.aprax.tmaii.backend.security.ProviderThrottler
import dev.aprax.tmaii.backend.security.RateLimiter
import dev.aprax.tmaii.backend.security.Role
import dev.aprax.tmaii.backend.security.TokenCipher
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SecurityTest {

    @Test
    fun passwordHashAndVerify() {
        val hash = PasswordHasher.hash("correct horse battery staple")
        assertTrue(hash.startsWith("$2"))
        assertTrue(PasswordHasher.verify("correct horse battery staple", hash))
        assertFalse(PasswordHasher.verify("wrong-password", hash))
    }

    @Test
    fun tokenCipherRoundTrip() {
        val cipher = TokenCipher("unit-test-key-material")
        val secret = "tmk_live_super_secret_token"
        val enc = cipher.encrypt(secret)
        assertFalse(enc.contains(secret))
        assertEquals(secret, cipher.decrypt(enc))
    }

    @Test
    fun jwtIssueAndDecode() {
        val config = testConfig()
        val jwt = JwtService(config)
        val pair = jwt.issue("user-1", Role.ACCOUNT, 24)
        val decoded = jwt.decode(pair.accessToken)
        assertNotNull(decoded)
        assertEquals("user-1", decoded!!.subject)
        assertEquals("ACCOUNT", decoded.getClaim("role").asString())
        assertEquals(pair.sessionId, decoded.getClaim("sid").asString())
        assertNull(jwt.decode("not-a-token"))
    }

    @Test
    fun rateLimiterEnforcesLimit() = runBlocking {
        val limiter = RateLimiter(permitsPerSecond = 1, burst = 2)
        assertTrue(limiter.tryAcquire("ip1"))
        assertTrue(limiter.tryAcquire("ip1"))
        assertFalse(limiter.tryAcquire("ip1"))
        assertTrue(limiter.tryAcquire("ip2"))
    }

    @Test
    fun providerThrottlerSpacessCalls() = runBlocking {
        val t = ProviderThrottler()
        val start = System.currentTimeMillis()
        t.withPermit("p", 100) { }
        t.withPermit("p", 100) { }
        val elapsed = System.currentTimeMillis() - start
        assertTrue(elapsed >= 90, "expected throttle delay, was ${elapsed}ms")
    }

    private fun testConfig() = AppConfig(
        port = 8080,
        appEnv = "test",
        publicBaseUrl = "http://localhost",
        databaseUrl = "jdbc:postgresql://localhost/test",
        databaseUser = "u",
        databasePassword = "p",
        databaseMaxPoolSize = 1,
        jwtSecret = "unit-test-secret-that-is-long-enough-32+",
        tokenEncryptionKey = "unit-test-encryption-key",
        guestSessionHours = 1,
        accountSessionHours = 2,
        fceApiKey = null,
        tmailorApiKey = null,
        fceEnabled = true,
        tmailorEnabled = true,
        tmailorExpectedIp = null,
        adminEmail = null,
        adminPassword = null,
        rateLimitRps = 5,
        guestMailboxLimit = 10,
        accountMailboxLimit = 30,
        corsOrigins = listOf("http://localhost"),
    )
}
