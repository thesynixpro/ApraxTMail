package dev.aprax.tmaii.backend.routes

import dev.aprax.tmaii.backend.auth.authUser
import dev.aprax.tmaii.backend.models.ApiOk
import dev.aprax.tmaii.backend.models.CreateMailboxBody
import dev.aprax.tmaii.backend.models.OtpResponse
import dev.aprax.tmaii.backend.providers.CreateMailboxRequest
import dev.aprax.tmaii.backend.services.MailboxService
import dev.aprax.tmaii.backend.utils.BadRequestException
import io.ktor.server.application.call
import io.ktor.server.auth.authenticate
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route

fun Route.mailboxRoutes(service: MailboxService) {
    authenticate {
        route("/api/v1") {
            get("/providers") {
                call.respond(mapOf("providers" to service.listProviders()))
            }

            get("/providers/quotas") {
                call.respond(mapOf("quotas" to service.providerQuotas()))
            }

            post("/mailboxes") {
                val ctx = service.ctx(call.authUser())
                val body = call.receive<CreateMailboxBody>()
                if (body.provider.isBlank()) throw BadRequestException("provider is required", "missing_field")
                val mailbox = service.createMailbox(
                    ctx = ctx,
                    providerId = body.provider,
                    request = CreateMailboxRequest(
                        username = body.username?.trim()?.takeIf { it.isNotEmpty() },
                        domain = body.domain?.trim()?.takeIf { it.isNotEmpty() },
                        random = body.random,
                    ),
                    label = body.label,
                )
                call.respond(io.ktor.http.HttpStatusCode.Created, mailbox)
            }

            get("/mailboxes") {
                val ctx = service.ctx(call.authUser())
                call.respond(mapOf("mailboxes" to service.listMailboxes(ctx)))
            }

            get("/mailboxes/{id}/messages") {
                val ctx = service.ctx(call.authUser())
                val id = call.parameters["id"] ?: throw BadRequestException("id required")
                call.respond(mapOf("messages" to service.refreshMessages(ctx, id)))
            }

            get("/mailboxes/{id}/messages/{messageId}") {
                val ctx = service.ctx(call.authUser())
                val id = call.parameters["id"] ?: throw BadRequestException("id required")
                val messageId = call.parameters["messageId"] ?: throw BadRequestException("messageId required")
                call.respond(service.getMessage(ctx, id, messageId))
            }

            get("/mailboxes/{id}/otp") {
                val ctx = service.ctx(call.authUser())
                val id = call.parameters["id"] ?: throw BadRequestException("id required")
                val otp = service.getOtp(ctx, id)
                call.respond(
                    OtpResponse(
                        otp = otp?.code,
                        source = otp?.source?.name,
                        score = otp?.score,
                        messageId = otp?.messageId,
                        subject = otp?.subject,
                        detected = otp != null,
                    ),
                )
            }

            delete("/mailboxes/{id}") {
                val ctx = service.ctx(call.authUser())
                val id = call.parameters["id"] ?: throw BadRequestException("id required")
                val deleteRemote = call.request.queryParameters["remote"] != "false"
                service.deleteMailbox(ctx, id, deleteRemote)
                call.respond(ApiOk(message = "Mailbox deleted"))
            }

            delete("/mailboxes/{id}/messages/{messageId}") {
                val ctx = service.ctx(call.authUser())
                val id = call.parameters["id"] ?: throw BadRequestException("id required")
                val messageId = call.parameters["messageId"] ?: throw BadRequestException("messageId required")
                service.deleteMessage(ctx, id, messageId)
                call.respond(ApiOk(message = "Message deleted"))
            }
        }
    }
}

private fun MailboxService.ctx(user: dev.aprax.tmaii.backend.auth.AuthenticatedUser) =
    MailboxService.AuthContext(userId = user.userId, role = user.role.name)
