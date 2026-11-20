package dev.aprax.tmaii.backend.routes

import dev.aprax.tmaii.backend.auth.AuthManager
import dev.aprax.tmaii.backend.auth.authUser
import dev.aprax.tmaii.backend.auth.clientIp
import dev.aprax.tmaii.backend.config.AppConfig
import dev.aprax.tmaii.backend.models.AdminAuditEntry
import dev.aprax.tmaii.backend.models.AdminLoginBody
import dev.aprax.tmaii.backend.models.AdminProviderAction
import dev.aprax.tmaii.backend.models.AdminProviderResponse
import dev.aprax.tmaii.backend.models.AdminStatsResponse
import dev.aprax.tmaii.backend.models.AdminUsageResponse
import dev.aprax.tmaii.backend.models.AdminUserEntry
import dev.aprax.tmaii.backend.models.AuthResponse
import dev.aprax.tmaii.backend.models.DayUsage
import dev.aprax.tmaii.backend.models.ErrorEntry
import dev.aprax.tmaii.backend.providers.ProviderRegistry
import dev.aprax.tmaii.backend.repositories.AuditLogRepo
import dev.aprax.tmaii.backend.repositories.MailboxRepo
import dev.aprax.tmaii.backend.repositories.SessionRepo
import dev.aprax.tmaii.backend.repositories.UsageLogRepo
import dev.aprax.tmaii.backend.repositories.UserRepo
import dev.aprax.tmaii.backend.security.Role
import dev.aprax.tmaii.backend.services.MailboxService
import dev.aprax.tmaii.backend.utils.ForbiddenException
import dev.aprax.tmaii.backend.utils.TooManyRequestsException
import dev.aprax.tmaii.backend.utils.UnauthorizedException
import io.ktor.server.application.call
import io.ktor.server.auth.authenticate
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import java.time.OffsetDateTime
import java.time.ZoneOffset

fun Route.adminRoutes(
    authManager: AuthManager,
    registry: ProviderRegistry,
    config: AppConfig,
    service: MailboxService,
) {
    route("/api/v1/admin") {
        post("/login") {
            val ip = call.clientIp()
            if (!authManager.loginLimiter.tryAcquire("admin:$ip")) {
                throw TooManyRequestsException("Too many attempts", retryAfterSeconds = 60)
            }
            val body = call.receive<AdminLoginBody>()
            val (user, token) = authManager.login(body.email, body.password)
            if (user.role != Role.ADMIN) {
                authManager.logout(user.sessionId)
                throw ForbiddenException("Not an admin account", "forbidden_role")
            }
            AuditLogRepo.log(user.userId, "admin_login", body.email, null, ip)
            call.respond(
                AuthResponse(
                    token = token.accessToken,
                    role = user.role.name,
                    userId = user.userId,
                    email = user.email,
                    expiresAt = token.expiresAtMillis,
                ),
            )
        }

        authenticate {
            get("/stats") {
                val admin = call.authUser()
                requireAdmin(admin.role)
                val now = OffsetDateTime.now(ZoneOffset.UTC)
                val day = now.minusDays(1)
                val dbOk = try {
                    MailboxRepo.countAll(); true
                } catch (_: Exception) {
                    false
                }
                call.respond(
                    AdminStatsResponse(
                        totalUsers = UserRepo.countByKind(Role.ACCOUNT.name) + UserRepo.countByKind(Role.ADMIN.name),
                        totalAccounts = UserRepo.countByKind(Role.ACCOUNT.name),
                        activeGuestSessions = SessionRepo.countActiveGuests(),
                        totalMailboxes = MailboxRepo.countAll(),
                        mailboxesByProvider = MailboxRepo.countByProvider(),
                        requestsLast24h = UsageLogRepo.countSince(day),
                        failuresLast24h = UsageLogRepo.countFailuresSince(day),
                        rateLimitEventsLast24h = UsageLogRepo.countRateLimitsSince(day),
                        databaseOk = dbOk,
                        backendStatus = if (dbOk) "healthy" else "degraded",
                        recentErrors = UsageLogRepo.recentErrors(10).map {
                            ErrorEntry(it.provider, it.action, it.errorCode, it.statusCode, it.at)
                        },
                    ),
                )
            }

            get("/providers") {
                requireAdmin(call.authUser().role)
                val list = registry.list().map { p ->
                    val health = if (p.isConfigured()) {
                        try {
                            registry.throttler.withPermit(p.id, registry.minIntervalMs(p.id)) { p.healthCheck() }
                        } catch (e: Exception) {
                            null
                        }
                    } else null
                    val quota = if (p.isConfigured()) {
                        try {
                            registry.throttler.withPermit(p.id, registry.minIntervalMs(p.id)) { p.getQuota() }
                        } catch (_: Exception) {
                            null
                        }
                    } else null
                    val info = registry.info(p)
                    AdminProviderResponse(
                        id = p.id,
                        displayName = p.displayName,
                        enabled = info.enabled,
                        configured = p.isConfigured(),
                        keyConfigured = when (p.id) {
                            registry.freecustom.id -> !config.fceApiKey.isNullOrBlank()
                            registry.tmailor.id -> !config.tmailorApiKey.isNullOrBlank()
                            else -> false
                        },
                        keyPreview = when (p.id) {
                            registry.freecustom.id -> config.fceApiKey?.let { mask(it) }
                            registry.tmailor.id -> config.tmailorApiKey?.let { mask(it) }
                            else -> null
                        },
                        reachable = health?.reachable,
                        healthMessage = health?.message,
                        capabilities = p.capabilities,
                        quota = quota,
                        documentationUrl = p.documentationUrl,
                    )
                }
                call.respond(mapOf("providers" to list))
            }

            post("/providers/{id}/action") {
                val admin = call.authUser()
                requireAdmin(admin.role)
                val id = call.parameters["id"] ?: throw dev.aprax.tmaii.backend.utils.BadRequestException("id required")
                val body = call.receive<AdminProviderAction>()
                val enabled = when (body.action) {
                    "enable" -> true
                    "disable" -> false
                    "test" -> {
                        val p = registry.get(id)
                        val health = if (p.isConfigured()) {
                            registry.throttler.withPermit(p.id, registry.minIntervalMs(p.id)) { p.healthCheck() }
                        } else {
                            dev.aprax.tmaii.backend.providers.ProviderHealth(p.id, false, "Not configured")
                        }
                        AuditLogRepo.log(admin.userId, "provider_test", id, health.message, call.clientIp())
                        call.respond(mapOf("health" to health))
                        return@post
                    }
                    else -> throw dev.aprax.tmaii.backend.utils.BadRequestException("Unknown action", "invalid_action")
                }
                // Enable/disable is recorded as an audit event; runtime toggles require env change
                // (secrets live only in environment variables — documented in docs/SECRETS.md).
                AuditLogRepo.log(
                    admin.userId, "provider_${body.action}", id,
                    "Runtime toggle requested; apply via FCE_ENABLED / TMAILOR_ENABLED env and restart",
                    call.clientIp(),
                )
                call.respond(
                    mapOf(
                        "ok" to true,
                        "requested" to enabled,
                        "note" to "Provider toggles are applied via environment variables (FCE_ENABLED, TMAILOR_ENABLED) for security. Restart the backend after changing them.",
                    ),
                )
            }

            get("/usage") {
                requireAdmin(call.authUser().role)
                val now = OffsetDateTime.now(ZoneOffset.UTC)
                val day = now.minusDays(1)
                val daily = UsageLogRepo.dailyUsage(30).map {
                    DayUsage(it.date, it.total, it.failed, it.byProvider)
                }
                call.respond(
                    AdminUsageResponse(
                        days = daily,
                        byProvider = UsageLogRepo.countByProviderSince(now.minusDays(30)),
                        totalRequests = UsageLogRepo.countSince(now.minusDays(30)),
                        totalFailures = UsageLogRepo.countFailuresSince(now.minusDays(30)),
                        rateLimitEvents = UsageLogRepo.countRateLimitsSince(now.minusDays(30)),
                    ),
                )
            }

            get("/users") {
                requireAdmin(call.authUser().role)
                val limit = call.request.queryParameters["limit"]?.toIntOrNull()?.coerceIn(1, 100) ?: 50
                val offset = call.request.queryParameters["offset"]?.toLongOrNull() ?: 0L
                val users = UserRepo.listUsers(limit, offset).map { u ->
                    AdminUserEntry(
                        id = u.id,
                        email = u.email,
                        kind = u.kind,
                        status = u.status,
                        createdAt = u.createdAt.toString(),
                    )
                }
                call.respond(mapOf("users" to users))
            }

            post("/users/{id}/status") {
                val admin = call.authUser()
                requireAdmin(admin.role)
                val id = call.parameters["id"] ?: throw dev.aprax.tmaii.backend.utils.BadRequestException("id required")
                val body = call.receive<Map<String, String>>()
                val status = body["status"] ?: throw dev.aprax.tmaii.backend.utils.BadRequestException("status required")
                if (status !in setOf("active", "suspended")) {
                    throw dev.aprax.tmaii.backend.utils.BadRequestException("invalid status", "invalid_status")
                }
                val target = UserRepo.findById(id)
                    ?: throw dev.aprax.tmaii.backend.utils.NotFoundException("User not found")
                if (target.kind == Role.ADMIN.name && status == "suspended" && id == admin.userId) {
                    throw ForbiddenException("Cannot suspend yourself", "self_suspend")
                }
                UserRepo.setStatus(id, status)
                if (status == "suspended") SessionRepo.revokeAllForUser(id)
                AuditLogRepo.log(admin.userId, "user_status", id, status, call.clientIp())
                call.respond(mapOf("ok" to true))
            }

            get("/audit") {
                requireAdmin(call.authUser().role)
                call.respond(mapOf("entries" to AuditLogRepo.recent(100).map {
                    AdminAuditEntry(it.id, it.adminId, it.action, it.target, it.details, it.at)
                }))
            }
        }
    }
}

private fun requireAdmin(role: Role) {
    if (role != Role.ADMIN) throw ForbiddenException("Admin access required", "forbidden_role")
}

private fun mask(key: String): String =
    if (key.length <= 8) "****" else "${key.take(4)}…${key.takeLast(4)}"
