package dev.aprax.tmaii.backend.providers

import dev.aprax.tmaii.backend.utils.NotSupportedOperationException
import dev.aprax.tmaii.backend.utils.ProviderErrorException
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.util.UUID

/**
 * Tmailor adapter.
 * Docs: https://platform.tmailor.com/docs
 * Base: https://open-api.tmailor.com
 * Free plan: 30 req/min, 2,000 req/day, 100 mailboxes/30d, outbound IP allowlist enforced.
 * Only mailbox creation consumes quota. Messages retained 24h. Receive-only.
 */
class TmailorProvider(
    private val apiKey: String?,
    private val enabled: Boolean,
    private val http: ProviderHttpClient,
) : EmailProvider {

    override val id: String = "tmailor"
    override val displayName: String = "Tmailor"
    override val documentationUrl: String = "https://platform.tmailor.com/docs"

    override val capabilities = ProviderCapabilities(
        randomAddress = true,
        customName = true,
        domainSelection = true,
        providerOtpEndpoint = false, // OTP comes embedded in message reads
        otpFromMessage = true,
        verificationLinks = true,
        attachments = false,
        deleteMessage = true,
        deleteMailbox = true,
        reuseUrl = true,
        idempotency = true,
        messageRetentionHours = 24,
        longPolling = false,
    )

    private val authHeader: String? get() = apiKey?.let { "Bearer $it" }

    override fun isConfigured(): Boolean = enabled && !apiKey.isNullOrBlank()

    private fun requireConfigured() {
        if (!isConfigured()) throw NotSupportedOperationException("Tmailor provider is not configured")
    }

    private suspend fun <T> call(block: suspend () -> T): T {
        requireConfigured()
        return block()
    }

    override suspend fun healthCheck(): ProviderHealth {
        if (!isConfigured()) return ProviderHealth(id, false, "API key not configured or provider disabled")
        val start = System.currentTimeMillis()
        return try {
            call { http.call(id, HttpMethod.Get, "$BASE/v1/health", null) }
            ProviderHealth(id, true, "OK", System.currentTimeMillis() - start)
        } catch (e: Exception) {
            ProviderHealth(id, false, e.message ?: "unreachable", System.currentTimeMillis() - start)
        }
    }

    override suspend fun getQuota(): ProviderQuota? = call {
        val res = http.call(id, HttpMethod.Get, "$BASE/v1/me", authHeader)
        val obj = http.parse<JsonObject>(res.body)
        val limits = obj["limits"]?.jsonObject
        val subscriptions = obj["subscriptions"]?.jsonArray
        val plan = subscriptions?.firstOrNull()?.jsonObject?.get("plan")?.jsonPrimitive?.contentOrNull
        ProviderQuota(
            providerId = id,
            plan = plan ?: "free",
            requestsPerSecond = null,
            requestsPerMonth = null,
            mailboxesRemaining = limits?.get("quota_remaining")?.jsonPrimitive?.contentOrNull?.toLongOrNull(),
            dailyRequestsRemaining = null,
            ipAllowlistEnforced = obj["ip_allowlist_enforced"]?.let {
                it.toString().toBooleanStrictOrNull()
            },
            notes = buildList {
                add("Free plan: 30 req/min, 2,000 req/day, 100 mailboxes/30 days")
                add("Free plan requires outbound IP allowlist in Tmailor dashboard")
                add("Messages retained 24 hours; only mailbox creation consumes quota")
            },
        )
    }

    override suspend fun listDomains(): List<ProviderDomain> = call {
        val res = http.call(id, HttpMethod.Get, "$BASE/v1/domains", authHeader)
        val obj = http.parse<JsonObject>(res.body)
        val items = obj["items"]?.jsonArray ?: return@call emptyList()
        items.mapNotNull { el ->
            val d = el.jsonObject
            val domain = d["domain"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
            val usable = when (val u = d["usable"]) {
                is JsonNull -> true
                null -> true
                else -> u.toString().toBooleanStrictOrNull() ?: true
            }
            val verified = when (val v = d["verified"]) {
                is JsonNull -> true
                null -> true
                else -> v.toString().toBooleanStrictOrNull() ?: true
            }
            ProviderDomain(
                domain = domain,
                usable = usable && verified,
                tier = if (d["is_user_domain"]?.toString() == "true") "custom" else "shared",
            )
        }.filter { it.usable }
    }

    override suspend fun createMailbox(request: CreateMailboxRequest): EmailAccount = call {
        val body = MailboxCreateBody(
            domain = request.domain,
            name = request.username?.takeIf { request.random.not() || it.isNotBlank() }
                ?: request.username,
        )
        val res = http.call(
            providerId = id,
            method = HttpMethod.Post,
            url = "$BASE/v1/mailboxes",
            authHeader = authHeader,
            body = body,
            extraHeaders = mapOf("Idempotency-Key" to UUID.randomUUID().toString()),
            errorMapper = { status, _ ->
                when (status) {
                    HttpStatusCode.BadRequest -> "invalid_field" to "Invalid mailbox fields for Tmailor"
                    HttpStatusCode.Conflict -> "mailbox_exists" to "Mailbox already exists"
                    HttpStatusCode.PaymentRequired -> "quota_exceeded" to "Tmailor mailbox quota exhausted"
                    HttpStatusCode.Forbidden -> "ip_not_allowed" to "Server IP not allowlisted for Tmailor free plan"
                    HttpStatusCode.TooManyRequests -> "rate_limited" to "Tmailor rate limit reached"
                    else -> null
                }
            },
        )
        val obj = http.parse<JsonObject>(res.body)
        val token = obj["token"]?.let { if (it is JsonNull) null else it.jsonPrimitive.contentOrNull }
        val reuseUrl = obj["web"]?.jsonObject?.get("reuse_url")?.let {
            if (it is JsonNull) null else it.jsonPrimitive.contentOrNull
        }
        EmailAccount(
            providerId = id,
            address = obj["email"]?.jsonPrimitive?.contentOrNull
                ?: error("Tmailor response missing email"),
            providerToken = token,
            createdAt = obj["created_at"]?.jsonPrimitive?.contentOrNull,
            reuseUrl = reuseUrl,
        )
    }

    @Serializable
    private data class MailboxCreateBody(
        val domain: String? = null,
        val name: String? = null,
    )

    override suspend fun deleteMailbox(account: EmailAccount): Unit = call {
        val token = account.providerToken
            ?: throw NotSupportedOperationException("Tmailor mailbox token missing")
        http.call(
            providerId = id,
            method = HttpMethod.Post,
            url = "$BASE/v1/mailbox/delete",
            authHeader = authHeader,
            body = TokenBody(token),
            errorMapper = { status, _ ->
                if (status == HttpStatusCode.NotFound) "not_found" to "Mailbox not found" else null
            },
        )
        Unit
    }

    @Serializable
    private data class TokenBody(val token: String)

    override suspend fun listMessages(account: EmailAccount): List<EmailMessage> = call {
        val token = account.providerToken
            ?: throw NotSupportedOperationException("Tmailor mailbox token missing")
        val res = http.call(
            providerId = id,
            method = HttpMethod.Post,
            url = "$BASE/v1/mailbox/messages",
            authHeader = authHeader,
            body = MessagesQuery(token = token, limit = 100),
        )
        val obj = http.parse<JsonObject>(res.body)
        val items = obj["items"]?.jsonArray ?: return@call emptyList()
        items.map { el -> mapSummary(el.jsonObject) }
    }

    @Serializable
    private data class MessagesQuery(
        val token: String,
        val limit: Int = 100,
        val unread: Boolean? = null,
        val since: Long? = null,
    )

    private fun mapSummary(d: JsonObject): EmailMessage {
        val fromObj = d["from"]?.jsonObject
        val otpRaw = d["otp"]?.let { if (it is JsonNull) null else it.jsonPrimitive.contentOrNull }
        return EmailMessage(
            id = d["id"]?.jsonPrimitive?.contentOrNull ?: "",
            from = fromObj?.get("address")?.jsonPrimitive?.contentOrNull
                ?: d["from"]?.jsonPrimitive?.contentOrNull ?: "",
            fromName = fromObj?.get("name")?.jsonPrimitive?.contentOrNull,
            to = d["to"]?.jsonPrimitive?.contentOrNull,
            subject = d["subject"]?.jsonPrimitive?.contentOrNull,
            receivedAt = d["received_at"]?.jsonPrimitive?.contentOrNull,
            seen = d["seen"]?.toString()?.toBooleanStrictOrNull() ?: false,
            hasAttachment = false,
            otp = otpRaw?.let { EmailOtp(it, OtpSource.PROVIDER) },
            verificationLinks = emptyList(),
        )
    }

    override suspend fun getMessage(account: EmailAccount, messageId: String): EmailMessageDetail = call {
        val token = account.providerToken
            ?: throw NotSupportedOperationException("Tmailor mailbox token missing")
        val res = http.call(
            providerId = id,
            method = HttpMethod.Post,
            url = "$BASE/v1/mailbox/message",
            authHeader = authHeader,
            body = MessageReadBody(token = token, message_id = messageId),
            errorMapper = { status, _ ->
                if (status == HttpStatusCode.NotFound) "not_found" to "Message not found or expired" else null
            },
        )
        val obj = http.parse<JsonObject>(res.body)
        val summary = mapSummary(obj)
        val text = obj["text"]?.let { if (it is JsonNull) null else it.jsonPrimitive.contentOrNull }
        val html = obj["html"]?.let { if (it is JsonNull) null else it.jsonPrimitive.contentOrNull }
        val bodyAvailable = obj["body_available"]?.toString()?.toBooleanStrictOrNull() ?: true
        val providerOtp = obj["otp"]?.let { if (it is JsonNull) null else it.jsonPrimitive.contentOrNull }
        val codes = obj["codes"]?.let { arr ->
            runCatching { arr.jsonArray.map { it.jsonPrimitive.contentOrNull } }.getOrDefault(emptyList())
                .filterNotNull()
        } ?: emptyList()
        val links = obj["links"]?.let { arr ->
            runCatching { arr.jsonArray.map { it.jsonPrimitive.contentOrNull } }.getOrDefault(emptyList())
                .filterNotNull()
        } ?: emptyList()

        var otp = providerOtp?.let { EmailOtp(it, OtpSource.PROVIDER, messageId = messageId) }
        if (otp == null) {
            val source = text ?: html ?: ""
            dev.aprax.tmaii.backend.services.OtpParser.extract(source)?.let {
                otp = EmailOtp(it, OtpSource.LOCAL, messageId = messageId)
            }
        }

        EmailMessageDetail(
            message = summary.copy(
                otp = otp,
                verificationLinks = links.take(10).map { VerificationLink(it, messageId) },
            ),
            html = html,
            text = text,
            attachments = emptyList(), // Tmailor is receive-only without attachments
            bodyAvailable = bodyAvailable,
            providerCodes = codes,
            providerLinks = links,
        )
    }

    @Serializable
    private data class MessageReadBody(
        val token: String,
        @SerialName("message_id") val message_id: String,
    )

    override suspend fun deleteMessage(account: EmailAccount, messageId: String): Unit = call {
        val token = account.providerToken
            ?: throw NotSupportedOperationException("Tmailor mailbox token missing")
        http.call(
            providerId = id,
            method = HttpMethod.Post,
            url = "$BASE/v1/mailbox/message/delete",
            authHeader = authHeader,
            body = MessageReadBody(token = token, message_id = messageId),
        )
        Unit
    }

    override suspend fun getOtp(account: EmailAccount): EmailOtp? {
        // Tmailor has no standalone OTP endpoint; surface the best OTP from recent messages.
        requireConfigured()
        val messages = listMessages(account)
        for (m in messages) {
            m.otp?.let { return it }
        }
        for (m in messages.take(5)) {
            val detail = getMessage(account, m.id)
            detail.message.otp?.let { return it }
            detail.text?.let { t ->
                dev.aprax.tmaii.backend.services.OtpParser.extract(t)?.let {
                    return EmailOtp(it, OtpSource.LOCAL, messageId = m.id, subject = m.subject)
                }
            }
        }
        return null
    }

    companion object {
        const val BASE = "https://open-api.tmailor.com"
    }
}
