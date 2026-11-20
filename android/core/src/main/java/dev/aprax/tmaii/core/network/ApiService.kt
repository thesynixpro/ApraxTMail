package dev.aprax.tmaii.core.network

import dev.aprax.tmaii.core.model.AuthResponse
import dev.aprax.tmaii.core.model.CreateMailboxBody
import dev.aprax.tmaii.core.model.HealthResponse
import dev.aprax.tmaii.core.model.Mailbox
import dev.aprax.tmaii.core.model.MailboxesResponse
import dev.aprax.tmaii.core.model.MeResponse
import dev.aprax.tmaii.core.model.MessageDetail
import dev.aprax.tmaii.core.model.MessagesResponse
import dev.aprax.tmaii.core.model.OkResponse
import dev.aprax.tmaii.core.model.OtpResponse
import dev.aprax.tmaii.core.model.ProviderInfo
import dev.aprax.tmaii.core.model.ProviderQuota
import dev.aprax.tmaii.core.model.ProvidersResponse
import dev.aprax.tmaii.core.model.QuotasResponse
import kotlinx.serialization.Serializable
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
data class LoginBody(val email: String, val password: String)

@Serializable
data class RegisterBody(val email: String, val password: String, val guestToken: String? = null)

@Singleton
class ApiService @Inject constructor(private val api: ApiClient) {

    suspend fun health(): HealthResponse = api.get("/health", token = null)

    suspend fun createGuest(): AuthResponse = api.post("/api/v1/auth/guest")

    suspend fun register(body: RegisterBody): AuthResponse = api.post("/api/v1/auth/register", body, token = null)

    suspend fun login(body: LoginBody): AuthResponse = api.post("/api/v1/auth/login", body, token = null)

    suspend fun logout(): OkResponse = api.post("/api/v1/auth/logout")

    suspend fun me(): MeResponse = api.get("/api/v1/auth/me")

    suspend fun providers(): List<ProviderInfo> =
        api.get<ProvidersResponse>("/api/v1/providers").providers

    suspend fun quotas(): List<ProviderQuota> =
        api.get<QuotasResponse>("/api/v1/providers/quotas").quotas

    suspend fun createMailbox(body: CreateMailboxBody): Mailbox =
        api.post("/api/v1/mailboxes", body)

    suspend fun listMailboxes(): List<Mailbox> =
        api.get<MailboxesResponse>("/api/v1/mailboxes").mailboxes

    suspend fun listMessages(mailboxId: String): List<dev.aprax.tmaii.core.model.Message> =
        api.get<MessagesResponse>("/api/v1/mailboxes/$mailboxId/messages").messages

    suspend fun getMessage(mailboxId: String, messageId: String): MessageDetail =
        api.get("/api/v1/mailboxes/$mailboxId/messages/$messageId")

    suspend fun getOtp(mailboxId: String): OtpResponse =
        api.get("/api/v1/mailboxes/$mailboxId/otp")

    suspend fun deleteMailbox(mailboxId: String, remote: Boolean = true): OkResponse =
        api.delete("/api/v1/mailboxes/$mailboxId?remote=$remote")

    suspend fun deleteMessage(mailboxId: String, messageId: String): OkResponse =
        api.delete("/api/v1/mailboxes/$mailboxId/messages/$messageId")
}
