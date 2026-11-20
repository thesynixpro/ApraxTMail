package dev.aprax.tmaii.backend.providers

import kotlinx.serialization.Serializable

// ---------- Normalized models shared by both providers ----------

@Serializable
data class ProviderCapabilities(
    val randomAddress: Boolean = false,
    val customName: Boolean = false,
    val domainSelection: Boolean = false,
    val providerOtpEndpoint: Boolean = false,
    val otpFromMessage: Boolean = false,
    val verificationLinks: Boolean = false,
    val attachments: Boolean = false,
    val deleteMessage: Boolean = false,
    val deleteMailbox: Boolean = false,
    val reuseUrl: Boolean = false,
    val idempotency: Boolean = false,
    val messageRetentionHours: Int? = null,
    val longPolling: Boolean = false,
)

@Serializable
data class ProviderInfo(
    val id: String,
    val displayName: String,
    val enabled: Boolean,
    val configured: Boolean,
    val capabilities: ProviderCapabilities,
    val documentationUrl: String,
)

@Serializable
data class ProviderQuota(
    val providerId: String,
    val plan: String? = null,
    val requestsPerSecond: Long? = null,
    val requestsPerMonth: Long? = null,
    val requestsRemainingMonth: Long? = null,
    val mailboxesRemaining: Long? = null,
    val dailyRequestsRemaining: Long? = null,
    val creditsRemaining: Long? = null,
    val ipAllowlistEnforced: Boolean? = null,
    val notes: List<String> = emptyList(),
)

@Serializable
data class ProviderDomain(
    val domain: String,
    val usable: Boolean = true,
    val tier: String? = null,
)

@Serializable
data class ProviderHealth(
    val providerId: String,
    val reachable: Boolean,
    val message: String,
    val latencyMs: Long? = null,
)

enum class OtpSource { PROVIDER, LOCAL, LINK }

@Serializable
data class EmailOtp(
    val code: String,
    val source: OtpSource,
    val score: Double? = null,
    val messageId: String? = null,
    val subject: String? = null,
)

@Serializable
data class VerificationLink(
    val url: String,
    val messageId: String? = null,
)

@Serializable
data class EmailAttachment(
    val filename: String,
    val contentType: String? = null,
    val size: Long? = null,
)

@Serializable
data class EmailMessage(
    val id: String,
    val from: String,
    val fromName: String? = null,
    val to: String? = null,
    val subject: String? = null,
    val receivedAt: String? = null,
    val seen: Boolean = false,
    val hasAttachment: Boolean = false,
    val otp: EmailOtp? = null,
    val verificationLinks: List<VerificationLink> = emptyList(),
)

@Serializable
data class EmailMessageDetail(
    val message: EmailMessage,
    val html: String? = null,
    val text: String? = null,
    val attachments: List<EmailAttachment> = emptyList(),
    val bodyAvailable: Boolean = true,
    val providerCodes: List<String> = emptyList(),
    val providerLinks: List<String> = emptyList(),
)

@Serializable
data class EmailAccount(
    val providerId: String,
    val address: String,
    /** Opaque provider-side token (Tmailor). Never serialized to API clients. */
    val providerToken: String? = null,
    val createdAt: String? = null,
    val reuseUrl: String? = null,
    val meta: Map<String, String> = emptyMap(),
)

@Serializable
data class CreateMailboxRequest(
    val username: String? = null,
    val domain: String? = null,
    val random: Boolean = true,
)

// ---------- Errors ----------

data class ProviderCallResult<T>(
    val value: T,
    val statusCode: Int,
    val latencyMs: Long,
)

// ---------- The unified interface ----------

interface EmailProvider {
    val id: String
    val displayName: String
    val capabilities: ProviderCapabilities
    val documentationUrl: String

    /** True when the API key is present and the provider is toggled on. */
    fun isConfigured(): Boolean

    suspend fun healthCheck(): ProviderHealth

    suspend fun getQuota(): ProviderQuota?

    suspend fun listDomains(): List<ProviderDomain>

    suspend fun createMailbox(request: CreateMailboxRequest): EmailAccount

    suspend fun deleteMailbox(account: EmailAccount)

    suspend fun listMessages(account: EmailAccount): List<EmailMessage>

    suspend fun getMessage(account: EmailAccount, messageId: String): EmailMessageDetail

    suspend fun deleteMessage(account: EmailAccount, messageId: String)

    /**
     * Provider-native OTP retrieval. Returns null when the provider has no OTP
     * for this inbox; throws [dev.aprax.tmaii.backend.utils.NotSupportedOperationException]
     * when the plan/endpoint does not support it (caller falls back to local parsing).
     */
    suspend fun getOtp(account: EmailAccount): EmailOtp?
}
