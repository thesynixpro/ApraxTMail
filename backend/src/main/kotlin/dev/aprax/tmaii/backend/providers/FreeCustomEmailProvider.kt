package dev.aprax.tmaii.backend.providers

import dev.aprax.tmaii.backend.utils.NotSupportedOperationException
import dev.aprax.tmaii.backend.utils.ProviderErrorException
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.encodeURLPath
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * FreeCustom.Email adapter.
 * Docs: https://www.freecustom.email/api/docs/quickstart
 * Base: https://api2.freecustom.email
 * Free plan: 1 req/s, 1,000 requests/month. OTP endpoint requires Startup+;
 * on lower plans the messages list returns otp = "__DETECTED__".
 */
class FreeCustomEmailProvider(
    private val apiKey: String?,
    private val enabled: Boolean,
    private val http: ProviderHttpClient,
    private val throttlerMinIntervalMs: Long = 1100L,
) : EmailProvider {

    override val id: String = "freecustom"
    override val displayName: String = "FreeCustom.Email"
    override val documentationUrl: String = "https://www.freecustom.email/api/docs/quickstart"

    override val capabilities = ProviderCapabilities(
        randomAddress = true,       // backend composes username + platform domain
        customName = true,
        domainSelection = true,
        providerOtpEndpoint = true, // plan-gated; handled gracefully
        otpFromMessage = true,
        verificationLinks = true,
        attachments = true,         // listing only on free plan
        deleteMessage = true,
        deleteMailbox = true,
        reuseUrl = false,
        idempotency = false,
        messageRetentionHours = null,
        longPolling = false,        // /wait costs 10 requests/call on paid plans
    )

    private val authHeader: String? get() = apiKey?.let { "Bearer $it" }
    private val domainsMutex = Mutex()
    @Volatile private var cachedDomains: List<ProviderDomain>? = null
    @Volatile private var cachedDomainsAt: Long = 0

    override fun isConfigured(): Boolean = enabled && !apiKey.isNullOrBlank()

    private fun requireConfigured() {
        if (!isConfigured()) {
            throw NotSupportedOperationException("FreeCustom.Email provider is not configured")
        }
    }

    private suspend fun <T> call(block: suspend () -> T): T {
        requireConfigured()
        // Provider-level spacing handled by ProviderRegistry.throttler; here just execute.
        return block()
    }

    override suspend fun healthCheck(): ProviderHealth {
        if (!isConfigured()) {
            return ProviderHealth(id, false, "API key not configured or provider disabled")
        }
        val start = System.currentTimeMillis()
        return try {
            call { http.call(id, HttpMethod.Get, "$BASE/v1/me", authHeader) }
            ProviderHealth(id, true, "OK", System.currentTimeMillis() - start)
        } catch (e: ProviderErrorException) {
            ProviderHealth(id, false, e.message ?: "health check failed", System.currentTimeMillis() - start)
        } catch (e: Exception) {
            ProviderHealth(id, false, e.message ?: "unreachable", System.currentTimeMillis() - start)
        }
    }

    override suspend fun getQuota(): ProviderQuota? = call {
        val res = http.call(id, HttpMethod.Get, "$BASE/v1/me", authHeader)
        val obj = http.parse<JsonObject>(res.body)
        val rateLimits = obj["rate_limits"]?.let {
            runCatching { http.parse<RateLimits>(it.toString()) }.getOrNull()
        }
        ProviderQuota(
            providerId = id,
            plan = obj["plan_label"]?.jsonPrimitive?.contentOrNull
                ?: obj["plan"]?.jsonPrimitive?.contentOrNull,
            requestsPerSecond = rateLimits?.requestsPerSecond,
            requestsPerMonth = rateLimits?.requestsPerMonth,
            requestsRemainingMonth = null,
            mailboxesRemaining = null,
            creditsRemaining = (obj["credits"]?.jsonPrimitive?.contentOrNull?.toLongOrNull())
                ?: (obj["trial_credits"]?.jsonPrimitive?.contentOrNull?.toLongOrNull()),
            notes = buildList {
                add("Monthly quota resets 00:00 UTC on the 1st")
                add("OTP endpoint costs 3 requests and requires Startup plan")
            },
        )
    }

    @Serializable
    private data class RateLimits(
        @SerialName("requestsPerSecond") val requestsPerSecond: Long? = null,
        @SerialName("requestsPerMonth") val requestsPerMonth: Long? = null,
    )

    override suspend fun listDomains(): List<ProviderDomain> = call {
        val now = System.currentTimeMillis()
        cachedDomains?.let { if (now - cachedDomainsAt < 5 * 60_000) return@call it }
        domainsMutex.withLock {
            cachedDomains?.let { if (System.currentTimeMillis() - cachedDomainsAt < 5 * 60_000) return@withLock it }
            val res = http.call(id, HttpMethod.Get, "$BASE/v1/domains", authHeader)
            val obj = http.parse<JsonObject>(res.body)
            val data = obj["data"]?.jsonArray ?: error("missing data")
            val domains = data.mapNotNull { el ->
                val d = el.jsonObject
                val domain = d["domain"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
                ProviderDomain(
                    domain = domain,
                    usable = d["expired"]?.jsonPrimitive?.contentOrNull != "true",
                    tier = d["tier"]?.jsonPrimitive?.contentOrNull,
                )
            }.filter { it.usable }
            cachedDomains = domains
            cachedDomainsAt = System.currentTimeMillis()
            domains
        }
    }

    override suspend fun createMailbox(request: CreateMailboxRequest): EmailAccount = call {
        val address = if (request.username != null && request.domain != null) {
            "${request.username}@${request.domain}"
        } else {
            val domains = listDomains().filter { it.tier == null || it.tier == "free" }
            if (domains.isEmpty()) throw ProviderErrorException(
                "No FreeCustom domains available", "no_domain_available", HttpStatusCode.ServiceUnavailable, id,
            )
            val domain = request.domain ?: domains.random().domain
            val username = request.username ?: randomUsername()
            "$username@$domain"
        }
        val res = http.call(
            providerId = id,
            method = HttpMethod.Post,
            url = "$BASE/v1/inboxes",
            authHeader = authHeader,
            body = InboxCreateRequest(inbox = address, isTesting = false),
            errorMapper = { status, body ->
                when {
                    status == HttpStatusCode.BadRequest -> "invalid_inbox" to "Invalid inbox address: $address"
                    status == HttpStatusCode.Forbidden -> "forbidden" to "Domain not allowed for this plan/key"
                    else -> null
                }
            },
        )
        val obj = runCatching { http.parse<JsonObject>(res.body) }.getOrNull()
        val registeredAt = obj?.get("data")?.jsonObject?.get("registered_at")?.jsonPrimitive?.contentOrNull
        EmailAccount(
            providerId = id,
            address = address,
            providerToken = null,
            createdAt = registeredAt,
        )
    }

    @Serializable
    private data class InboxCreateRequest(
        val inbox: String,
        @SerialName("isTesting") val isTesting: Boolean = false,
    )

    override suspend fun deleteMailbox(account: EmailAccount): Unit = call {
        http.call(
            providerId = id,
            method = HttpMethod.Delete,
            url = "$BASE/v1/inboxes/${account.address.encodeURLPath()}",
            authHeader = authHeader,
            errorMapper = { status, _ ->
                if (status == HttpStatusCode.NotFound) "not_found" to "Inbox not found" else null
            },
        )
        Unit
    }

    override suspend fun listMessages(account: EmailAccount): List<EmailMessage> = call {
        val res = http.call(
            providerId = id,
            method = HttpMethod.Get,
            url = "$BASE/v1/inboxes/${account.address.encodeURLPath()}/messages",
            authHeader = authHeader,
            errorMapper = { status, _ ->
                if (status == HttpStatusCode.Forbidden) "forbidden" to "Inbox not owned by this key" else null
            },
        )
        val obj = http.parse<JsonObject>(res.body)
        val data = obj["data"]?.jsonArray ?: return@call emptyList()
        data.map { el -> mapMessageSummary(el.jsonObject) }
    }

    private fun mapMessageSummary(d: JsonObject): EmailMessage {
        val otpRaw = d["otp"]?.let {
            when (it) {
                is kotlinx.serialization.json.JsonNull -> null
                else -> it.jsonPrimitive.contentOrNull
            }
        }
        val otp = when {
            otpRaw == null || otpRaw == "__DETECTED__" -> null
            else -> EmailOtp(code = otpRaw, source = OtpSource.PROVIDER)
        }
        val link = d["verificationLink"]?.jsonPrimitive?.contentOrNull
        return EmailMessage(
            id = d["id"]?.jsonPrimitive?.contentOrNull ?: "",
            from = d["from"]?.jsonPrimitive?.contentOrNull ?: "",
            fromName = null,
            to = d["to"]?.jsonPrimitive?.contentOrNull,
            subject = d["subject"]?.jsonPrimitive?.contentOrNull,
            receivedAt = d["date"]?.jsonPrimitive?.contentOrNull,
            seen = true,
            hasAttachment = d["hasAttachment"]?.jsonPrimitive?.contentOrNull == "true",
            otp = otp,
            verificationLinks = link?.let { listOf(VerificationLink(it)) } ?: emptyList(),
        )
    }

    override suspend fun getMessage(account: EmailAccount, messageId: String): EmailMessageDetail = call {
        val res = http.call(
            providerId = id,
            method = HttpMethod.Get,
            url = "$BASE/v1/inboxes/${account.address.encodeURLPath()}/messages/${messageId.encodeURLPath()}",
            authHeader = authHeader,
            errorMapper = { status, _ ->
                if (status == HttpStatusCode.NotFound) "not_found" to "Message not found" else null
            },
        )
        val obj = http.parse<JsonObject>(res.body)
        val data = obj["data"]?.jsonObject ?: obj
        val summary = mapMessageSummary(data)
        val html = data["html"]?.jsonPrimitive?.contentOrNull
        val text = data["text"]?.jsonPrimitive?.contentOrNull
        val attachments = data["attachments"]?.let { arr ->
            runCatching {
                arr.jsonArray.map { a ->
                    val ao = a.jsonObject
                    EmailAttachment(
                        filename = ao["filename"]?.jsonPrimitive?.contentOrNull ?: "attachment",
                        contentType = ao["contentType"]?.jsonPrimitive?.contentOrNull,
                        size = ao["size"]?.jsonPrimitive?.contentOrNull?.toLongOrNull(),
                    )
                }
            }.getOrDefault(emptyList())
        } ?: emptyList()

        var otp = summary.otp
        var links = summary.verificationLinks
        if (otp == null && html != null) {
            val parsed = dev.aprax.tmaii.backend.services.OtpParser.extract(html.ifBlank { text ?: "" })
            if (parsed != null) otp = EmailOtp(code = parsed, source = OtpSource.LOCAL, messageId = summary.id)
        }
        if (links.isEmpty()) {
            val textOrHtml = text ?: html ?: ""
            links = dev.aprax.tmaii.backend.services.OtpParser.findVerificationLinks(textOrHtml)
                .map { VerificationLink(it, summary.id) }
        }

        EmailMessageDetail(
            message = summary.copy(otp = otp, verificationLinks = links),
            html = html,
            text = text,
            attachments = attachments,
            bodyAvailable = !html.isNullOrBlank() || !text.isNullOrBlank(),
        )
    }

    override suspend fun deleteMessage(account: EmailAccount, messageId: String): Unit = call {
        http.call(
            providerId = id,
            method = HttpMethod.Delete,
            url = "$BASE/v1/inboxes/${account.address.encodeURLPath()}/messages/${messageId.encodeURLPath()}",
            authHeader = authHeader,
        )
        Unit
    }

    override suspend fun getOtp(account: EmailAccount): EmailOtp? = call {
        val res = try {
            http.call(
                providerId = id,
                method = HttpMethod.Get,
                url = "$BASE/v1/inboxes/${account.address.encodeURLPath()}/otp",
                authHeader = authHeader,
            )
        } catch (e: ProviderErrorException) {
            if (e.code == "plan_required" || e.code == "plan_restriction" ||
                e.status == HttpStatusCode.Forbidden
            ) {
                throw NotSupportedOperationException("OTP endpoint requires a higher plan; falling back to local parsing")
            }
            throw e
        }
        val obj = http.parse<JsonObject>(res.body)
        val data = if (obj["data"] is JsonObject) obj["data"]!!.jsonObject else obj
        val otp = data["otp"]?.jsonPrimitive?.contentOrNull
            ?: data["code"]?.jsonPrimitive?.contentOrNull
        when {
            otp == null || otp == "__DETECTED__" ->
                throw NotSupportedOperationException("Provider did not return an actual OTP code")
            else -> EmailOtp(
                code = otp,
                source = OtpSource.PROVIDER,
                score = data["score"]?.jsonPrimitive?.contentOrNull?.toDoubleOrNull(),
                messageId = data["message_id"]?.jsonPrimitive?.contentOrNull
                    ?: data["email_id"]?.jsonPrimitive?.contentOrNull,
                subject = data["subject"]?.jsonPrimitive?.contentOrNull,
            )
        }
    }

    companion object {
        const val BASE = "https://api2.freecustom.email"

        fun randomUsername(): String {
            val chars = "abcdefghijklmnopqrstuvwxyz0123456789"
            return (1..10).map { chars.random() }.joinToString("")
        }
    }
}
