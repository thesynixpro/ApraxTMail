package dev.aprax.tmaii.backend.auth

import dev.aprax.tmaii.backend.config.AppConfig
import dev.aprax.tmaii.backend.repositories.SessionRepo
import dev.aprax.tmaii.backend.repositories.UserRepo
import dev.aprax.tmaii.backend.security.JwtService
import dev.aprax.tmaii.backend.security.PasswordHasher
import dev.aprax.tmaii.backend.security.Role
import dev.aprax.tmaii.backend.services.MailboxService
import dev.aprax.tmaii.backend.utils.ApiException
import dev.aprax.tmaii.backend.utils.BadRequestException
import dev.aprax.tmaii.backend.utils.ForbiddenException
import dev.aprax.tmaii.backend.utils.TooManyRequestsException
import dev.aprax.tmaii.backend.utils.UnauthorizedException
import dev.aprax.tmaii.backend.security.RateLimiter
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.Principal
import io.ktor.server.auth.bearer
import io.ktor.server.auth.principal
import io.ktor.server.plugins.origin
import io.ktor.server.request.header
import java.time.OffsetDateTime
import java.time.ZoneOffset

data class AuthenticatedUser(
    val userId: String,
    val role: Role,
    val sessionId: String,
    val email: String? = null,
) : Principal

class AuthManager(
    private val config: AppConfig,
    private val jwt: JwtService,
) {
    val loginLimiter = RateLimiter(permitsPerSecond = 2, burst = 10)

    fun createGuest(userAgent: String?, ip: String?): Pair<AuthenticatedUser, dev.aprax.tmaii.backend.security.TokenPair> {
        val user = UserRepo.create(kind = Role.GUEST.name)
        val token = jwt.issue(user.id, Role.GUEST, config.guestSessionHours)
        SessionRepo.create(token.sessionId, user.id, OffsetDateTime.ofInstant(
            java.time.Instant.ofEpochMilli(token.expiresAtMillis), ZoneOffset.UTC,
        ), userAgent, ip)
        return AuthenticatedUser(user.id, Role.GUEST, token.sessionId) to token
    }

    fun register(email: String, password: String, guestToken: String?): Pair<AuthenticatedUser, dev.aprax.tmaii.backend.security.TokenPair> {
        val normalized = email.trim().lowercase()
        if (!normalized.contains("@") || normalized.length > 254) {
            throw BadRequestException("Invalid email address", "invalid_email")
        }
        if (password.length < 8) {
            throw BadRequestException("Password must be at least 8 characters", "weak_password")
        }
        if (UserRepo.findByEmail(normalized) != null) {
            throw dev.aprax.tmaii.backend.utils.ConflictException("Email already registered", "email_taken")
        }
        val user = UserRepo.create(
            kind = Role.ACCOUNT.name,
            email = normalized,
            passwordHash = PasswordHasher.hash(password),
        )
        // Convert guest → account: transfer mailboxes from guest session
        guestToken?.let { gt ->
            jwt.decode(gt)?.let { decoded ->
                if (decoded.getClaim("role").asString() == Role.GUEST.name) {
                    val guestId = decoded.subject ?: return@let
                    UserRepo.transferGuestMailboxes(guestId, user.id)
                }
            }
        }
        val token = jwt.issue(user.id, Role.ACCOUNT, config.accountSessionHours)
        SessionRepo.create(
            token.sessionId, user.id,
            OffsetDateTime.ofInstant(java.time.Instant.ofEpochMilli(token.expiresAtMillis), ZoneOffset.UTC),
            null, null,
        )
        return AuthenticatedUser(user.id, Role.ACCOUNT, token.sessionId, normalized) to token
    }

    fun login(email: String, password: String): Pair<AuthenticatedUser, dev.aprax.tmaii.backend.security.TokenPair> {
        val normalized = email.trim().lowercase()
        val user = UserRepo.findByEmail(normalized)
            ?: throw UnauthorizedException("Invalid email or password", "invalid_credentials")
        if (user.kind == Role.GUEST.name) throw UnauthorizedException("Invalid email or password", "invalid_credentials")
        if (user.status != "active") throw ForbiddenException("Account suspended", "account_suspended")
        val hash = UserRepo.passwordHashOf(user.id)
            ?: throw UnauthorizedException("Invalid email or password", "invalid_credentials")
        if (!PasswordHasher.verify(password, hash)) {
            throw UnauthorizedException("Invalid email or password", "invalid_credentials")
        }
        val role = Role.valueOf(user.kind)
        val hours = if (role == Role.ADMIN) config.accountSessionHours else config.accountSessionHours
        val token = jwt.issue(user.id, role, hours)
        SessionRepo.create(
            token.sessionId, user.id,
            OffsetDateTime.ofInstant(java.time.Instant.ofEpochMilli(token.expiresAtMillis), ZoneOffset.UTC),
            null, null,
        )
        return AuthenticatedUser(user.id, role, token.sessionId, user.email) to token
    }

    fun logout(sessionId: String) {
        SessionRepo.revoke(sessionId)
    }

    fun authenticate(token: String): AuthenticatedUser {
        val decoded = jwt.decode(token) ?: throw UnauthorizedException("Invalid or expired token")
        val sessionId = decoded.getClaim("sid").asString()
            ?: throw UnauthorizedException("Invalid token payload")
        val session = SessionRepo.find(sessionId)
            ?: throw UnauthorizedException("Session not found")
        if (session.revokedAt != null) throw UnauthorizedException("Session revoked")
        if (session.expiresAt.isBefore(OffsetDateTime.now(ZoneOffset.UTC))) {
            throw UnauthorizedException("Session expired")
        }
        val role = runCatching { Role.valueOf(decoded.getClaim("role").asString() ?: "GUEST") }
            .getOrDefault(Role.GUEST)
        val user = UserRepo.findById(decoded.subject ?: "")
            ?: throw UnauthorizedException("User not found")
        if (user.status != "active") throw ForbiddenException("Account suspended", "account_suspended")
        return AuthenticatedUser(user.id, role, sessionId, user.email)
    }

    fun requireRole(user: AuthenticatedUser, vararg roles: Role) {
        if (user.role !in roles) throw ForbiddenException("Insufficient permissions", "forbidden_role")
    }
}

fun Application.installAuth(authManager: AuthManager) {
    install(Authentication) {
        bearer {
            authenticate { tokenCreds ->
                try {
                    authManager.authenticate(tokenCreds.token)
                } catch (_: ApiException) {
                    null
                }
            }
        }
    }
}

suspend fun ApplicationCall.authUser(): AuthenticatedUser =
    principal<AuthenticatedUser>()
        ?: throw UnauthorizedException()

fun ApplicationCall.clientIp(): String =
    request.header("X-Forwarded-For")?.split(",")?.firstOrNull()?.trim()
        ?: request.header("X-Real-IP")
        ?: try {
            request.origin.remoteHost
        } catch (_: Exception) {
            "unknown"
        }
