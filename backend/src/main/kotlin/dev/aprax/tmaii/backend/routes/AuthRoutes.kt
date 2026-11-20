package dev.aprax.tmaii.backend.routes

import dev.aprax.tmaii.backend.auth.AuthManager
import dev.aprax.tmaii.backend.auth.authUser
import dev.aprax.tmaii.backend.auth.clientIp
import dev.aprax.tmaii.backend.models.AuthResponse
import dev.aprax.tmaii.backend.models.LoginBody
import dev.aprax.tmaii.backend.models.MeResponse
import dev.aprax.tmaii.backend.models.RegisterBody
import dev.aprax.tmaii.backend.repositories.UserRepo
import dev.aprax.tmaii.backend.security.TokenPair
import dev.aprax.tmaii.backend.utils.ApiException
import dev.aprax.tmaii.backend.utils.TooManyRequestsException
import io.ktor.server.application.call
import io.ktor.server.auth.authenticate
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import io.ktor.server.routing.get
import io.ktor.server.routing.route

fun Route.authRoutes(authManager: AuthManager) {
    route("/api/v1/auth") {
        post("/guest") {
            val ip = call.clientIp()
            if (!authManager.loginLimiter.tryAcquire("guest:$ip")) {
                throw TooManyRequestsException("Too many session requests", retryAfterSeconds = 30)
            }
            val (user, token) = authManager.createGuest(call.request.headers["User-Agent"], ip)
            call.respond(
                AuthResponse(
                    token = token.accessToken,
                    role = user.role.name,
                    userId = user.userId,
                    expiresAt = token.expiresAtMillis,
                ),
            )
        }

        post("/register") {
            val ip = call.clientIp()
            if (!authManager.loginLimiter.tryAcquire("register:$ip")) {
                throw TooManyRequestsException("Too many attempts", retryAfterSeconds = 30)
            }
            val body = call.receive<RegisterBody>()
            val (user, token) = authManager.register(body.email, body.password, body.guestToken)
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

        post("/login") {
            val ip = call.clientIp()
            if (!authManager.loginLimiter.tryAcquire("login:$ip")) {
                throw TooManyRequestsException("Too many login attempts", retryAfterSeconds = 60)
            }
            val body = call.receive<LoginBody>()
            val (user, token) = authManager.login(body.email, body.password)
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
            post("/logout") {
                val user = call.authUser()
                authManager.logout(user.sessionId)
                call.respond(mapOf("success" to true))
            }

            get("/me") {
                val user = call.authUser()
                val row = UserRepo.findById(user.userId)
                call.respond(
                    MeResponse(
                        userId = user.userId,
                        role = user.role.name,
                        email = user.email,
                        createdAt = row?.createdAt?.toString(),
                    ),
                )
            }
        }
    }
}
