package dev.aprax.tmaii.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ApiErrorBody(
    val success: Boolean = false,
    val error: String? = null,
    val message: String? = null,
    val retryAfter: Int? = null,
)

@Serializable
data class AuthResponse(
    val token: String,
    val role: String,
    val userId: String,
    val email: String? = null,
    val expiresAt: Long,
)

@Serializable
data class MeResponse(
    val userId: String,
    val role: String,
    val email: String? = null,
    val createdAt: String? = null,
)

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
    val documentationUrl: String = "",
)

@Serializable
data class ProvidersResponse(val providers: List<ProviderInfo> = emptyList())

@Serializable
data class ProviderDomain(
    val domain: String,
    val usable: Boolean = true,
    val tier: String? = null,
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
data class QuotasResponse(val quotas: List<ProviderQuota> = emptyList())

@Serializable
data class Mailbox(
    val id: String,
    val provider: String,
    val address: String,
    val label: String? = null,
    val createdAt: String,
    val lastSyncAt: String? = null,
    val remoteDeleted: Boolean = false,
    val unreadCount: Int = 0,
    val reuseUrl: String? = null,
)

@Serializable
data class MailboxesResponse(val mailboxes: List<Mailbox> = emptyList())

@Serializable
data class CreateMailboxBody(
    val provider: String,
    val username: String? = null,
    val domain: String? = null,
    val random: Boolean = true,
    val label: String? = null,
)

@Serializable
data class Message(
    val id: String,
    val from: String,
    val fromName: String? = null,
    val to: String? = null,
    val subject: String? = null,
    val receivedAt: String? = null,
    val seen: Boolean = false,
    val hasAttachment: Boolean = false,
    val otp: Map<String, String>? = null,
    val verificationLinks: List<String> = emptyList(),
)

@Serializable
data class MessagesResponse(val messages: List<Message> = emptyList())

@Serializable
data class MessageDetail(
    val message: Message,
    val html: String? = null,
    val text: String? = null,
    val attachments: List<Attachment> = emptyList(),
    val bodyAvailable: Boolean = true,
)

@Serializable
data class Attachment(
    val filename: String,
    val contentType: String? = null,
    val size: Long? = null,
)

@Serializable
data class OtpResponse(
    val otp: String? = null,
    val source: String? = null,
    val score: Double? = null,
    val messageId: String? = null,
    val subject: String? = null,
    val detected: Boolean = false,
)

@Serializable
data class HealthResponse(
    val status: String = "unknown",
    val version: String = "",
    val database: String = "",
    val environment: String = "",
    val providers: List<ProviderHealthSummary> = emptyList(),
    val uptimeSeconds: Long = 0,
)

@Serializable
data class ProviderHealthSummary(
    val id: String,
    val configured: Boolean,
    val enabled: Boolean,
    val reachable: Boolean? = null,
    val message: String? = null,
)

@Serializable
data class OkResponse(val success: Boolean = true, val message: String? = null)

/** Local Room entity for offline mailbox history. */
data class LocalMailbox(
    val id: String,
    val provider: String,
    val address: String,
    val createdAt: Long,
    val lastSyncAt: Long? = null,
    val label: String? = null,
)

data class LocalMessage(
    val id: String,
    val mailboxId: String,
    val from: String,
    val fromName: String?,
    val subject: String?,
    val receivedAt: Long?,
    val seen: Boolean,
    val hasOtp: Boolean,
    val otpCode: String?,
    val otpSource: String?,
    val hasVerificationLink: Boolean,
)

enum class ThemeMode { DARK, LIGHT, SYSTEM }

enum class RefreshInterval(val seconds: Int, val label: String) {
    SEC_15(15, "15 seconds"),
    SEC_30(30, "30 seconds"),
    SEC_60(60, "1 minute"),
    MIN_2(120, "2 minutes"),
    MANUAL(0, "Manual only"),
    ;

    companion object {
        fun fromSeconds(seconds: Int): RefreshInterval =
            entries.firstOrNull { it.seconds == seconds } ?: SEC_30
    }
}
