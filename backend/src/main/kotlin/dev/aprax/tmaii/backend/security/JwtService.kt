package dev.aprax.tmaii.backend.security

import com.auth0.jwt.JWT
import com.auth0.jwt.JWTVerifier
import com.auth0.jwt.algorithms.Algorithm
import com.auth0.jwt.interfaces.DecodedJWT
import dev.aprax.tmaii.backend.config.AppConfig
import java.util.Date
import java.util.UUID

enum class Role { GUEST, ACCOUNT, ADMIN }

data class TokenPair(
    val accessToken: String,
    val sessionId: String,
    val expiresAtMillis: Long,
)

class JwtService(private val config: AppConfig) {
    private val algorithm = Algorithm.HMAC256(config.jwtSecret)

    val verifier: JWTVerifier = JWT.require(algorithm)
        .withIssuer(ISSUER)
        .build()

    fun issue(userId: String, role: Role, sessionHours: Long): TokenPair {
        val sessionId = UUID.randomUUID().toString()
        val expiresAt = System.currentTimeMillis() + sessionHours * 3600_000L
        val token = JWT.create()
            .withIssuer(ISSUER)
            .withSubject(userId)
            .withClaim("sid", sessionId)
            .withClaim("role", role.name)
            .withExpiresAt(Date(expiresAt))
            .sign(algorithm)
        return TokenPair(token, sessionId, expiresAt)
    }

    fun decode(token: String): DecodedJWT? = try {
        verifier.verify(token)
    } catch (_: Exception) {
        null
    }

    companion object {
        const val ISSUER = "aprax-tmaii"
    }
}
