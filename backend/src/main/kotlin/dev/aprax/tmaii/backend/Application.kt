package dev.aprax.tmaii.backend

import dev.aprax.tmaii.backend.auth.AuthManager
import dev.aprax.tmaii.backend.auth.installAuth
import dev.aprax.tmaii.backend.config.AppConfig
import dev.aprax.tmaii.backend.database.DatabaseFactory
import dev.aprax.tmaii.backend.models.ApiErrorBody
import dev.aprax.tmaii.backend.models.HealthResponse
import dev.aprax.tmaii.backend.models.ProviderHealthSummary
import dev.aprax.tmaii.backend.providers.ProviderRegistry
import dev.aprax.tmaii.backend.repositories.MailboxRepo
import dev.aprax.tmaii.backend.repositories.SessionRepo
import dev.aprax.tmaii.backend.repositories.UserRepo
import dev.aprax.tmaii.backend.routes.adminRoutes
import dev.aprax.tmaii.backend.routes.authRoutes
import dev.aprax.tmaii.backend.routes.mailboxRoutes
import dev.aprax.tmaii.backend.security.JwtService
import dev.aprax.tmaii.backend.security.PasswordHasher
import dev.aprax.tmaii.backend.security.RateLimiter
import dev.aprax.tmaii.backend.security.Role
import dev.aprax.tmaii.backend.security.TokenCipher
import dev.aprax.tmaii.backend.services.MailboxService
import dev.aprax.tmaii.backend.utils.ApiException
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationStopped
import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.BadRequestException as KtorBadRequest
import io.ktor.server.plugins.compression.Compression
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.cors.routing.CORS
import io.ktor.server.plugins.defaultheaders.DefaultHeaders
import io.ktor.server.plugins.forwardedheaders.XForwardedHeaders
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.request.httpMethod
import io.ktor.server.request.path
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jetbrains.exposed.sql.transactions.transaction
import org.slf4j.LoggerFactory
import java.io.File

private val logger = LoggerFactory.getLogger("ApraxTmaii")

fun main() {
    val config = AppConfig.fromEnv()
    embeddedServer(Netty, port = config.port, host = "0.0.0.0", module = { module(config) })
        .start(wait = true)
}

fun Application.module(config: AppConfig = AppConfig.fromEnv()) {
    val startedAt = System.currentTimeMillis()
    val json = ProviderRegistry.defaultJson

    DatabaseFactory.init(config)
    val jwt = JwtService(config)
    val cipher = TokenCipher(config.tokenEncryptionKey)
    val registry = ProviderRegistry(config)
    val authManager = AuthManager(config, jwt)
    val mailboxService = MailboxService(config, registry, cipher, json)

    bootstrapAdmin(config)

    install(ContentNegotiation) { json(json) }
    install(DefaultHeaders) {
        header(HttpHeaders.CacheControl, "no-store")
    }
    install(Compression)
    install(XForwardedHeaders)

    install(CORS) {
        val allowed = config.corsOrigins.toSet()
        allowOrigins { origin -> origin in allowed }
        allowMethod(io.ktor.http.HttpMethod.Get)
        allowMethod(io.ktor.http.HttpMethod.Post)
        allowMethod(io.ktor.http.HttpMethod.Delete)
        allowMethod(io.ktor.http.HttpMethod.Put)
        allowMethod(io.ktor.http.HttpMethod.Patch)
        allowHeader(HttpHeaders.Authorization)
        allowHeader(HttpHeaders.ContentType)
        allowHeader("Idempotency-Key")
        allowCredentials = true
    }

    install(StatusPages) {
        exception<ApiException> { call, cause ->
            cause.retryAfterSeconds?.let { call.response.header("Retry-After", it.toString()) }
            call.respond(
                cause.status,
                ApiErrorBody(
                    error = cause.code,
                    message = cause.message ?: "Error",
                    retryAfter = cause.retryAfterSeconds,
                    details = cause.details,
                ),
            )
        }
        exception<KtorBadRequest> { call, cause ->
            call.respond(
                HttpStatusCode.BadRequest,
                ApiErrorBody(error = "bad_request", message = cause.message ?: "Bad request"),
            )
        }
        exception<Throwable> { call, cause ->
            logger.error("Unhandled error on ${call.request.httpMethod.value} ${call.request.path()}", cause)
            call.respond(
                HttpStatusCode.InternalServerError,
                ApiErrorBody(error = "internal_error", message = "An unexpected error occurred"),
            )
        }
    }

    installAuth(authManager)

    launch {
        while (true) {
            delay(3_600_000)
            try {
                SessionRepo.purgeExpired()
            } catch (e: Exception) {
                logger.warn("Session purge failed: {}", e.message)
            }
        }
    }

    val adminDir = sequenceOf(
        File("admin/frontend"),
        File("/opt/admin/frontend"),
        File("../admin/frontend"),
        File(System.getenv("ADMIN_DIR") ?: "\u0000"),
    ).firstOrNull { it.isDirectory && it.listFiles()?.isNotEmpty() == true }

    routing {
        get("/health") {
            val dbOk = try {
                MailboxRepo.countAll()
                true
            } catch (_: Exception) {
                false
            }
            val providers = registry.list().map { p ->
                ProviderHealthSummary(
                    id = p.id,
                    configured = p.isConfigured(),
                    enabled = when (p.id) {
                        registry.freecustom.id -> config.fceEnabled
                        registry.tmailor.id -> config.tmailorEnabled
                        else -> true
                    },
                )
            }
            call.respond(
                HealthResponse(
                    status = if (dbOk) "ok" else "degraded",
                    version = "1.0.0",
                    database = if (dbOk) "up" else "down",
                    environment = config.appEnv,
                    providers = providers,
                    uptimeSeconds = (System.currentTimeMillis() - startedAt) / 1000,
                ),
            )
        }

        get("/") {
            call.respondText(
                """{"service":"aprax-tmaii-backend","status":"ok","health":"/health","admin":"/admin/"}""",
                contentType = ContentType.Application.Json,
            )
        }

        authRoutes(authManager)
        mailboxRoutes(mailboxService)
        adminRoutes(authManager, registry, config, mailboxService)

        if (adminDir != null) {
            get("/admin") {
                call.response.header(HttpHeaders.Location, "/admin/")
                call.respond(HttpStatusCode.TemporaryRedirect)
            }
            get("/admin/{...}") {
                val rawPath = call.request.path().removePrefix("/admin/").ifBlank { "index.html" }
                if (rawPath.contains("..") || rawPath.startsWith("/")) {
                    call.respond(HttpStatusCode.NotFound)
                    return@get
                }
                val file = File(adminDir, rawPath)
                if (file.isFile && file.canonicalPath.startsWith(adminDir.canonicalPath)) {
                    call.respondText(file.readText(), contentType = guessType(file.name))
                } else {
                    val index = File(adminDir, "index.html")
                    if (index.isFile) {
                        call.respondText(index.readText(), contentType = ContentType.Text.Html)
                    } else {
                        call.respond(HttpStatusCode.NotFound)
                    }
                }
            }
        }
    }

    environment.monitor.subscribe(ApplicationStopped) {
        DatabaseFactory.close()
        registry.httpClient.close()
    }
}

private fun guessType(name: String): ContentType = when {
    name.endsWith(".html") -> ContentType.Text.Html
    name.endsWith(".css") -> ContentType.Text.CSS
    name.endsWith(".js") -> ContentType.Application.JavaScript
    name.endsWith(".svg") -> ContentType.Image.SVG
    name.endsWith(".png") -> ContentType.Image.PNG
    name.endsWith(".json") -> ContentType.Application.Json
    name.endsWith(".ico") -> ContentType.Image.XIcon
    else -> ContentType.Application.OctetStream
}

/** Creates the first admin account from ADMIN_EMAIL / ADMIN_PASSWORD if none exists. */
fun bootstrapAdmin(config: AppConfig) {
    val email = config.adminEmail?.trim()?.lowercase()
    val password = config.adminPassword
    if (email.isNullOrBlank() || password.isNullOrBlank()) {
        logger.warn("ADMIN_EMAIL/ADMIN_PASSWORD not set — no admin account available")
        return
    }
    transaction {
        if (UserRepo.findByEmail(email) != null) {
            logger.info("Admin account already exists")
            return@transaction
        }
        if (password.length < 12) {
            logger.error("ADMIN_PASSWORD must be at least 12 characters — admin not created")
            return@transaction
        }
        UserRepo.create(kind = Role.ADMIN.name, email = email, passwordHash = PasswordHasher.hash(password))
        logger.info("Bootstrapped first admin account for {}", email)
    }
}
